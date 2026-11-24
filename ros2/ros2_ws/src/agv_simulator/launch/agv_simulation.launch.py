from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node
from launch_ros.parameter_descriptions import ParameterValue


def generate_launch_description():
    """
    AGV 虛擬車輛與 VDA 5050 橋接啟動腳本 (全參數化，支援啟動時覆寫車輛與初始點位)
    範例：
      ros2 launch agv_simulator agv_simulation.launch.py \\
        robot_id:=AGV-01 namespace:=agv01 init_map_id:=WAREHOUSE_120 \\
        init_x:=3.0 init_y:=3.0 init_node_id:=P_001
    """
    launch_args = [
        DeclareLaunchArgument('robot_id', default_value='AGV-01', description='車輛編號'),
        DeclareLaunchArgument('namespace', default_value='agv01', description='ROS 2 命名空間'),
        DeclareLaunchArgument('manufacturer', default_value='agv-lab', description='VDA 5050 製造商代碼'),
        DeclareLaunchArgument('mqtt_broker', default_value='mqtt', description='MQTT Broker 位址'),
        DeclareLaunchArgument('mqtt_port', default_value='1883', description='MQTT Broker 連接埠'),
        DeclareLaunchArgument('init_map_id', default_value='', description='初始地圖代碼'),
        DeclareLaunchArgument('init_x', default_value='0.0', description='初始 X 座標 (公尺)'),
        DeclareLaunchArgument('init_y', default_value='0.0', description='初始 Y 座標 (公尺)'),
        DeclareLaunchArgument('init_yaw', default_value='0.0', description='初始朝向角 (弧度)'),
        DeclareLaunchArgument('init_node_id', default_value='', description='初始所在點位代碼'),
        DeclareLaunchArgument('max_speed', default_value='1.5', description='車輛最大線速度 (m/s)'),
        DeclareLaunchArgument('acceleration', default_value='0.8', description='最大加速度 (m/s^2)'),
        DeclareLaunchArgument('deceleration', default_value='1.0', description='最大減速度 (m/s^2)'),
        DeclareLaunchArgument('angular_speed', default_value='1.5', description='最大角速度 (rad/s)'),
    ]

    namespace = LaunchConfiguration('namespace')

    # 1. 虛擬車輛節點
    virtual_agv_node = Node(
        package='agv_simulator',
        executable='virtual_agv',
        namespace=namespace,
        name='virtual_agv',
        parameters=[{
            'robot_id': LaunchConfiguration('robot_id'),
            'init_x': ParameterValue(LaunchConfiguration('init_x'), value_type=float),
            'init_y': ParameterValue(LaunchConfiguration('init_y'), value_type=float),
            'init_yaw': ParameterValue(LaunchConfiguration('init_yaw'), value_type=float),
            'init_node_id': LaunchConfiguration('init_node_id'),
            'max_speed': ParameterValue(LaunchConfiguration('max_speed'), value_type=float),
            'acceleration': ParameterValue(LaunchConfiguration('acceleration'), value_type=float),
            'deceleration': ParameterValue(LaunchConfiguration('deceleration'), value_type=float),
            'angular_speed': ParameterValue(LaunchConfiguration('angular_speed'), value_type=float),
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
            'robot_id': LaunchConfiguration('robot_id'),
            'manufacturer': LaunchConfiguration('manufacturer'),
            'mqtt_broker': LaunchConfiguration('mqtt_broker'),
            'mqtt_port': ParameterValue(LaunchConfiguration('mqtt_port'), value_type=int),
            'init_map_id': LaunchConfiguration('init_map_id'),
            'default_max_speed': ParameterValue(LaunchConfiguration('max_speed'), value_type=float),
        }],
        output='screen'
    )

    return LaunchDescription([
        *launch_args,
        virtual_agv_node,
        vda5050_bridge_node,
    ])
