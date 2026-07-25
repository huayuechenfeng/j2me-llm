





# J2ME LLM v0.3 用户指南

J2ME LLM 是面向 CLDC 1.1 / MIDP 2.0 手机和 Java ME 模拟器的 OpenAI Chat Completions 兼容客户端。v0.3 在四个独立服务档案、按需模型目录、思考控制、多模态和离线配置包基础上，新增中英双语与触屏全屏操作。

本文按当前 v0.3 实现说明。不同 Java ME 手机的安装器、证书库、文件选择器和联网确认框并不完全相同；菜单名称或排列可能随机型变化，但数据和安全边界不变。

## 1. 安装前准备

最低运行环境：

- CLDC 1.1；
- MIDP 2.0；
- HTTP/HTTPS Connector；
- 若要导入、导出或发送本地图片，还需要 JSR-75 FileConnection；没有 JSR-75 时，文字聊天和 RMS 档案仍可使用。

### 1.1 语言与触屏操作

首次启动会读取 `microedition.locale`：`zh*` 区域默认显示简体中文，其余区域显示 English。可在聊天界面的“更多 → 语言”中选择“跟随系统”“简体中文”或“English”。偏好独立保存在 `J2MELLM_UI_PREFS`，不会修改档案和聊天记录。

支持指针事件的设备使用全屏 Canvas，底部三个按钮为“输入/停止”“图片/档案”“更多”。“更多”包含档案、设置、语言、思维链、清空和退出；在消息区上下拖动即可滚动。不支持触屏的设备继续使用软键命令和方向键。

建议把 `J2ME-LLM.jar` 与 `J2ME-LLM.jad` 放在同一目录，并从 JAD 安装。JAD 声明网络权限，并把文件读写列为可选权限。手机第一次联网、读文件或写文件时可能再次询问，请按实际需要授权。

如果是从 v0.1 更新，先阅读 [覆盖更新与恢复指南](UPDATE_AND_RECOVERY.md)。最重要的一条是：**选择“更新/替换”，不要先卸载旧版。**

## 2. 四个档案彼此独立

v0.3 固定提供 OpenAI、DeepSeek、Kimi、自定义四个档案。每个档案分别保存 API Key、聊天端点、模型目录端点、模型、系统提示词、流式开关、上下文消息数、思考设置、多模态设置和模型列表缓存；聊天记录也按档案分开。

| 档案 | 默认聊天端点 | 默认模型目录端点 | 初始模型 | 默认思考协议 |
| --- | --- | --- | --- | --- |
| OpenAI | `https://api.openai.com/v1/chat/completions` | `https://api.openai.com/v1/models` | 留空，由用户填写或获取 | `reasoning_effort` |
| DeepSeek | `https://api.deepseek.com/chat/completions` | `https://api.deepseek.com/models` | `deepseek-v4-flash` | `thinking.type` |
| Kimi | `https://api.moonshot.cn/v1/chat/completions` | `https://api.moonshot.cn/v1/models` | `kimi-k3` | 根据模型区分可切换或常开思考 |
| 自定义 | OpenAI 兼容示例值，可修改 | 可填写，也可由聊天端点推导 | 留空 | 可选择兼容策略 |

服务商会新增、弃用或重命名模型，表中的初始模型只代表 v0.3 发布时的预设，不是永久保证。实际可用模型以账户权限和模型目录返回值为准。

### 2.1 第一次配置

1. 打开档案选择，进入要使用的提供商档案。
2. 填写该档案的 API Key。无鉴权的兼容服务或局域网测试服务可以留空。
3. 可直接填写模型；也可以让模型名保持空白，选择“模型列表”后按“联网获取”，再从结果中选择。
4. 按需要修改系统提示词、2–24 条上下文消息数和流式响应。
5. 选定模型后保存档案，再切换为当前档案。

内置档案默认使用官方端点。需要走 TLS 兼容网关时，可开启端点覆盖后改成网关地址。自定义档案的端点始终可编辑。

切换档案不会复制 Key 或聊天记录。例如，OpenAI 与 DeepSeek 即使使用同名模型，仍是两套独立配置和历史。

### 2.2 API Key 的安全边界

API Key 保存在 Java ME 的 RMS 中。老手机通常没有能供 MIDlet 使用的硬件安全区，RMS 也不是加密保险箱：

- 不要把含真实密钥的手机交给不可信的人；
- 优先使用有额度限制、用途受限且可撤销的 Key；
- 通过 HTTP 网关时，手机端填写的是设备令牌，真实上游 Key 留在网关；
- 导出的 `.j2cfg` 同样含明文密钥，传输完成后应删除所有临时副本。

## 3. 按需获取模型列表

应用不会在启动时自动访问 `/models`。只有用户主动选择获取模型列表时，才会发出带 Bearer 鉴权的 GET 请求。这样可减少启动延迟、流量、TLS 握手和堆内存占用。

一次获取会增量解析根对象中的 `data[].id`，最多保留 64 个不重复的模型 ID，并给响应体设置 128 KiB 的安全上限。成功结果和获取时间保存在当前档案的 RMS 中；下次打开档案可直接使用缓存，不需要重新联网。

使用建议：

- 新 Key 或服务商模型更新后再手动刷新；
- 获取失败时，可以继续使用上次缓存或手工输入模型名；若已获取但 RMS 保存失败，列表只在当前会话可用，应用会明确警告；
- 自定义档案若没有填写模型目录端点，会尝试从以 `/chat/completions` 或 `/responses` 结尾的聊天端点推导 `/models`；推导结果不保证适合所有私有服务；
- 当前附带的轻量 TLS 网关同时转发 `POST /v1/chat/completions` 与 `GET /v1/models`；手机端聊天和模型端点都应指向同一网关的对应路径。

OpenAI 的模型目录响应采用 `data` 数组，每项包含 `id`；详见 [OpenAI List models](https://developers.openai.com/api/reference/resources/models/methods/list)。DeepSeek 与 Kimi 也提供兼容的模型目录，分别见 [DeepSeek List Models](https://api-docs.deepseek.com/api/list-models/) 与 [Kimi API 概览](https://platform.kimi.com/docs/api/overview)。

## 4. “思考模式”与“折叠思考”是两件事

v0.3 有两个互不替代的控制：

1. **请求思考模式**决定下一次请求发给服务端什么参数，取值为自动、开启、关闭；
2. **界面折叠/展开思考**只决定已经收到的思考文字是否显示，不改变服务端是否思考，也不会重新发起请求。

请求思考模式的含义：

- **自动**：不额外发送控制字段，让服务端或模型使用默认行为；兼容性最好；
- **开启**：按当前档案的协议发送启用字段，并在适用时发送思考强度；
- **关闭**：按协议发送关闭字段。若模型本身强制思考，应用会采用该模型允许的有效模式，不能保证真正关闭。

协议映射：

| 档案/协议 | 开启 | 关闭 | 注意事项 |
| --- | --- | --- | --- |
| OpenAI effort | `reasoning_effort` 为选定强度 | `reasoning_effort: "none"` | 不是每个模型都接受全部强度或 `none` |
| DeepSeek 可切换思考 | `thinking: {"type":"enabled"}` 并带适用 effort | `thinking: {"type":"disabled"}` | 当前官方模型支持 high/max，部分值会映射 |
| Kimi K2.5/K2.6 | `thinking: {"type":"enabled"}`，不带 effort | `thinking: {"type":"disabled"}` | K2.6 支持保留式思考；普通聊天不强制回传旧推理 |
| Kimi K3 / K2.7 Code | K3 发送适用的 `reasoning_effort`；K2.7 Code 不发控制字段 | 无真正关闭；按开启处理 | 两者都会回传历史 assistant 的 `reasoning_content` |
| 无协议 | 不发送控制字段 | 不发送控制字段 | 用于未知兼容端点，以及未明确识别能力的 Kimi/Moonshot 型号 |

思考强度可使用实现允许的 `minimal`、`low`、`medium`、`high`、`xhigh`、`max`；服务端可能只接受其中一部分。收到的 `reasoning_content`、`reasoning`、`analysis` 或 `<think>…</think>` 会进入独立思考区域。

有关官方字段与模型限制，请查阅 [OpenAI Create chat completion](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[DeepSeek 思考模式](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode) 和 [Kimi K2 Thinking 指南](https://platform.kimi.com/docs/guide/use-kimi-k2-thinking-model)。

不要把界面显示的思考文字当成模型内部推理的完整记录；兼容端点返回什么字段由服务端决定。

## 5. 多模态按需启用

每个档案的多模态默认关闭。关闭时：

- 不显示或不启用图片发送入口；
- 不读取本地图片，不分配图片附件对象；
- 请求只包含文字消息；
- 响应不搜索兼容厂商的图片扩展字段，也不下载图片。

只有确认当前模型支持图片输入、手机有 JSR-75 且内存足够时再启用。图片发送上限为 96 KiB 且 65,536 像素（约 256×256）；选择器目录最多显示前 256 项。未知尺寸或超过像素上限的图片不会交给平台解码器。请求使用 Chat Completions 的 `image_url` content part 和 Base64 data URL，`detail` 固定为 `low`。图片字节在请求写出后释放，不写进聊天历史；历史只保留有限的图片名称或来源元数据。

兼容图片输出不是 OpenAI Chat Completions 的标准图片生成接口。多模态开启后，应用只尝试识别常见兼容字段、data URL 或远程 URL；能否显示还受手机证书、网络、图片编码器和剩余堆内存影响。OpenAI 对图像输入与图像生成接口的区分见 [Create chat completion](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)。

出现内存不足、图片无法解码或 TLS 错误时：先关闭多模态，清空较长会话并重启应用，再尝试尺寸更小的 JPEG/PNG。文字功能不依赖图片模块。

## 6. 聊天、流式输出和历史

发送后，应用把请求 JSON 直接写入网络输出流，并通过 SSE 增量显示正文与思考内容。停止命令会关闭当前连接；服务端已经收到的请求不一定能撤销或退款。

每个档案都有独立历史。上下文消息数取值 2–24，决定一次请求最多带上最近多少条有效消息；RMS 会保存最近 24 条已完成消息，待发送状态和错误响应不会作为正常上下文。为保护老手机内存，单条正文、思考、总会话和持久化字段都有上限，过长内容可能截断，较旧消息也可能被回收。

“清空”只清除当前档案的聊天历史，不删除其他档案的 Key、设置或聊天。

## 7. 离线配置包：免去逐字输入密钥

项目的 `provisioner/index.html` 是单文件离线生成器。可在安卓手机或电脑浏览器中直接打开，不需要 Web 服务器，也不会把表单上传到网络。

推荐流程：

1. 把整个 `provisioner` 目录复制到可信设备；
2. 断网也可直接打开 `index.html`；
3. 填写四个档案，生成 `J2ME-LLM-config-v2.j2cfg`；
4. 用安卓系统分享功能通过蓝牙发送到 Java 手机；
5. 在 J2ME LLM 中选择导入，定位该 `.j2cfg`；
6. 导入成功后核对活动档案、端点和模型，再开始使用；
7. 导入成功提示出现时，可直接选择删除手机上的明文配置包；同时清理安卓下载目录和蓝牙收件箱里的副本。

`.j2cfg` 最大 32 KiB。外层和载荷均有版本标记，载荷使用 Base64 包装并带 CRC-32，用于发现传输损坏或意外修改。**Base64 和 CRC 都不是加密，也不能证明文件来自可信来源。** 手机端会先检查大小、格式、版本、Base64、载荷上限和 CRC，再接受档案。

导入档案配置不会导入聊天记录，也不会立即加载图片。配置包中出现的档案会替换对应槽位；**包中省略的档案会原样保留**，包括其 Key、端点、模型、开关和模型缓存，因此可以只导入一个自定义档案。包未指定活动档案时保留手机当前选择；明确指定时才切换。详细格式见 [离线生成器说明](../provisioner/README.md)。

## 8. 导出备份与恢复

导出会把四个档案、活动档案和相关配置写成 `.j2cfg`，默认文件名为 `J2ME-LLM-backup-<毫秒时间戳>.j2cfg`，位置是手机报告的第一个可写文件系统根目录。每次导出使用新名称；若目标已存在，应用会拒绝写入，绝不会截断或覆盖旧备份。写入时先生成同目录临时文件，逐字节回读校验后再改成最终文件名。设备需要 JSR-75 文件写权限；部分手机会让用户选择存储卡或确认写入。

恢复前建议先在离线生成器中使用“载入备份检查”验证文件，再在手机端导入。导入成功后检查每个档案的端点和模型，最后做一次低风险测试请求。

备份包含明文 API Key，但**不包含聊天记录、模型列表缓存或图片数据**。不要通过邮件、公开网盘或不可信即时通信长期保存它。完整步骤见 [覆盖更新与恢复指南](UPDATE_AND_RECOVERY.md)。

## 9. 老手机 HTTPS/TLS 兼容网关

现代 LLM 端点通常要求新 TLS 和新根证书，索爱等 MIDP 2.0 手机可能因握手算法或证书链过旧而无法直连。项目附带零依赖 Node.js 网关：

```powershell
$env:UPSTREAM_URL='https://api.openai.com/v1/chat/completions'
$env:UPSTREAM_API_KEY='真实上游密钥'
$env:DEVICE_TOKEN='至少十二字符的随机设备令牌'
$env:HOST='0.0.0.0'
node .\gateway\server.js
```

手机档案中填写：

- 聊天端点：`http://<网关局域网地址>:8787/v1/chat/completions`；
- API Key：`DEVICE_TOKEN`，不是上游真实 Key；
- 模型：上游接受的模型名。若网关设置了 `UPSTREAM_MODEL`，网关会覆盖手机传来的模型。

安全规则：

- 模拟器使用时保持默认 `HOST=127.0.0.1`；
- 真机使用 `0.0.0.0` 时，只在可信局域网开放 8787 端口，并设置主机防火墙；
- **绝不把 HTTP 监听端口直接暴露到公网**；
- 局域网 HTTP 会暴露设备令牌、提示词、回答和图片给能监听该网络的人；
- 当前网关处理鉴权后的聊天 POST 与模型目录 GET，并提供无需鉴权的 `/health`；其他路径不代理。

详细设置与自测见 [网关说明](../gateway/README.md)。

## 10. 常见问题

### HTTPS 握手失败或“证书错误”

先在同一手机浏览器测试 HTTPS；若仍失败，通常不是 API Key 问题，而是 TLS/根证书限制。改用可信局域网网关，不要关闭上游 HTTPS。

### 获取模型列表失败，但聊天可以用

检查模型目录端点。若通过附带网关，手机端应填写网关的 `/v1/models`，并确认网关的 `UPSTREAM_MODELS_URL` 可推导或已显式配置。若上游本身没有模型目录，可继续使用缓存或手工输入模型名。

### 开启思考后服务端返回参数错误

把思考模式改回“自动”，或为自定义档案选择“无协议”。模型对 `reasoning_effort` 与 `thinking` 的支持并不完全相同。

### Kimi 的“关闭思考”看起来没有生效

常开思考模型不能由客户端真正关闭。v0.3 会避免发送无效的关闭组合；若必须关闭，请选择服务商提供的可切换思考模型。

### 没有图片或导入/导出入口

确认档案已开启多模态，以及手机具有 JSR-75 FileConnection。某些设备需要在应用权限中单独允许“读取用户数据”和“写入用户数据”。

### 更新后看不到旧配置

不要继续反复安装或卸载。先确认安装器是否把 v0.3 识别为同一个 MIDlet 套件，再按 [恢复指南](UPDATE_AND_RECOVERY.md) 检查旧 RMS 迁移或导入 `.j2cfg` 备份。

## 11. 相关官方文档

- [OpenAI List models](https://developers.openai.com/api/reference/resources/models/methods/list)
- [OpenAI Create chat completion](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)
- [DeepSeek API 文档](https://api-docs.deepseek.com/)
- [DeepSeek List Models](https://api-docs.deepseek.com/api/list-models/)
- [DeepSeek 思考模式](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode)
- [Kimi 开放平台概览](https://platform.kimi.com/docs/overview)
- [Kimi API 概览](https://platform.kimi.com/docs/api/overview)
- [Kimi 模型目录](https://platform.kimi.com/docs/models)
- [Kimi K2 Thinking 指南](https://platform.kimi.com/docs/guide/use-kimi-k2-thinking-model)
- [Oracle Java ME RMS RecordStore](https://docs.oracle.com/javame/config/cldc/ref-impl/midp2.0/jsr118/javax/microedition/rms/RecordStore.html)

以上链接与预设说明按 2026-07-25 核对；模型和服务条款可能随后变化。







