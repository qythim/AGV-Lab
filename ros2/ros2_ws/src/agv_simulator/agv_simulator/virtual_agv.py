import json
import math

import rclpy
from rclpy.node import Node
from std_msgs.msg import String


class VirtualAGV(Node):
    """
    虛擬 AGV 運動學模擬節點
    透過 ROS 2 參數動態設定初始位置、節點編號與速度/加減速上限，避免硬編碼。
    """

    def __init__(self, *, node_name: str = 'virtual_agv', namespace: str = '', parameter_overrides=None):
        super().__init__(
            node_name,
            namespace=namespace,
            parameter_overrides=parameter_overrides
        )

        # 宣告可動態配置的車輛識別、初始姿態與運動學參數
        self.declare_parameter('robot_id', 'AGV-01')
        self.declare_parameter('init_x', 0.0)
        self.declare_parameter('init_y', 0.0)
        self.declare_parameter('init_yaw', 0.0)
        self.declare_parameter('init_node_id', '')
        self.declare_parameter('max_speed', 1.5)
        self.declare_parameter('acceleration', 0.8)
        self.declare_parameter('deceleration', 1.0)
        self.declare_parameter('angular_speed', 1.5)

        self.robot_id = self.get_parameter('robot_id').get_parameter_value().string_value
        self.x = self.get_parameter('init_x').get_parameter_value().double_value
        self.y = self.get_parameter('init_y').get_parameter_value().double_value
        self.yaw = self.get_parameter('init_yaw').get_parameter_value().double_value
        self.last_node_id = self.get_parameter('init_node_id').get_parameter_value().string_value

        # 運動學參數配置 (供後續加減速曲線升級直接引用)
        self.max_speed = self.get_parameter('max_speed').get_parameter_value().double_value
        self.acceleration = self.get_parameter('acceleration').get_parameter_value().double_value
        self.deceleration = self.get_parameter('deceleration').get_parameter_value().double_value
        self.angular_speed = self.get_parameter('angular_speed').get_parameter_value().double_value

        # 當前行駛狀態
        self.speed = self.max_speed
        self.current_linear_velocity = 0.0
        self.waypoints = []
        self.target_index = 0

        self.task_id = None
        self.order_update_id = 0
        self.map_version = None
        self.status = 'IDLE'

        # 使用相對 Topic 名稱 (command / state)，支援 ROS 2 Namespace 多車隔離
        self.command_sub = self.create_subscription(
            String,
            'command',
            self.command_callback,
            10
        )

        self.state_pub = self.create_publisher(
            String,
            'state',
            10
        )

        # 20Hz (每 50ms) 執行一次物理位移更新
        self.timer = self.create_timer(
            0.05,
            self.update_motion
        )

        self.get_logger().info(
            f'[{self.robot_id}] Virtual AGV 初始化完成 '
            f'(pos=({self.x:.2f}, {self.y:.2f}), yaw={self.yaw:.2f}, '
            f'node={self.last_node_id or "無"}, max_speed={self.max_speed:.2f} m/s)'
        )

    def command_callback(self, msg):
        try:
            command = json.loads(msg.data)
            action = command.get('action')

            if action == 'MOVE':
                waypoints = command.get('waypoints', [])
                cmd_speed = float(command.get('max_speed', self.max_speed))

                if not waypoints or cmd_speed <= 0:
                    self.get_logger().warning(f'[{self.robot_id}] 收到無效的移動指令，忽略執行')
                    return

                self.waypoints = waypoints
                self.speed = min(cmd_speed, self.max_speed)
                self.task_id = command.get('task_id')
                self.order_update_id = int(command.get('order_update_id', 0))
                self.map_version = command.get('map_version')

                # 滑動視窗無縫銜接：若首個航點即為車輛當前所在點位，直接從第 2 個航點開始推進，避免原地停頓
                first_wp = waypoints[0]
                dist_to_first = math.hypot(
                    float(first_wp.get('x', 0.0)) - self.x,
                    float(first_wp.get('y', 0.0)) - self.y
                )
                if dist_to_first < 0.05 and len(waypoints) > 1:
                    self.last_node_id = first_wp.get('nodeId', self.last_node_id)
                    self.target_index = 1
                else:
                    self.target_index = 0

                self.status = 'MOVING'

                self.get_logger().info(
                    f'[{self.robot_id}] 執行訂單: {self.task_id} (updateId={self.order_update_id}, '
                    f'點位數: {len(waypoints)}, 起始索引: {self.target_index}, 上限速度: {self.speed:.2f} m/s)'
                )

            elif action == 'STOP':
                self.waypoints = []
                self.target_index = 0
                self.task_id = None
                self.order_update_id = 0
                self.current_linear_velocity = 0.0
                self.status = 'IDLE'
                self.get_logger().info(f'[{self.robot_id}] 收到 STOP 指令，已立即停止並清除行程')
                self.publish_state()

            elif action == 'PAUSE':
                if self.status == 'MOVING':
                    self.current_linear_velocity = 0.0
                    self.status = 'PAUSED'
                    self.get_logger().info(f'[{self.robot_id}] 收到 PAUSE 指令，已暫停移動')
                    self.publish_state()

            elif action == 'RESUME':
                if self.status == 'PAUSED':
                    self.status = 'MOVING'
                    self.get_logger().info(f'[{self.robot_id}] 收到 RESUME 指令，恢復移動')
                    self.publish_state()

            elif action == 'INIT_POSITION':
                self.x = float(command.get('x', self.x))
                self.y = float(command.get('y', self.y))
                self.yaw = float(command.get('yaw', self.yaw))
                if 'node_id' in command and command.get('node_id') is not None:
                    self.last_node_id = str(command.get('node_id'))
                self.waypoints = []
                self.target_index = 0
                self.task_id = None
                self.order_update_id = 0
                self.current_linear_velocity = 0.0
                self.status = 'IDLE'
                self.get_logger().info(
                    f'[{self.robot_id}] 已重設初始位置: pos=({self.x:.2f}, {self.y:.2f}), '
                    f'yaw={self.yaw:.2f}, node={self.last_node_id or "無"}'
                )
                self.publish_state()

        except (ValueError, TypeError, KeyError) as error:
            self.get_logger().error(f'[{self.robot_id}] 無效指令內容: {error}')
        except json.JSONDecodeError:
            self.get_logger().error(f'[{self.robot_id}] 指令 JSON 解析失敗')

    def update_motion(self):
        dt = 0.05

        if self.status == 'MOVING':
            if self.target_index >= len(self.waypoints):
                self.current_linear_velocity = 0.0
                self.status = 'COMPLETED'
            else:
                target = self.waypoints[self.target_index]
                target_x = float(target['x'])
                target_y = float(target['y'])

                # 優先採用該航點對應邊的速限，並受限於車輛硬體最大速度
                wp_speed = float(target.get('max_speed', self.speed))
                effective_speed = min(wp_speed, self.max_speed) if wp_speed > 0 else self.speed

                dx = target_x - self.x
                dy = target_y - self.y
                distance = math.sqrt(dx * dx + dy * dy)

                if distance < 0.03:
                    self.x = target_x
                    self.y = target_y
                    self.last_node_id = target.get('nodeId', '')
                    self.target_index += 1

                    if self.target_index >= len(self.waypoints):
                        self.current_linear_velocity = 0.0
                        self.status = 'COMPLETED'
                else:
                    step = min(effective_speed * dt, distance)
                    self.current_linear_velocity = effective_speed
                    self.yaw = math.atan2(dy, dx)
                    self.x += dx / distance * step
                    self.y += dy / distance * step
        else:
            self.current_linear_velocity = 0.0

        self.publish_state()

    def publish_state(self):
        state = {
            'robot_id': self.robot_id,
            'task_id': self.task_id,
            'order_update_id': self.order_update_id,
            'map_version': self.map_version,
            'position': {
                'x': self.x,
                'y': self.y
            },
            'orientation': self.yaw,
            'last_node_id': self.last_node_id,
            'linear_velocity': self.current_linear_velocity,
            'current_waypoint': self.target_index,
            'status': self.status
        }

        msg = String()
        msg.data = json.dumps(state)
        self.state_pub.publish(msg)


def main(args=None):
    rclpy.init(args=args)
    node = VirtualAGV()

    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.shutdown()


if __name__ == '__main__':
    main()
