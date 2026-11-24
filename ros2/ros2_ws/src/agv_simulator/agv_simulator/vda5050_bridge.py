import json
import os
from datetime import datetime, timezone

import paho.mqtt.client as mqtt
import rclpy
from rclpy.node import Node
from std_msgs.msg import String


class Vda5050Bridge(Node):
    """
    VDA 5050 協議轉換橋接節點 (Bridge Node)
    職責：
    1. 接收同命名空間下的 ROS 2 虛擬車輛狀態 (state) -> 轉換為 VDA 5050 State 發佈至 MQTT
    2. 接收 MQTT 上的 VDA 5050 Order 訂單 -> 轉換為 ROS 2 控制指令 (command)
    """

    def __init__(self, *, node_name: str = 'vda5050_bridge', namespace: str = '', parameter_overrides=None):
        super().__init__(
            node_name,
            namespace=namespace,
            parameter_overrides=parameter_overrides
        )

        # 透過 ROS 2 參數與環境變數動態載入設定，避免硬編碼
        default_mqtt_host = os.getenv('MQTT_HOST', 'mqtt')
        default_mqtt_port = int(os.getenv('MQTT_PORT', '1883'))

        self.declare_parameter('robot_id', 'AGV-01')
        self.declare_parameter('manufacturer', 'agv-lab')
        self.declare_parameter('mqtt_broker', default_mqtt_host)
        self.declare_parameter('mqtt_port', default_mqtt_port)
        self.declare_parameter('init_map_id', '')
        self.declare_parameter('default_max_speed', 1.0)

        self.serial_number = self.get_parameter('robot_id').get_parameter_value().string_value
        self.manufacturer = self.get_parameter('manufacturer').get_parameter_value().string_value
        self.mqtt_broker = self.get_parameter('mqtt_broker').get_parameter_value().string_value
        self.mqtt_port = self.get_parameter('mqtt_port').get_parameter_value().integer_value
        self.current_map_id = self.get_parameter('init_map_id').get_parameter_value().string_value
        self.default_max_speed = self.get_parameter('default_max_speed').get_parameter_value().double_value

        self.header_id = 0
        self.connection_header_id = 0

        # VDA 5050 訂單狀態追蹤 (供 orderUpdateId 驗證與 nodeStates / edgeStates / actionStates / errors 回報)
        self.current_order_id = ''
        self.current_order_update_id = 0
        self.current_order_nodes = []
        self.current_order_edges = []
        self.action_states = []
        self.errors = []

        # MQTT Topic 命名規範 (符合 VDA 5050 標準: uagv/v2/manufacturer/serialNumber/topic)
        self.topic_prefix = f'uagv/v2/{self.manufacturer}/{self.serial_number}'
        self.state_topic = f'{self.topic_prefix}/state'
        self.order_topic = f'{self.topic_prefix}/order'
        self.instant_actions_topic = f'{self.topic_prefix}/instantActions'
        self.connection_topic = f'{self.topic_prefix}/connection'

        # 1. 初始化 ROS 2 相對 Topic (command / state)，與同 Namespace 下的 virtual_agv 通訊
        self.ros_command_pub = self.create_publisher(
            String,
            'command',
            10
        )

        self.ros_state_sub = self.create_subscription(
            String,
            'state',
            self.on_ros_state_received,
            10
        )

        # 2. 初始化 MQTT 客戶端連線 (相容 paho-mqtt 1.x 與 2.x 標準語法)
        self.mqtt_client = mqtt.Client(client_id=f'{self.serial_number}_vda_bridge')
        self.mqtt_client.on_connect = self.on_mqtt_connect
        self.mqtt_client.on_message = self.on_mqtt_message

        # 設定 MQTT 遺囑訊息 (LWT)：若節點異常斷線，由 Broker 自動發布 CONNECTIONBROKEN
        self.mqtt_client.will_set(
            self.connection_topic,
            payload=self.build_connection_payload('CONNECTIONBROKEN'),
            qos=1,
            retain=True
        )

        try:
            self.get_logger().info(
                f'[{self.serial_number}] 正在連線至 MQTT Broker ({self.mqtt_broker}:{self.mqtt_port})...'
            )
            self.mqtt_client.connect(self.mqtt_broker, self.mqtt_port, keepalive=60)
            self.mqtt_client.loop_start()  # 在背景線程處理 MQTT 網路通訊
        except Exception as e:
            self.get_logger().error(f'[{self.serial_number}] MQTT 連線失敗: {e}')

        self.get_logger().info(
            f'[{self.serial_number}] VDA 5050 Bridge Node 初始化完成 '
            f'(namespace={self.get_namespace()}, init_map_id={self.current_map_id or "未指定"})'
        )

    def build_connection_payload(self, connection_state: str) -> str:
        """符合 VDA 5050 v2 規範的 connection 訊息內容"""
        self.connection_header_id += 1
        return json.dumps({
            'headerId': self.connection_header_id,
            'timestamp': datetime.now(timezone.utc).isoformat(),
            'version': '2.0.0',
            'manufacturer': self.manufacturer,
            'serialNumber': self.serial_number,
            'connectionState': connection_state
        })

    def publish_connection_state(self, connection_state: str, wait: bool = False):
        """發佈車輛連線狀態 (ONLINE / OFFLINE) 至保留主題 (retain=True)"""
        try:
            payload = self.build_connection_payload(connection_state)
            msg_info = self.mqtt_client.publish(self.connection_topic, payload, qos=1, retain=True)
            if wait:
                msg_info.wait_for_publish(timeout=1.0)
            self.get_logger().info(
                f'[{self.serial_number}] 已發佈連線狀態: {connection_state} ({self.connection_topic})'
            )
        except Exception as e:
            self.get_logger().error(f'[{self.serial_number}] 發佈連線狀態 {connection_state} 失敗: {e}')

    def on_mqtt_connect(self, client, userdata, flags, rc, *args):
        """當成功連線至 MQTT Broker 時觸發"""
        if rc == 0:
            self.get_logger().info(
                f'[{self.serial_number}] MQTT 連線成功！開始訂閱 Topic: '
                f'{self.order_topic}, {self.instant_actions_topic}'
            )
            self.mqtt_client.subscribe(self.order_topic, qos=1)
            self.mqtt_client.subscribe(self.instant_actions_topic, qos=1)
            self.publish_connection_state('ONLINE')
        else:
            self.get_logger().error(f'[{self.serial_number}] MQTT 連線錯誤，返回碼: {rc}')

    def on_mqtt_message(self, client, userdata, msg):
        """
        接收來自 RCS 的 MQTT 訊息，依 Topic 分派至 Order 或 InstantActions 處理器
        """
        try:
            payload = json.loads(msg.payload.decode('utf-8'))
            if msg.topic == self.instant_actions_topic or msg.topic.endswith('/instantActions'):
                self.handle_instant_actions(payload)
            else:
                self.handle_order(payload)
        except Exception as e:
            self.get_logger().error(
                f'[{self.serial_number}] 解析 MQTT 訊息失敗 (topic={msg.topic}): {e}'
            )

    def handle_instant_actions(self, payload: dict):
        """
        處理 VDA 5050 instantActions 即時指令：
        支援 startPause、stopPause、cancelOrder、initPosition
        """
        actions = payload.get('actions', [])
        if not isinstance(actions, list) or not actions:
            self.get_logger().warning(f'[{self.serial_number}] 收到空的 instantActions，忽略執行')
            return

        for action in actions:
            action_type = action.get('actionType', '')
            action_id = action.get('actionId', '')
            params = self._extract_action_params(action.get('actionParameters'))

            self.get_logger().info(
                f'[{self.serial_number}] 收到即時動作指令: {action_type} (id={action_id})'
            )

            if action_type == 'startPause':
                self._publish_ros_command({'action': 'PAUSE'})
                self._record_action_state(action_id, action_type, 'FINISHED')
            elif action_type == 'stopPause':
                self._publish_ros_command({'action': 'RESUME'})
                self._record_action_state(action_id, action_type, 'FINISHED')
            elif action_type == 'cancelOrder':
                self.current_order_id = ''
                self.current_order_update_id = 0
                self.current_order_nodes = []
                self.current_order_edges = []
                self._publish_ros_command({'action': 'STOP'})
                self._record_action_state(action_id, action_type, 'FINISHED')
            elif action_type == 'initPosition':
                map_id = params.get('mapId')
                if map_id:
                    self.current_map_id = str(map_id)
                self.current_order_id = ''
                self.current_order_update_id = 0
                self.current_order_nodes = []
                self.current_order_edges = []
                ros_cmd = {
                    'action': 'INIT_POSITION',
                    'x': float(params.get('x', 0.0)),
                    'y': float(params.get('y', 0.0)),
                    'yaw': float(params.get('theta', 0.0)),
                    'node_id': str(params.get('lastNodeId', ''))
                }
                self._publish_ros_command(ros_cmd)
                self._record_action_state(action_id, action_type, 'FINISHED')
            else:
                self.get_logger().warning(
                    f'[{self.serial_number}] 未支援的 instantAction 類型: {action_type}'
                )
                self._record_action_state(action_id, action_type, 'FAILED')

    def _record_action_state(self, action_id: str, action_type: str, status: str):
        """記錄最近執行的即時動作狀態供 VDA 5050 State 回報 (保留最近 10 筆)"""
        self.action_states.append({
            'actionId': action_id or f'{action_type}_{self.header_id}',
            'actionType': action_type,
            'actionStatus': status
        })
        if len(self.action_states) > 10:
            self.action_states = self.action_states[-10:]

    @staticmethod
    def _extract_action_params(raw_params) -> dict:
        """相容 VDA 5050 標準 [{key, value}] 陣列格式與簡易鍵值字典格式"""
        if isinstance(raw_params, dict):
            return raw_params
        result = {}
        if isinstance(raw_params, list):
            for item in raw_params:
                if isinstance(item, dict) and 'key' in item:
                    result[str(item['key'])] = item.get('value')
        return result

    def _publish_ros_command(self, ros_cmd: dict):
        """發送控制指令至同命名空間下的 virtual_agv"""
        cmd_msg = String()
        cmd_msg.data = json.dumps(ros_cmd)
        self.ros_command_pub.publish(cmd_msg)

    def apply_place(self, map_id: str, point_id: str, x: float, y: float, theta: float):
        """供 SimulatorSupervisor 直接重置該車輛之地圖與點位座標，並清除執行中訂單"""
        if map_id:
            self.current_map_id = str(map_id)
        self.current_order_id = ''
        self.current_order_update_id = 0
        self.current_order_nodes = []
        self.current_order_edges = []
        self._publish_ros_command({
            'action': 'INIT_POSITION',
            'x': float(x),
            'y': float(y),
            'yaw': float(theta),
            'node_id': str(point_id or '')
        })

    def handle_order(self, payload: dict):
        """
        接收來自 RCS 的 VDA 5050 Order 訂單
        依 VDA 5050 v2 規範驗證 orderId 與 orderUpdateId，並解析 nodes 與 edges.maxSpeed
        """
        try:
            order_id = str(payload.get('orderId', 'order_unknown'))
            order_update_id = int(payload.get('orderUpdateId', 0))

            # VDA 5050 orderUpdateId 驗證邏輯
            is_update = False
            if order_id == self.current_order_id and self.current_order_nodes:
                if order_update_id == self.current_order_update_id:
                    self.get_logger().info(
                        f'[{self.serial_number}] 收到重複的訂單更新 '
                        f'(orderId={order_id}, orderUpdateId={order_update_id})，靜默忽略'
                    )
                    return
                if order_update_id < self.current_order_update_id:
                    err_desc = (
                        f'拒絕過期的 orderUpdateId={order_update_id} '
                        f'(目前執行中 orderUpdateId={self.current_order_update_id})'
                    )
                    self.get_logger().warning(f'[{self.serial_number}] {err_desc}')
                    self.errors.append({
                        'errorType': 'orderUpdateError',
                        'errorLevel': 'WARNING',
                        'errorDescription': err_desc
                    })
                    return
                is_update = True
                # 收到合法的新展延版本時，清除先前的 orderUpdateError 警告
                self.errors = [e for e in self.errors if e.get('errorType') != 'orderUpdateError']
            else:
                self.errors = []

            self.get_logger().info(
                f'[{self.serial_number}] 收到 VDA 5050 訂單: {order_id} (orderUpdateId={order_update_id})'
            )

            # 解析每條邊的速限設定 (以 endNodeId 對應進入該點位前所行經之邊速限)
            edges = payload.get('edges', [])
            edge_speed_by_end_node = {}
            for edge in edges:
                end_node_id = edge.get('endNodeId')
                edge_max_speed = edge.get('maxSpeed')
                if end_node_id and edge_max_speed is not None:
                    try:
                        speed_val = float(edge_max_speed)
                        if speed_val > 0:
                            edge_speed_by_end_node[end_node_id] = speed_val
                    except (TypeError, ValueError):
                        pass

            # 解析 VDA 5050 nodes 點位清單並動態同步 mapId
            nodes = payload.get('nodes', [])
            waypoints = []
            for node in nodes:
                node_id = node.get('nodeId', '')
                node_pos = node.get('nodePosition', {})
                node_map_id = node_pos.get('mapId')
                if node_map_id:
                    self.current_map_id = str(node_map_id)

                wp_speed = edge_speed_by_end_node.get(node_id, self.default_max_speed)
                waypoints.append({
                    'x': float(node_pos.get('x', 0.0)),
                    'y': float(node_pos.get('y', 0.0)),
                    'nodeId': node_id,
                    'max_speed': wp_speed
                })

            if not waypoints:
                self.get_logger().warning(f'[{self.serial_number}] 訂單中無有效點位，忽略執行')
                return

            # 更新當前生效之訂單狀態快照
            self.current_order_id = order_id
            self.current_order_update_id = order_update_id
            self.current_order_nodes = nodes
            self.current_order_edges = edges

            # 若 payload 頂層有提供 mapId 且尚未設定，亦作為備援來源
            if not self.current_map_id and payload.get('mapId'):
                self.current_map_id = str(payload.get('mapId'))

            # 取各段邊速限作為預設參考值
            command_max_speed = max(
                (wp['max_speed'] for wp in waypoints),
                default=self.default_max_speed
            )

            # 組裝 ROS 2 內部控制指令
            ros_cmd = {
                'action': 'MOVE',
                'task_id': order_id,
                'order_update_id': order_update_id,
                'is_update': is_update,
                'map_version': str(payload.get('mapVersion', '1')),
                'max_speed': command_max_speed,
                'waypoints': waypoints
            }

            self._publish_ros_command(ros_cmd)

            ns = self.get_namespace().rstrip('/')
            cmd_topic = f'{ns}/command' if ns else '/command'
            self.get_logger().info(
                f'[{self.serial_number}] 已轉發移動指令至 ROS 2 ({cmd_topic})，'
                f'地圖: {self.current_map_id or "未指定"}, 點位數量: {len(waypoints)}'
            )

        except Exception as e:
            self.get_logger().error(f'[{self.serial_number}] 解析 VDA 5050 訂單時發生錯誤: {e}')

    def on_ros_state_received(self, msg):
        """
        接收來自 virtual_agv 的內部狀態
        封裝為 VDA 5050 State 格式發佈至 MQTT
        """
        try:
            internal_state = json.loads(msg.data)
            self.header_id += 1

            pos = internal_state.get('position', {})
            x = float(pos.get('x', 0.0))
            y = float(pos.get('y', 0.0))
            theta = float(internal_state.get('orientation', 0.0))
            status = internal_state.get('status', 'IDLE')
            current_wp = int(internal_state.get('current_waypoint', 0))

            # 動態計算尚未走完的 nodeStates 與 edgeStates
            node_states = []
            edge_states = []
            if status in ('MOVING', 'PAUSED') and self.current_order_nodes:
                for node in self.current_order_nodes[current_wp:]:
                    node_states.append({
                        'nodeId': node.get('nodeId', ''),
                        'sequenceId': node.get('sequenceId', 0),
                        'released': bool(node.get('released', True)),
                        'nodePosition': node.get('nodePosition', {})
                    })
                edge_start_idx = max(0, current_wp - 1)
                for edge in self.current_order_edges[edge_start_idx:]:
                    edge_states.append({
                        'edgeId': edge.get('edgeId', ''),
                        'sequenceId': edge.get('sequenceId', 0),
                        'released': bool(edge.get('released', True))
                    })

            # 組裝符合 VDA 5050 v2 規範的完整 State JSON 物件
            vda_state = {
                'headerId': self.header_id,
                'timestamp': datetime.now(timezone.utc).isoformat(),
                'version': '2.0.0',
                'manufacturer': self.manufacturer,
                'serialNumber': self.serial_number,
                'orderId': internal_state.get('task_id') or self.current_order_id or '',
                'orderUpdateId': int(internal_state.get('order_update_id', self.current_order_update_id)),
                'lastNodeId': internal_state.get('last_node_id', ''),
                'driving': (status == 'MOVING'),
                'paused': (status == 'PAUSED'),
                'nodeStates': node_states,
                'edgeStates': edge_states,
                'agvPosition': {
                    'x': x,
                    'y': y,
                    'theta': theta,
                    'mapId': self.current_map_id,
                    'positionInitialized': bool(self.current_map_id)
                },
                'velocity': {
                    'vx': float(internal_state.get('linear_velocity', 0.0)),
                    'omega': 0.0
                },
                'actionStates': self.action_states,
                'batteryState': {
                    'batteryCharge': 98.5,
                    'charging': False
                },
                'operatingMode': 'AUTOMATIC',
                'errors': self.errors,
                'safetyState': {
                    'eStop': 'None',
                    'fieldViolation': False
                }
            }

            self.mqtt_client.publish(self.state_topic, json.dumps(vda_state))

        except Exception as e:
            self.get_logger().error(f'[{self.serial_number}] 轉發 VDA 5050 狀態至 MQTT 失敗: {e}')

    def destroy_node(self):
        """節點關閉時先發佈 OFFLINE 狀態，再安全中斷 MQTT 連線"""
        self.publish_connection_state('OFFLINE', wait=True)
        self.mqtt_client.loop_stop()
        self.mqtt_client.disconnect()
        super().destroy_node()


def main(args=None):
    rclpy.init(args=args)
    bridge_node = Vda5050Bridge()

    try:
        rclpy.spin(bridge_node)
    except KeyboardInterrupt:
        pass
    finally:
        bridge_node.destroy_node()
        rclpy.shutdown()


if __name__ == '__main__':
    main()
