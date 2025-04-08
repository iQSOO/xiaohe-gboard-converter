#!/bin/bash

cd /root

echo "【1】激活虚拟环境..."
if [ -f ~/xiaohe-env/bin/activate ]; then
    source ~/xiaohe-env/bin/activate
else
    echo "未找到虚拟环境，正在创建..."
    apt update && apt install -y python3-venv
    python3 -m venv ~/xiaohe-env
    source ~/xiaohe-env/bin/activate
    pip install pypinyin
fi

echo "【2】检查 Python 脚本是否存在..."
if [ ! -f convert_to_xiaohe_txt.py ]; then
    echo "错误：未找到 convert_to_xiaohe_txt.py，请先上传脚本！"
    exit 1
fi

echo "【3】后台启动转换任务..."
nohup python convert_to_xiaohe_txt.py > xiaohe.log 2>&1 &
echo "任务已在后台运行，日志请查看：tail -f xiaohe.log"
