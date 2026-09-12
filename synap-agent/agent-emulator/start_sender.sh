#!/bin/sh
while true
do
    # 发送（1-10随机个）设备的数据
    ./agent-emulator --host synap-server --device_count $((RANDOM % 10 + 1))
    # 休眠 1 小时
    sleep 1h
done