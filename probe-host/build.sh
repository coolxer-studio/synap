#!/bin/bash
# 请输入目标操作系统(可选值有: linux 、windows、darwin)
# 请输入目标架构(可选值有: amd64、arm64)

# 对输入的参数进行校验
if [ $# -ne 2 ]; then
    echo "请输入正确的参数"
    echo "Usage: $0 <os> <aarch>"
    exit 1
fi

# 对输入的第一个参数判断是否为linux 、windows、darwin 合法值
if [ $1 != "linux" ] && [ $1 != "windows" ] && [ $1 != "darwin" ]; then
    echo "请输入正确的操作系统， 合法值为: linux 、windows、darwin"
    echo "Usage: $0 <os> <aarch>"
    exit 1
fi

# 对输入的第二个参数判断是否为amd64、arm64 合法值
if [ $2 != "amd64" ] && [ $2 != "arm64" ]; then
    echo "请输入正确的架构，合法值为: amd64、arm64"
    echo "Usage: $0 <os> <aarch>"
    exit 1
fi

# 开始打包程序
echo "=============================================================================================================================="
echo "======= 开始打包 ====="
echo "=============================================================================================================================="

file_name="x-probe"
# 如果为windows 架构，则将编译后的程序后缀改为.exe
if [ $1 == "windows" ]; then
    file_name="x-probe.exe"
fi

echo "开始编译程序"
CGO_ENABLED=0 GOOS=$1 GOARCH=$2 go build -x -v -o $file_name
# CGO_ENABLED=0 GOOS=$os GOARCH=$aarch go build -x -v -o x-probe_$os\_$aarch
echo "打包成功！！"
