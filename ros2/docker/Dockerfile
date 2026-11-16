
FROM ros:jazzy-ros-base

ENV DEBIAN_FRONTEND=noninteractive

# Install ROS 2 development tools & MQTT for VDA 5050
RUN apt-get update && apt-get install -y \
    python3-colcon-common-extensions \
    python3-rosdep \
    python3-pip \
    python3-numpy \
    python3-paho-mqtt \
    && rm -rf /var/lib/apt/lists/*

# ROS 2 workspace
WORKDIR /workspace/ros2_ws

# Keep container alive for development
CMD ["bash", "-lc", "source /opt/ros/jazzy/setup.bash && sleep infinity"]
