import json
from datetime import datetime, timezone
import rclpy
from rclpy.node import Node
from std_msgs.msg import String
import paho.mqtt.client as mqtt


class Vda5050Bridge(Node):
    """
    VDA 5050 協議轉換橋接節點 (Bridge Node)
    職責：
    1. 接收 ROS 2 虛擬車輛內部狀態 (/agv/agv01/state) -> 轉換為 VDA 5050 State 發佈至 MQTT
    2. 接收 MQTT 上的 VDA 5050 Order 訂單 -> 轉換為 ROS 2 控制指令 (/agv/agv01/command)
    """

    def __init__(self):
        super().__init__('vda5050_bridge')

        # 支援 ROS 2 參數動態設定 (車輛識別碼預設為 'AGV-01')
        self.declare_parameter('robot_id', 'AGV-01')
        self.serial_number = self.get_parameter('robot_id').get_parameter_value().string_value
        self.manufacturer = 'agv-lab'
        self.header_id = 0

        # MQTT Topic 命名規範 (符合 VDA 5050 標準: uagv/v2/manufacturer/serialNumber/topic)
        self.topic_prefix = f'uagv/v2/{self.manufacturer}/{self.serial_number}'
        self.state_topic = f'{self.topic_prefix}/state'
        self.order_topic = f'{self.topic_prefix}/order'

        # 1. 初始化 ROS 2 相對 Topic (command / state)，與同 Namespace 下的 virtual_agv 通訊
        # 發佈控制指令給 virtual_agv
        self.ros_command_pub = self.create_publisher(
            String,
            'command',
            10
        )

        # 訂閱 virtual_agv 發佈的內部狀態
        self.ros_state_sub = self.create_subscription(
            String,
            'state',
            self.on_ros_state_received,
            10
        )

        # 2. 初始化 MQTT 客戶端連線 (相容 paho-mqtt 1.x 與 2.x 標準語法)
        self.mqtt_broker = 'mqtt'
        self.mqtt_port = 1883
        self.mqtt_client = mqtt.Client(client_id=f'{self.serial_number}_vda_bridge')

        self.mqtt_client.on_connect = self.on_mqtt_connect
        self.mqtt_client.on_message = self.on_mqtt_message

        try:
            self.get_logger().info(f'正在連線至 MQTT Broker ({self.mqtt_broker}:{self.mqtt_port})...')
            self.mqtt_client.connect(self.mqtt_broker, self.mqtt_port, keepalive=60)
            self.mqtt_client.loop_start()  # 在背景線程處理 MQTT 網路通訊
        except Exception as e:
            self.get_logger().error(f'MQTT 連線失敗: {e}')

        self.get_logger().info('VDA 5050 Bridge Node 初始化完成')

    def on_mqtt_connect(self, client, userdata, flags, rc, *args):
        """當成功連線至 MQTT Broker 時觸發"""
        if rc == 0:
            self.get_logger().info(f'MQTT 連線成功！開始訂閱訂單 Topic: {self.order_topic}')
            self.mqtt_client.subscribe(self.order_topic)
        else:
            self.get_logger().error(f'MQTT 連線錯誤，返回碼: {rc}')

    def on_mqtt_message(self, client, userdata, msg):
        """
        接收來自 RCS 的 VDA 5050 Order 訂單
        將其轉換為 ROS 2 虛擬車輛可識別的 action: MOVE 指令
        """
        try:
            payload = json.loads(msg.payload.decode('utf-8'))
            self.get_logger().info(f'收到 VDA 5050 訂單: {payload.get("orderId")}')

            # 解析 VDA 5050 nodes 點位清單
            nodes = payload.get('nodes', [])
            waypoints = []
            for node in nodes:
                node_pos = node.get('nodePosition', {})
                waypoints.append({
                    'x': float(node_pos.get('x', 0.0)),
                    'y': float(node_pos.get('y', 0.0)),
                    'nodeId': node.get('nodeId', '')
                })

            if not waypoints:
                self.get_logger().warning('訂單中無有效點位，忽略執行')
                return

            # 組裝 ROS 2 內部控制指令
            ros_cmd = {
                'action': 'MOVE',
                'task_id': payload.get('orderId', 'order_unknown'),
                'map_version': '1.0',
                'max_speed': 1.0,
                'waypoints': waypoints
            }

            cmd_msg = String()
            cmd_msg.data = json.dumps(ros_cmd)
            self.ros_command_pub.publish(cmd_msg)
            self.get_logger().info(f'已轉發移動指令至 ROS 2 (/agv/agv01/command)，點位數量: {len(waypoints)}')

        except Exception as e:
            self.get_logger().error(f'解析 VDA 5050 訂單時發生錯誤: {e}')

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

            # 組裝符合 VDA 5050 規範的 State JSON 物件
            vda_state = {
                'headerId': self.header_id,
                'timestamp': datetime.now(timezone.utc).isoformat(),
                'version': '2.0.0',
                'manufacturer': self.manufacturer,
                'serialNumber': self.serial_number,
                'orderId': internal_state.get('task_id') or '',
                'lastNodeId': internal_state.get('last_node_id', ''),
                'driving': (internal_state.get('status') == 'MOVING'),
                'agvPosition': {
                    'x': x,
                    'y': y,
                    'theta': theta,
                    'mapId': 'BENCHMARK_01',
                    'positionInitialized': True
                },
                'velocity': {
                    'vx': float(internal_state.get('linear_velocity', 0.0)),
                    'omega': 0.0
                },
                'batteryState': {
                    'batteryCharge': 98.5,
                    'charging': False
                },
                'operatingMode': 'AUTOMATIC',
                'safetyState': {
                    'eStop': 'None',
                    'fieldViolation': False
                }
            }

            # 發佈至 MQTT state_topic
            self.mqtt_client.publish(self.state_topic, json.dumps(vda_state))

        except Exception as e:
            self.get_logger().error(f'轉發 VDA 5050 狀態至 MQTT 失敗: {e}')

    def destroy_node(self):
        """節點關閉時安全中斷 MQTT 連線"""
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

