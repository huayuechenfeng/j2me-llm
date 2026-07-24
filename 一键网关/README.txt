J2ME LLM 独立一键网关（Windows x64）
====================================

这个压缩包已经包含 Node.js 运行时和网关程序。
不需要安装 Node.js，也不需要下载 J2ME LLM 源码。
整个文件夹可以解压或复制到任意位置，但不要只复制 BAT 文件。


一、最短使用步骤
----------------

1. 解压整个压缩包。
2. 用记事本打开 gateway.conf。
3. 至少填写：

   UPSTREAM_URL=模型聊天端点
   UPSTREAM_MODELS_URL=模型列表端点
   UPSTREAM_API_KEY=真实上游 API Key

4. 保存配置，双击“启动网关.bat”。
5. Windows 防火墙询问时，只允许“专用网络”。
6. BAT 窗口会显示手机需要填写的 Chat、Models 和 API Key。
7. 网关使用期间不要关闭 BAT 窗口。


二、常用服务商地址
------------------

OpenAI：
UPSTREAM_URL=https://api.openai.com/v1/chat/completions
UPSTREAM_MODELS_URL=https://api.openai.com/v1/models

DeepSeek：
UPSTREAM_URL=https://api.deepseek.com/chat/completions
UPSTREAM_MODELS_URL=https://api.deepseek.com/models

Kimi 国内站：
UPSTREAM_URL=https://api.moonshot.cn/v1/chat/completions
UPSTREAM_MODELS_URL=https://api.moonshot.cn/v1/models

UPSTREAM_MODEL 通常留空，由手机选择模型。如果填写，网关会强制使用该模型。


三、手机设备令牌
----------------

配置文件默认是：

DEVICE_TOKEN=AUTO

第一次运行会生成一个 12 位纯数字令牌并写回 gateway.conf，例如：

582104739621

手机 J2ME LLM 的 API Key 应填写这 12 位数字，而不是真实上游 API Key。
纯数字是为了方便老式九宫格输入法输入。

需要更换令牌时，把 DEVICE_TOKEN 重新改成 AUTO，再运行 BAT。
旧版 BAT 生成的 UUID 令牌会自动迁移成 12 位数字。


四、手机中的配置
----------------

BAT 假设显示电脑地址为 192.168.1.20，端口为 8787：

聊天端点：http://192.168.1.20:8787/v1/chat/completions
模型端点：http://192.168.1.20:8787/v1/models
API Key：BAT 显示的 12 位数字

先在手机浏览器打开：

http://192.168.1.20:8787/health

如果看到包含 ok、true 和 j2me-llm-gateway 的文字，说明手机已经连通电脑。


五、故障排查
------------

手机打不开 /health：
- 确认手机和电脑连接同一个 Wi-Fi。
- Windows 网络类型应为“专用网络”。
- 在防火墙提示中允许包内 runtime\node.exe 访问专用网络。
- 公共 Wi-Fi 可能启用了客户端隔离，手机无法访问电脑。
- 电脑 IP 变化后，以 BAT 本次显示的地址为准。

返回 401 Invalid device token：
- 手机填写的必须是 BAT 显示的 12 位数字。
- 不要把真实上游 API Key 填到手机里。

返回 502 Upstream request failed：
- 检查 UPSTREAM_URL、UPSTREAM_MODELS_URL 和真实 API Key。
- 可临时设置 LOG_ERRORS=1，重新运行后查看脱敏错误。

端口被占用：
- 把 PORT=8787 改成其他端口，例如 PORT=8788。
- 手机端点中的端口也要同步修改。

多个服务商：
- 每个网关进程只对应一套上游配置。
- 复制整个文件夹多份，为每份设置不同端口和上游地址，然后分别运行。


六、安全提醒
------------

- gateway.conf 含有真实 API Key，不要上传、分享或放入公共网盘。
- 手机到电脑使用的是局域网 HTTP，只能在可信家庭网络中使用。
- 不要在路由器上把网关端口映射到公网。
- 12 位数字令牌用于可信局域网的输入便利，不应当作互联网级认证凭据。
- API Key 泄漏后应立即到服务商控制台撤销并重新生成。


七、停止与自测
--------------

关闭 BAT 窗口或按 Ctrl+C 即可停止网关。

高级用户可在命令行运行：

启动网关.bat --self-test

看到 Gateway self-test passed 表示压缩包内的网关运行正常。


运行时说明
----------

包内包含 Node.js v24.14.0 Windows x64 官方运行时。
Node.js 的许可文本位于 runtime\NODE-LICENSE.txt。
