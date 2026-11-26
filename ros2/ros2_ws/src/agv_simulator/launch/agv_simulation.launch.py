from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node
from launch_ros.parameter_descriptions import ParameterValue


def generate_launch_description():
    """
    AGV 多車模擬器一鍵啟動腳本：
    啟動 SimulatorSupervisorNode (sim_supervisor)，自動監聽 RCS 的 sim/fleet/snapshot 與
    sim/fleet/control 主題，依據資料庫 rcs_agv 狀態動態生成 (SPAWN)、放置 (PLACE) 與銷毀 (DESTROY) 車輛。
    """
    launch_args = [
        DeclareLaunchArgument('manufacturer', default_value='agv-lab', description='VDA 5050 製造商代碼'),
        DeclareLaunchArgument('mqtt_broker', default_value='mqtt', description='MQTT Broker 位址'),
        DeclareLaunchArgument('mqtt_port', default_value='1883', description='MQTT Broker 連接埠'),
        DeclareLaunchArgument(
            'rcs_agv_api_url',
            default_value='http://java21:8080/api/agvs',
            description='RCS 車輛清單 REST API (啟動時保底同步使用)'
        ),
        DeclareLaunchArgument('max_speed', default_value='1.5', description='車輛最大線速度 (m/s)'),
        DeclareLaunchArgument('acceleration', default_value='0.8', description='最大加速度 (m/s^2)'),
        DeclareLaunchArgument('deceleration', default_value='1.0', description='最大減速度 (m/s^2)'),
        DeclareLaunchArgument('angular_speed', default_value='1.8', description='最大原地轉向角速度 (rad/s)'),
    ]

    sim_supervisor_node = Node(
        package='agv_simulator',
        executable='sim_supervisor',
        name='sim_supervisor',
        parameters=[{
            'manufacturer': LaunchConfiguration('manufacturer'),
            'mqtt_broker': LaunchConfiguration('mqtt_broker'),
            'mqtt_port': ParameterValue(LaunchConfiguration('mqtt_port'), value_type=int),
            'rcs_agv_api_url': LaunchConfiguration('rcs_agv_api_url'),
            'max_speed': ParameterValue(LaunchConfiguration('max_speed'), value_type=float),
            'acceleration': ParameterValue(LaunchConfiguration('acceleration'), value_type=float),
            'deceleration': ParameterValue(LaunchConfiguration('deceleration'), value_type=float),
            'angular_speed': ParameterValue(LaunchConfiguration('angular_speed'), value_type=float),
        }],
        output='screen'
    )

    return LaunchDescription([
        *launch_args,
        sim_supervisor_node,
    ])
