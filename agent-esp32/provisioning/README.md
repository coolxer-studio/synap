# ESP32 量产配置

每台设备必须使用独立 manifest、UUID 和短时单次 enrollment token。真实 manifest、NVS
密钥、生成的 CSV 和二进制镜像不得提交到仓库。

生产生成命令：

```bash
python tools/generate_provisioning.py \
  /secure/input/device-0001.json \
  provisioning/generated/device-0001.bin \
  --key-file /secure/input/nvs-key.bin
```

`--key-file` 必须是与 `nvs_keys` 分区匹配的 ESP-IDF NVS key partition 镜像；量产烧录时
还必须把同一文件烧录到 `partitions.csv` 中的 `nvs_keys` 地址 `0x9000`。不得复用到不同
产品或把 key 文件和设备镜像存放在同一非受控目录。四个 NVS 数据分区不使用分区表的
旧式 `encrypted` 标志，而由 `CONFIG_NVS_ENCRYPTION` 和安全 NVS API 统一执行 XTS 加密；
`nvs_keys` 自身仍由 Flash Encryption 保护。

仅本地开发可以显式生成未加密镜像：

```bash
python tools/generate_provisioning.py \
  /tmp/synap-esp32-dev.json \
  provisioning/generated/dev.bin \
  --allow-plain-dev
```

烧录地址由 `partitions.csv` 决定，当前 `provision` 分区位于 `0xb000`：

```bash
esptool.py --chip esp32s3 write_flash 0xb000 provisioning/generated/device-0001.bin
```

生产环境同时烧录 key partition：

```bash
esptool.py --chip esp32s3 write_flash \
  0x9000 /secure/input/nvs-key.bin \
  0xb000 provisioning/generated/device-0001.bin
```

量产烧录站应在受控临时目录生成镜像，烧录后删除输入 manifest、token 和中间文件，并
启用 Flash Encryption、NVS Encryption、Secure Boot。探针仅在 handshake finish 成功后
删除 `enroll_token`；它不会自动擦除损坏的身份、配置或队列分区。
