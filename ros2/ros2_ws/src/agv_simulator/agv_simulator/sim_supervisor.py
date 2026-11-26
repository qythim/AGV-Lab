import json
import math
import os
import queue
import re
import urllib.request

import paho.mqtt.client as mqtt
import rclpy
from rclpy.executors import MultiThreadedExecutor
from rclpy.node import Node
from rclpy.parameter import Parameter

from agv_simulator.vda5050_bridge import Vda5050Bridge
from agv_simulator.virtual_agv import VirtualAGV


class SimulatorSupervisorNode(Node):
    """
    ROS 2 多車模擬器總管節點 (Simulator Supervisor)
    職責：
    1. 訂閱 MQTT sim/fleet/snapshot (retained) 與 sim/fleet/control，以 RCS 資料庫為唯一真實來源
    2. 動態建立 (SPAWN)、瞬移重置 (PLACE) 與銷毀 (DESTROY) 多台 (VirtualAGV + Vda5050Bridge) 節點對
    3. 透過執行緒安全佇列於 ROS 2 主執行緒操作 Executor，避免跨執行緒競態條件
    """

    def __init__(self, executor: MultiThreadedExecutor):
        super().__init__('sim_supervisor')
        self.sim_executor = executor

        default_mqtt_host = os.getenv('MQTT_HOST', 'mqtt')
        default_mqtt_port = int(os.getenv('MQTT_PORT', '1883'))
        default_rcs_api = os.getenv('RCS_AGV_API_URL', 'http://java21:8080/api/agvs')

        self.declare_parameter('mqtt_broker', default_mqtt_host)
        self.declare_parameter('mqtt_port', default_mqtt_port)
        self.declare_parameter('manufacturer', 'agv-lab')
        self.declare_parameter('rcs_agv_api_url', default_rcs_api)
        self.declare_parameter('max_speed', 1.5)
        self.declare_parameter('acceleration', 0.8)
        self.declare_parameter('deceleration', 1.0)
        self.declare_parameter('angular_speed', 1.8)

        self.mqtt_broker = self.get_parameter('mqtt_broker').get_parameter_value().string_value
        self.mqtt_port = self.get_parameter('mqtt_port').get_parameter_value().integer_value
        self.manufacturer = self.get_parameter('manufacturer').get_parameter_value().string_value
        self.rcs_agv_api_url = self.get_parameter('rcs_agv_api_url').get_parameter_value().string_value
        self.max_speed = self.get_parameter('max_speed').get_parameter_value().double_value
        self.acceleration = self.get_parameter('acceleration').get_parameter_value().double_value
        self.deceleration = self.get_parameter('deceleration').get_parameter_value().double_value
        self.angular_speed = self.get_parameter('angular_speed').get_parameter_value().double_value

        self.control_topic = 'sim/fleet/control'
        self.snapshot_topic = 'sim/fleet/snapshot'

        # 目前運作中的車輛節點表：agv_id -> {'virtual_agv': VirtualAGV, 'bridge': Vda5050Bridge}
        self.active_agvs = {}
        self.cmd_queue = queue.Queue()
        self.snapshot_received = False

        # 每 100ms 於 ROS 2 主執行緒處理來自 MQTT 的車隊管理指令
        self.timer = self.create_timer(0.1, self._process_pending_commands)

        # 啟動後 2 秒若尚未收到 MQTT retained snapshot，主動向 RCS HTTP API 拉取一次車隊清單作為保底
        self.fallback_timer = self.create_timer(2.0, self._fallback_http_sync_once)

        self.mqtt_client = mqtt.Client(client_id='ros2_sim_supervisor')
        self.mqtt_client.on_connect = self._on_mqtt_connect
        self.mqtt_client.on_message = self._on_mqtt_message

        try:
            self.get_logger().info(
                f'[SimSupervisor] 正在連線至 MQTT Broker ({self.mqtt_broker}:{self.mqtt_port})...'
            )
            self.mqtt_client.connect(self.mqtt_broker, self.mqtt_port, keepalive=60)
            self.mqtt_client.loop_start()
        except Exception as e:
            self.get_logger().error(f'[SimSupervisor] MQTT 連線失敗: {e}')

        self.get_logger().info('[SimSupervisor] 多車模擬器總管節點初始化完成，等待車隊同步指令...')

    def _on_mqtt_connect(self, client, userdata, flags, rc, *args):
        if rc == 0:
            self.get_logger().info(
                f'[SimSupervisor] MQTT 連線成功！訂閱車隊同步主題: '
                f'{self.snapshot_topic}, {self.control_topic}'
            )
            self.mqtt_client.subscribe(self.snapshot_topic, qos=1)
            self.mqtt_client.subscribe(self.control_topic, qos=1)
        else:
            self.get_logger().error(f'[SimSupervisor] MQTT 連線錯誤，返回碼: {rc}')

    def _on_mqtt_message(self, client, userdata, msg):
        try:
            payload = json.loads(msg.payload.decode('utf-8'))
            self.cmd_queue.put((msg.topic, payload))
        except Exception as e:
            self.get_logger().error(f'[SimSupervisor] 解析 MQTT 訊息失敗 ({msg.topic}): {e}')

    def _process_pending_commands(self):
        while not self.cmd_queue.empty():
            try:
                topic, payload = self.cmd_queue.get_nowait()
            except queue.Empty:
                break

            try:
                if topic == self.snapshot_topic:
                    self.snapshot_received = True
                    self._handle_snapshot(payload)
                elif topic == self.control_topic:
                    self._handle_control(payload)
            except Exception as e:
                self.get_logger().error(f'[SimSupervisor] 處理車隊同步指令時發生錯誤: {e}')

    def _fallback_http_sync_once(self):
        """若啟動後尚未收到 MQTT retained snapshot，嘗試從 RCS REST API 拉取已放置之車輛清單"""
        self.fallback_timer.cancel()
        if self.snapshot_received or not self.rcs_agv_api_url:
            return

        try:
            req = urllib.request.Request(self.rcs_agv_api_url, method='GET')
            with urllib.request.urlopen(req, timeout=3.0) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                if not isinstance(data, list):
                    return
                agvs = []
                for item in data:
                    map_id = item.get('currentMapCode')
                    point_id = item.get('currentPoint')
                    pos_x = item.get('posX')
                    pos_y = item.get('posY')
                    if map_id and point_id and pos_x is not None and pos_y is not None:
                        angle_deg = float(item.get('angle') or 0.0)
                        agvs.append({
                            'agvId': item.get('agvCode'),
                            'mapId': map_id,
                            'pointId': point_id,
                            'x': float(pos_x),
                            'y': float(pos_y),
                            'theta': math.radians(angle_deg),
                        })
                self.get_logger().info(
                    f'[SimSupervisor] 透過 HTTP API 初始同步車隊成功，共 {len(agvs)} 台在圖車輛'
                )
                self.cmd_queue.put((self.snapshot_topic, {'agvs': agvs}))
        except Exception as e:
            self.get_logger().info(
                f'[SimSupervisor] 尚未從 HTTP 取得初始車隊 (等待 MQTT snapshot): {e}'
            )

    def _handle_snapshot(self, payload: dict):
        agv_list = payload.get('agvs', [])
        if not isinstance(agv_list, list):
            return

        desired_ids = set()
        for item in agv_list:
            agv_id = str(item.get('agvId', '')).strip()
            if not agv_id:
                continue
            desired_ids.add(agv_id)
            map_id = str(item.get('mapId', '')).strip()
            point_id = str(item.get('pointId', '')).strip()
            x = float(item.get('x', 0.0))
            y = float(item.get('y', 0.0))
            theta = float(item.get('theta', 0.0))

            if agv_id not in self.active_agvs:
                self._spawn_agv(agv_id, map_id, point_id, x, y, theta)

        # 移除已不在快照中的車輛
        for existing_id in list(self.active_agvs.keys()):
            if existing_id not in desired_ids:
                self._destroy_agv(existing_id)

    def _handle_control(self, payload: dict):
        action = str(payload.get('action', '')).upper()
        agv_id = str(payload.get('agvId', '')).strip()
        if not agv_id:
            return

        map_id = str(payload.get('mapId', '')).strip()
        point_id = str(payload.get('pointId', '')).strip()
        x = float(payload.get('x', 0.0))
        y = float(payload.get('y', 0.0))
        theta = float(payload.get('theta', 0.0))

        if action in ('SPAWN', 'PLACE'):
            if agv_id in self.active_agvs:
                self._place_existing_agv(agv_id, map_id, point_id, x, y, theta)
            else:
                self._spawn_agv(agv_id, map_id, point_id, x, y, theta)
        elif action == 'DESTROY':
            self._destroy_agv(agv_id)
        else:
            self.get_logger().warning(f'[SimSupervisor] 未知的車隊控制指令: {action}')

    def _spawn_agv(self, agv_id: str, map_id: str, point_id: str, x: float, y: float, theta: float):
        safe_suffix = re.sub(r'[^a-zA-Z0-9_]', '_', agv_id).lower()
        namespace = f'/agv_{safe_suffix}'

        virtual_params = [
            Parameter('robot_id', value=agv_id),
            Parameter('init_x', value=float(x)),
            Parameter('init_y', value=float(y)),
            Parameter('init_yaw', value=float(theta)),
            Parameter('init_node_id', value=point_id),
            Parameter('max_speed', value=float(self.max_speed)),
            Parameter('acceleration', value=float(self.acceleration)),
            Parameter('deceleration', value=float(self.deceleration)),
            Parameter('angular_speed', value=float(self.angular_speed)),
        ]
        bridge_params = [
            Parameter('robot_id', value=agv_id),
            Parameter('manufacturer', value=self.manufacturer),
            Parameter('mqtt_broker', value=self.mqtt_broker),
            Parameter('mqtt_port', value=int(self.mqtt_port)),
            Parameter('init_map_id', value=map_id),
            Parameter('default_max_speed', value=float(self.max_speed)),
        ]

        virtual_node = VirtualAGV(
            node_name='virtual_agv',
            namespace=namespace,
            parameter_overrides=virtual_params
        )
        bridge_node = Vda5050Bridge(
            node_name='vda5050_bridge',
            namespace=namespace,
            parameter_overrides=bridge_params
        )

        self.sim_executor.add_node(virtual_node)
        self.sim_executor.add_node(bridge_node)
        self.active_agvs[agv_id] = {
            'virtual_agv': virtual_node,
            'bridge': bridge_node,
        }

        self.get_logger().info(
            f'[SimSupervisor] 已動態生成車輛 [{agv_id}] '
            f'(ns={namespace}, map={map_id}, point={point_id}, pos=({x:.2f}, {y:.2f}))'
        )

    def _place_existing_agv(self, agv_id: str, map_id: str, point_id: str,
                            x: float, y: float, theta: float):
        pair = self.active_agvs.get(agv_id)
        if not pair:
            return
        bridge_node: Vda5050Bridge = pair['bridge']
        bridge_node.apply_place(map_id, point_id, x, y, theta)
        self.get_logger().info(
            f'[SimSupervisor] 已瞬移重置車輛 [{agv_id}] 至 '
            f'map={map_id}, point={point_id}, pos=({x:.2f}, {y:.2f})'
        )

    def _destroy_agv(self, agv_id: str):
        pair = self.active_agvs.pop(agv_id, None)
        if not pair:
            return

        virtual_node: VirtualAGV = pair['virtual_agv']
        bridge_node: Vda5050Bridge = pair['bridge']

        try:
            self.sim_executor.remove_node(bridge_node)
        except Exception:
            pass
        try:
            self.sim_executor.remove_node(virtual_node)
        except Exception:
            pass

        bridge_node.destroy_node()
        virtual_node.destroy_node()
        self.get_logger().info(f'[SimSupervisor] 已銷毀並移除車輛 [{agv_id}]')

    def destroy_node(self):
        for agv_id in list(self.active_agvs.keys()):
            self._destroy_agv(agv_id)
        self.mqtt_client.loop_stop()
        self.mqtt_client.disconnect()
        super().destroy_node()


def main(args=None):
    rclpy.init(args=args)
    executor = MultiThreadedExecutor()
    supervisor = SimulatorSupervisorNode(executor)
    executor.add_node(supervisor)

    try:
        executor.spin()
    except KeyboardInterrupt:
        pass
    finally:
        supervisor.destroy_node()
        executor.shutdown()
        rclpy.shutdown()


if __name__ == '__main__':
    main()

