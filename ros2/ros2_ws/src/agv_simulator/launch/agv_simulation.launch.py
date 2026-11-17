from launch import LaunchDescription
from launch_ros.actions import Node


def generate_launch_description():
    """
    AGV 虛擬車輛與 VDA 5050 橋接一鍵啟動腳本
    同時啟動：
    1. virtual_agv (運動學仿真核心)
    2. vda5050_bridge (MQTT 協議轉換橋樑)
    """
    namespace = 'agv01'
    robot_id = 'AGV-01'

    # 1. 虛擬車輛節點
    virtual_agv_node = Node(
        package='agv_simulator',
        executable='virtual_agv',
        namespace=namespace,
        name='virtual_agv',
        parameters=[{
            'robot_id': robot_id
        }],
        output='screen'
    )

    # 2. VDA 5050 橋接節點
    vda5050_bridge_node = Node(
        package='agv_simulator',
        executable='vda5050_bridge',
        namespace=namespace,
        name='vda5050_bridge',
        parameters=[{
            'robot_id': robot_id
        }],
        output='screen'
    )

    return LaunchDescription([
        virtual_agv_node,
        vda5050_bridge_node
    ])

