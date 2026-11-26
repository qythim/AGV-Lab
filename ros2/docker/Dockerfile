FROM ros:jazzy-ros-base

ENV DEBIAN_FRONTEND=noninteractive

# 安裝 ROS 2 建置工具與 VDA 5050 所需之 MQTT / NumPy 套件
RUN apt-get update && apt-get install -y \
    python3-colcon-common-extensions \
    python3-rosdep \
    python3-pip \
    python3-numpy \
    python3-paho-mqtt \
    && rm -rf /var/lib/apt/lists/*

# 設定 ROS 2 工作區並複製原始碼
WORKDIR /workspace/ros2_ws
COPY ros2_ws/src /workspace/ros2_ws/src

# 在建置映像檔時預先編譯 agv_simulator 套件
RUN bash -lc "source /opt/ros/jazzy/setup.bash && colcon build --packages-select agv_simulator"

# 容器啟動後自動載入環境並啟動多車模擬器
CMD ["bash", "-lc", "source /opt/ros/jazzy/setup.bash && source /workspace/ros2_ws/install/setup.bash && ros2 launch agv_simulator agv_simulation.launch.py"]