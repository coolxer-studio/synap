# synap-agent消息仿真器

## 运行环境
golang

## 构建说明
```
go build -o agent-emulator
```

## 运行说明
发送数据，如果是加密目录先解密再发送，如果非加密直接发送：
```
./agent-emulator --host 192.168.1.201 --device_count 1 --print_log 1
```

## 加密文件
加密文件（构建时需要）生成data_encryp目录存放加密文件
```
./agent-emulator

```