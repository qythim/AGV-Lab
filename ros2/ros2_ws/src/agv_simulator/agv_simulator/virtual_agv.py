
import json
import math

import rclpy
from rclpy.node import Node
from std_msgs.msg import String


class VirtualAGV(Node):

    def __init__(self):
        super().__init__('virtual_agv')

        # Robot identity
        self.robot_id = 'AGV-01'

        # Current robot position in ROS coordinates
        self.x = 0.0
        self.y = 0.0
        self.yaw = 0.0

        # Movement configuration
        self.speed = 1.0
        self.waypoints = []
        self.target_index = 0

        self.task_id = None
        self.map_version = None

        self.status = 'IDLE'

        # Subscribe to movement commands
        self.command_sub = self.create_subscription(
            String,
            '/agv/agv01/command',
            self.command_callback,
            10
        )

        # Publish robot state
        self.state_pub = self.create_publisher(
            String,
            '/agv/agv01/state',
            10
        )

        # Update movement every 50 ms
        self.timer = self.create_timer(
            0.05,
            self.update_motion
        )

        self.get_logger().info(
            'Virtual AGV initialized'
        )

    def command_callback(self, msg):

        try:
            command = json.loads(msg.data)

            action = command.get('action')

            if action == 'MOVE':

                waypoints = command.get('waypoints', [])
                speed = float(command.get('max_speed', 1.0))

                if not waypoints or speed <= 0:
                    self.get_logger().warning(
                        'Invalid movement command'
                    )
                    return

                self.waypoints = waypoints
                self.target_index = 0

                self.speed = speed
                self.task_id = command.get('task_id')
                self.map_version = command.get('map_version')

                self.status = 'MOVING'

                self.get_logger().info(
                    f"Task started: {self.task_id}"
                )

            elif action == 'STOP':

                self.waypoints = []
                self.target_index = 0
                self.status = 'IDLE'

            elif action == 'PAUSE':

                if self.status == 'MOVING':
                    self.status = 'PAUSED'

            elif action == 'RESUME':

                if self.status == 'PAUSED':
                    self.status = 'MOVING'

        except (ValueError, TypeError, KeyError) as error:
            self.get_logger().error(
                f'Invalid command: {error}'
            )

        except json.JSONDecodeError:
            self.get_logger().error(
                'Command JSON parsing failed'
            )

    def update_motion(self):

        dt = 0.05

        if self.status == 'MOVING':

            if self.target_index >= len(self.waypoints):

                self.status = 'COMPLETED'

            else:

                target = self.waypoints[self.target_index]

                target_x = float(target['x'])
                target_y = float(target['y'])

                dx = target_x - self.x
                dy = target_y - self.y

                distance = math.sqrt(dx * dx + dy * dy)

                if distance < 0.03:

                    self.x = target_x
                    self.y = target_y

                    self.target_index += 1

                    if self.target_index >= len(self.waypoints):
                        self.status = 'COMPLETED'

                else:

                    step = min(
                        self.speed * dt,
                        distance
                    )

                    self.yaw = math.atan2(dy, dx)

                    self.x += dx / distance * step
                    self.y += dy / distance * step

        self.publish_state()

    def publish_state(self):

        state = {
            'robot_id': self.robot_id,
            'task_id': self.task_id,
            'map_version': self.map_version,

            'position': {
                'x': self.x,
                'y': self.y
            },

            'orientation': self.yaw,

            'linear_velocity': (
                self.speed if self.status == 'MOVING' else 0.0
            ),

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
