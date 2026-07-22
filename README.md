# J2ME LLM

**简体中文** | [English](README.en.md)

J2ME LLM 是一个面向 CLDC 1.1 / MIDP 2.0 手机和 Java ME 模拟器的轻量 OpenAI Chat Completions 兼容客户端。v0.2.0 在索爱 W995 可运行的 v0.1 基础上加入多档案、官方预设、按需模型目录、真正独立的思考开关、低内存请求写出，以及可通过蓝牙传输的离线配置包。

## v0.2.0 功能

- OpenAI、DeepSeek、Kimi、自定义四个固定档案，Key、模型、端点、思考、多模态、模型缓存和聊天历史彼此隔离。
- 首次覆盖升级时读取旧 `J2MELLM_CFG`，迁移到“自定义（旧配置）”；旧配置库不会被删除。
- 每个档案的 RMS 配置与聊天都保存主记录和恢复副本，主记录损坏时优先从副本修复。
- 模型列表只在用户选择“联网获取”时请求；解析器增量读取 `data[].id`，缓存最多 64 个 ID，启动时不联网。
- 请求思考模式支持“自动 / 开启 / 关闭”；“思维链展开 / 折叠”只影响显示，两者互不改动。
- 多模态按档案默认关闭。关闭时不显示图片命令、不组装图片请求，也不解析图片响应扩展。
- 请求 JSON 先精确计算 `Content-Length`，再以 512 B 缓冲直接写入连接；图片 Base64 不再生成一份巨大的中间字符串。
- Canvas 缓存气泡布局，流式响应重绘约每 100 ms 合并一次。
- 活动会话采用 49,152 字符和 32 条消息双预算；单条正文 24,576 字符，思考 8,192 字符。
- 单文件离线网页生成 `.j2cfg`，手机端支持校验、导入、导出，并可在导入后删除明文配置包。
- 内置 Node.js 网关让旧 TLS 手机通过可信局域网 HTTP 访问现代 HTTPS 上游，并代理聊天和模型目录。

## 预设

| 档案 | Chat Completions | Models | 初始模型 | 思考协议 |
| --- | --- | --- | --- | --- |
| OpenAI | `https://api.openai.com/v1/chat/completions` | `https://api.openai.com/v1/models` | 留空，由用户获取或填写 | `reasoning_effort` |
| DeepSeek | `https://api.deepseek.com/chat/completions` | `https://api.deepseek.com/models` | `deepseek-v4-flash` | `thinking.type` + effort |
| Kimi | `https://api.moonshot.cn/v1/chat/completions` | `https://api.moonshot.cn/v1/models` | `kimi-k3` | K3 与 K2.7 Code 常开；K2.5/K2.6 可切换 |
| 自定义 | 可编辑 | 可编辑或从聊天端点推导 | 留空 | 无 / OpenAI effort / thinking object / 常开 |

内置档案默认恢复官方端点；勾选“高级覆盖”后可改成兼容网关。模型与参数会随服务端更新，使用前可按需刷新模型列表并参考 [OpenAI Models](https://developers.openai.com/api/reference/resources/models/methods/list)、[OpenAI Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[DeepSeek API](https://api-docs.deepseek.com/) 和 [Kimi 模型文档](https://platform.kimi.com/docs/models)。

## 构建

需要 Windows PowerShell 和带 `java`、`javac`、`jar` 的 JDK。工具链安装在项目内的 `.tools`，不会修改系统 Java 配置。

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\bootstrap.ps1
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1
```

输出：

- `dist/J2ME-LLM.jar`
- `dist/J2ME-LLM.jad`

构建脚本运行 11 组桌面自测、ECJ Java 1.3 / class 1.1 编译和 Java ME 预验证。发布包不要使用 `-SkipTests`。

启动 MicroEmulator：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\run-emulator.ps1
```

真实 RMS 升级/恢复夹具会创建 v0.1 数据、触发迁移，并破坏主记录验证副本恢复：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\test-rms-upgrade-recovery.ps1
```

## 安装和覆盖升级

优先把 JAR 与 JAD 放在同一目录并通过 JAD 安装。应用请求 HTTP/HTTPS 权限；文件读写是可选权限，仅在图片或配置包功能被调用时使用。

从 v0.1 更新时：

1. 不要卸载旧应用；卸载 Java ME suite 通常会删除其 RMS。
2. 保持 `MIDlet-Name: J2ME LLM` 与 `MIDlet-Vendor: Chihoko` 不变，用 v0.2.0 JAD 执行“更新/替换”。
3. 首次启动确认活动档案为“自定义（旧配置）”，再核对端点、Key、模型和旧聊天。
4. 升级完成后立即导出一份 `.j2cfg` 配置备份。

RMS 属于 MIDlet suite；覆盖是否被手机识别为同一 suite 仍取决于厂商安装器。完整步骤和恢复矩阵见 [更新与恢复](docs/UPDATE_AND_RECOVERY.md)。

## 离线配置包与蓝牙

在安卓手机或电脑上直接打开 [provisioner/index.html](provisioner/index.html)。页面无网络依赖，不会上传表单：

1. 填写四个档案并生成 `J2ME-LLM-config-v2.j2cfg`。
2. 使用安卓系统分享，通过蓝牙发给 Java 手机。
3. 在应用“档案 → 导入配置”中选择文件。
4. 导入成功后可直接删除手机上的配置包，同时清理安卓下载目录和蓝牙收件箱副本。

`.j2cfg` 使用版本化 JSON、Base64 payload 和 CRC-32。它能发现传输损坏，但**没有加密或来源认证**；API Key 仍是明文。建议导入可撤销、有限额的网关令牌，不要长期保存主账户密钥。格式和离线检查见 [生成器说明](provisioner/README.md)。

导出入口为“档案 → 导出配置”，默认写入第一个可写文件系统根目录的 `J2ME-LLM-backup-<毫秒时间戳>.j2cfg`。导入/导出只包含档案配置，不包含聊天记录。

## 思考与思维链

“设置 → 思考模式”决定以后请求是否发送控制字段：

- 自动：不发送控制参数，由模型决定。
- 开启：按档案协议发送开启和所选 effort。
- 关闭：OpenAI 风格发送 `reasoning_effort: none`，DeepSeek/Kimi K2 风格发送 `thinking.type: disabled`。
- Kimi K3 与 K2.7 Code 始终思考，选择关闭会规范为开启；K3 使用 `reasoning_effort`，K2.7 Code 不发送开关字段。

聊天界面的“思维链”命令只展开或折叠已经收到的 reasoning 文本，不会改变请求思考模式。不是所有模型都支持每种值；服务端拒绝时应改回“自动”。

## 多模态与内存

多模态默认关闭。开启后，有 JSR-75 的设备可选择不超过 96 KiB、且不超过 65,536 像素的 JPG、PNG、GIF 或 WebP；请求使用 `image_url` data URL 和 `detail: low`。返回图片属于兼容扩展，远程下载上限 256 KiB。

图片会显著增加堆占用。原始发送字节在请求写出后释放，旧预览会随会话预算回收；解码失败或内存不足只影响图片预览，不应阻止文字聊天。MIDP 解码器通常以 JPEG/PNG 最可靠。

## 老手机 HTTPS/TLS 网关

W995 等旧设备可能因 TLS 版本或根证书无法直连现代 HTTPS。项目提供 [gateway/server.js](gateway/server.js)：网关持有真实上游 Key，手机只保存设备令牌，并连接可信局域网地址，例如：

- Chat：`http://192.168.1.10:8787/v1/chat/completions`
- Models：`http://192.168.1.10:8787/v1/models`

局域网 HTTP 不是端到端加密，设备令牌、提示词、回答和图片可能被同网段观察。不要把端口暴露到公网；模拟器默认监听 `127.0.0.1`，真机需要时才监听局域网地址并配置防火墙。详见 [网关说明](gateway/README.md)。

## 文档

- [用户指南](docs/USER_GUIDE.md)
- [更新、迁移与恢复](docs/UPDATE_AND_RECOVERY.md)
- [架构、协议和内存预算](docs/ARCHITECTURE.md)
- [开发、测试与发布清单](docs/DEVELOPMENT.md)
- [离线配置包格式](provisioner/README.md)
- [TLS 兼容网关](gateway/README.md)

## 已知边界

- 核心协议仍是 Chat Completions，不是 Responses API；工具调用和标准图片生成未接入。
- 厂商思考字段和兼容图片输出并不统一，预设只能覆盖已知协议。
- Key 保存在 RMS 中，没有硬件安全区；安全强度取决于设备、文件权限和网关策略。
- `.j2cfg` 不备份聊天；手机卸载应用后 RMS 通常无法恢复。
- 模拟器不能代表 W995 的 TLS、字体、JSR-75、堆大小和图片解码器，发布前仍需实机回归。




