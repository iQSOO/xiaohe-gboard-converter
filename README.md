# XiaoHe Gboard Converter

来自 [iqsoo.com](https://www.iqsoo.com) 的开源项目：小鹤双拼词库转换工具  
自动将词库转换为 Gboard 等输入法支持的“小鹤双拼码表”格式。

---

## 一键安装（推荐方式）

无需手动配置环境、克隆项目，只需一行命令即可自动完成安装与运行：

```bash
curl -sSL https://raw.githubusercontent.com/iQSOO/xiaohe-gboard-converter/main/install_xiaohe.sh | bash
```

**来自 [iqsoo.com](https://www.iqsoo.com) 的开源项目：小鹤双拼词库转换工具**  
自动将词库转换为 Gboard 等输入法支持的“小鹤双拼码表”格式。

---

## 专用运行命令（推荐复制粘贴执行）

### 首次部署并转换：

```bash
cd /root && bash run_auto.sh
```

### 后续运行：

```bash
cd /root && bash run_xiaohe.sh
```

### 查看日志：

```bash
tail -f /root/xiaohe.log
```

---

**说明：**  
- **首次使用** 执行：`bash run_auto.sh`（自动安装并启动）  
- **后续使用** 执行：`bash run_xiaohe.sh`（不重复安装，仅启动转换工具）
