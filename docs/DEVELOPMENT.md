



# J2ME LLM v0.2 开发、测试与发布

本文说明如何在 Windows 上准备 Java ME 工具链、构建 v0.2、运行自动化测试、用 MicroEmulator 调试，以及在 Sony Ericsson W995 一类真机上做发布验收。更新/恢复的专门测试见 [UPDATE_AND_RECOVERY.md](UPDATE_AND_RECOVERY.md)，内部设计见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 1. 开发环境

必需：

- Windows PowerShell 5.1 或 PowerShell 7；
- 可从命令行调用 `java`、`javac`、`jar` 的 JDK；
- 首次下载工具链时可联网；
- 建议 Git，用于核对修改和发布版本。

可选：

- Node.js 18+，仅用于 TLS 兼容网关及其集成测试；
- 安卓/桌面现代浏览器，用于测试离线 `provisioner/index.html`；
- 支持 CLDC 1.1 / MIDP 2.0 的目标手机；
- 蓝牙或存储卡，用于 `.j2cfg` 和安装包传输。

工具链安装在项目内的 `.tools`，不修改系统级 Java 配置。`bootstrap.ps1` 当前固定下载/准备：

- Eclipse ECJ 4.6.1；
- MicroEmulator CLDC API 1.1、MIDP API 2.0 与 JSR-75 2.0.4；
- MicroEmulator 运行时依赖；
- Apache Maven 3.9.16，仅用于拉取模拟器依赖；
- ProGuard 7.9.1，用于 Java ME 预验证。

首次准备：

```powershell
java -version
javac -version
jar --version
powershell -ExecutionPolicy Bypass -File .\tools\bootstrap.ps1
```

下载完成后，日常构建不需要再次运行 bootstrap；若删除 `.tools`，需重新联网准备。

## 2. 项目布局

```text
config/                 MIDlet manifest
src/.../model/          档案、消息、延迟媒体状态
src/.../net/            聊天、SSE、模型目录与流式 JSON 写出
src/.../store/          v0.2 RMS、CRC、v0.1 迁移
src/.../provision/      .j2cfg 编解码和 JSR-75 文件服务
src/.../ui/             Canvas、表单、档案/模型/文件界面
src/.../util/           UTF-8、JSON、Base64、CRC 等小型实现
tests/                  可在桌面 JVM 运行的无框架自测
gateway/                Node.js TLS 兼容网关
provisioner/            单文件离线配置生成器
tools/                  bootstrap、build、emulator 脚本
dist/                   构建生成的 JAR/JAD
docs/                   用户、架构、开发、更新恢复文档
```

不要把 `.tools`、`build`、`dist` 中间产物、真实 `.j2cfg`、API Key 或网关 `.env` 提交到版本库。

## 3. 一键构建

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1
```

构建步骤：

1. 清理并重新创建项目内 `build/classes`、`build/tests`、`build/raw` 与 `dist`；脚本先验证目标确实位于工作区，避免误删外部目录。
2. 用桌面 `javac` 编译纯 Java 自测所需源码与 `tests`。
3. 逐个运行自测主类，任一返回非零即停止。
4. 用 ECJ 按 `-source 1.3 -target 1.1` 编译全部 MIDP 源码，bootclasspath 指向 CLDC 1.1。
5. 打包 raw JAR。
6. 用 ProGuard `-microedition` 完成预验证；发布构建不压缩、不优化、不混淆，方便定位真机问题。
7. 生成与最终 JAR 大小一致的 JAD。
8. 检查最终 JAR 确实包含 MIDlet 主类。

输出：

- `dist/J2ME-LLM.jar`
- `dist/J2ME-LLM.jad`

只在定位编译/打包问题时跳过测试：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1 -SkipTests
```

发布候选包禁止使用 `-SkipTests`。

## 4. 自动化自测

v0.2 的自测都是普通 `public static void main`，不依赖 JUnit；需要 MIDP 类型的请求测试使用最小桌面 stub，便于老工具链和离线构建。构建脚本应运行以下项目：

| 自测 | 覆盖点 |
| --- | --- |
| `JsonSelfTest` | JSON 对象/数组/字符串、转义、数字与错误输入 |
| `ThinkingFilterSelfTest` | 分段 `<think>` 标签、正文和思考分流、结束边界 |
| `MediaSelfTest` | Base64 与图片引用解析 |
| `ImageDimensionsSelfTest` | PNG/GIF/JPEG/WebP 头部尺寸、畸形数据与像素上限 |
| `JsonStreamWriterSelfTest` | UTF-8/JSON 转义的精确 `Content-Length` 与流式 Base64 |
| `ChatRequestWriterSelfTest` | 两遍请求写出、思考协议、多模态开关和 Kimi reasoning history |
| `ModelCatalogParserSelfTest` | 任意分块、UTF-8、`data[].id`、去重/截断、响应大小和畸形 JSON |
| `ProfileCodecSelfTest` | v2 profile 往返、固定四档案规范化、字段上限、CRC 损坏拒绝、旧配置字段迁移编解码 |
| `ConversationRecordValidatorSelfTest` | 历史记录头、档案 ID、计数边界和 CRC 的无消息解码校验 |
| `ProvisioningCodecSelfTest` | `.j2cfg` 往返、Base64/CRC/版本、字段和文件上限、重复 ID/活动 ID |
| `ProvisioningMapperSelfTest` | 四固定档案导入导出、部分导入保留、常开思考规范化、活动档案和模型缓存种子 |

新增纯算法模块时，优先继续使用这种无依赖自测。任何需要 MIDP 类的测试都应尽量把可验证逻辑分离到不依赖设备的 codec/parser，设备 API 留给模拟器和真机验收。

测试还应满足：

- 用多个分块大小喂增量解析器，包括一个字节一块；
- 对中文、代理对、反斜杠、控制字符测试 UTF-8/JSON 字节长度；
- 对每个硬上限测试“刚好等于”和“超过 1”；
- 对 CRC、magic、格式版本和计数分别破坏；
- 确认失败不返回半份档案或半份模型列表。

## 5. MicroEmulator

运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\run-emulator.ps1
```

脚本会先完整构建，再临时映射一个空闲盘符来绕过 MicroEmulator 2.0.4 对 Unicode JAR URL 的限制，把最终 JAR 交给模拟器；退出模拟器后会移除映射。至少检查：

1. MIDlet 可启动，中文菜单和气泡无乱码；
2. 四档案可切换，标题、设置和聊天随活动档案变化；
3. 多模态关闭时没有图片发送负担；
4. 空模型不能发送，错误提示能返回原界面；
5. 流式响应期间可停止，UI 不冻结；
6. 思考请求模式与“思维链”展开/折叠互不改变；
7. 长响应重绘大约每 100 ms 合并一次，方向键滚动仍可用；
8. 保存后退出并重启，活动档案、模型缓存与各历史仍在；
9. 导入/导出在具备 JSR-75 的运行配置下工作；缺 JSR-75 时给出能力提示而不是启动失败。

模拟器的 HTTPS、根证书、堆大小、LCDUI 字体和 JSR-75 行为不能代表 W995。模拟器通过只是进入真机测试的门槛。

### 5.1 实际 RMS 升级/恢复夹具

普通 codec 自测之外，可在隔离的 MicroEmulator `user.home` 中运行真实 `RecordStore` 生命周期：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\test-rms-upgrade-recovery.ps1
```

脚本创建 v0.1 格式的配置和聊天记录，并预建空的 v0.2 配置/聊天库来模拟首次写入前掉电；随后验证仍能迁移且旧库保留，再破坏配置与聊天主记录，验证从第二条 RMS 记录恢复。它使用临时 `Q:` 映射绕过 MicroEmulator 2.0.4 对 Unicode JAR URL 的限制，结束时会移除映射；测试数据只写在 `build/rms-upgrade-recovery`。

## 6. 网络测试

### 6.1 使用本地兼容端点

优先用不会产生费用的本地假服务覆盖：

- SSE 每个 JSON delta 被拆成多个 TCP/行读取分块；
- `[DONE]` 正常结束；
- 流式开关打开但服务器返回普通 JSON；
- 非 2xx JSON 错误与超长错误；
- `reasoning_content`、`reasoning`、`analysis`、跨 chunk `<think>`；
- 模型目录顺序、重复 ID、64 项截断和 128 KiB 拒绝；
- 连接中断与用户取消；
- 多模态开/关时响应图片扩展字段是否被忽略/识别。

不要在自动化日志里打印 Authorization、`.j2cfg` payload 或完整用户提示词。

### 6.2 TLS 兼容网关自测

```powershell
node .\gateway\self-test.js
```

该测试覆盖 health、设备令牌、聊天与模型路由、上游鉴权、模型覆盖、状态/类型转发和 SSE 响应流。随后做手工测试：

```powershell
$env:UPSTREAM_API_KEY='受限测试密钥'
$env:DEVICE_TOKEN='至少十二字符的随机测试令牌'
$env:HOST='127.0.0.1'
node .\gateway\server.js
```

模拟器聊天端点设为 `http://127.0.0.1:8787/v1/chat/completions`，模型端点设为 `http://127.0.0.1:8787/v1/models`。真机测试才把 HOST 改为 `0.0.0.0`，并用防火墙只允许可信局域网访问。

### 6.3 真实提供商烟雾测试

每个官方档案只做短提示、低成本模型的最小验证：

1. 直接获取模型列表（设备 TLS 允许时）；
2. AUTO + 纯文字 + 流式；
3. ON/OFF 的协议参数是否被该模型接受；
4. 非流式一次；
5. 只有模型明确支持时才测图片。

服务端模型和参数会变化。OpenAI 参考 [模型目录](https://developers.openai.com/api/reference/resources/models/methods/list) 与 [Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)，DeepSeek 参考 [API 文档](https://api-docs.deepseek.com/) 与 [思考模式](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode)，Kimi 参考 [模型目录](https://platform.kimi.com/docs/models) 与 [API 概览](https://platform.kimi.com/docs/api/overview)。记录测试日期与实际模型 ID，不要只写“官方预设通过”。

## 7. 离线生成器测试

`provisioner/index.html` 必须能以 `file://` 直接打开，不得依赖 CDN、字体、分析脚本或后端。测试 Chrome/Android 浏览器至少覆盖：

- 页面断网打开和生成；
- 中文提示词、URL、反斜杠和引号往返；
- 四档案不同 Key 与活动档案；
- 下载 `J2ME-LLM-config-v2.j2cfg`；
- “载入备份检查”读回同一文件；
- 人为改一个 Base64/CRC 字符后拒绝；
- 32 KiB 文件和 24 KiB payload 上限；
- 生成文件通过 Java `ProvisioningCodecSelfTest` 的固定向量或手机端导入。

网页实现和 Java codec 必须共享以下语义：格式名 `j2me-llm-config`、版本 2、UTF-8 payload、标准 Base64、标准 CRC-32 多项式 `0xEDB88320`、八位大写十六进制 CRC。

## 8. 真机验收清单

以下清单以 Sony Ericsson W995 这类 MIDP 2.0 设备为重点。每个发布候选至少完整执行一次。

### 8.1 安装与启动

- [ ] JAD 安装成功，JAR size 匹配，没有“无效应用”或预验证错误。
- [ ] 首次启动请求的网络/文件权限符合 JAD，拒绝可选文件权限后文字功能仍能启动。
- [ ] 冷启动、退到后台、恢复、正常退出、强制结束后再次启动均不崩溃。
- [ ] 中文、英文、长模型名和端点在小屏幕可读。

### 8.2 四档案与 RMS

- [ ] OpenAI、DeepSeek、Kimi、自定义分别保存不同 Key/端点/模型。
- [ ] 切换后标题、模型、思考、多模态和历史都对应当前档案。
- [ ] 每档案发两轮对话，退出 MIDlet、重启手机后仍不串档。
- [ ] 清空当前历史不会清除其他档案，也不会清除配置。
- [ ] 模型列表只在用户操作时联网；缓存重启后仍可选择。

### 8.3 思考与流式 UI

- [ ] AUTO 不额外发送思考控制字段。
- [ ] 可切换模型的 ON 与 OFF 均被服务端接受；不支持时错误清晰。
- [ ] Kimi K3/K2.7 Code 选择 OFF 时不会发出无效关闭组合；K3 只发 low/high/max effort，K2.7 Code 不发控制字段；K2.5/K2.6 不附带 effort。
- [ ] “思维链”折叠只改变显示；切换前后不自动重发请求。
- [ ] 10 分钟或长响应流式过程中无明显卡死、闪烁和持续按键失灵。
- [ ] 停止请求后可立即继续输入；没有幽灵回调写进下一档案。

### 8.4 内存与多模态

- [ ] 多模态默认关闭，纯文字连续 20 轮无明显内存恶化。
- [ ] 32 KiB 左右长正文、长思考和旧消息回收按预算工作。
- [ ] 96 KiB 且 65,536 像素边界内图片能发送；超过任一上限或无法识别尺寸时拒绝解码。
- [ ] 可用堆不足时显示“选择更小图片”，MIDlet 不退出。
- [ ] 请求结束后图片原始字节释放；切换档案/清空后预览可回收。
- [ ] JPEG/PNG 真机预览；GIF/WebP 即使解码失败也不破坏文字路径。
- [ ] 多模态关闭时忽略服务端图片扩展字段和 Markdown 图片引用。

### 8.5 `.j2cfg`

- [ ] 安卓离线生成 → 蓝牙发送 → W995 导入，全程不需要网页联网。
- [ ] 四档案字段、活动档案、思考和多模态设置准确。
- [ ] CRC 错误、错误版本、超限文件都拒绝且原 RMS 不变。
- [ ] 导出到存储卡/首个根目录，再在安卓生成器读回验证。
- [ ] 导入/导出不会带入聊天历史或图片。
- [ ] 测试后删除含 Key 的文件并确认蓝牙收件箱无副本。

### 8.6 HTTPS 与网关

- [ ] 能直连时验证官方 HTTPS；记录固件证书/TLS结果。
- [ ] 不能直连时，可信 LAN 网关聊天成功；真实 Key 只在网关。
- [ ] 错误设备令牌返回 401，手机显示可读错误。
- [ ] 网关关闭/网络切换/超时后可恢复，不必重装应用。
- [ ] 防火墙确认 8787 未暴露公网。

### 8.7 覆盖更新与恢复

- [ ] 从实际 v0.1.0 包创建配置和历史，再覆盖安装 0.2.0。
- [ ] 安装器明确识别更新，Name/Vendor/签名一致。
- [ ] 旧配置和历史迁入“自定义（旧配置）”，其他档案保持默认。
- [ ] 连续重启两次不会重复迁移。
- [ ] 主记录损坏时从备份恢复；主备都坏时安全失败。
- [ ] `.j2cfg` 导出—清理测试数据—导入恢复成功。

## 9. 内存压力测试方法

不要只看 JAR 大小。真机上依次组合最坏路径：

1. 载入接近 24 条的持久化历史；
2. 展开长思考，快速上下滚动；
3. 接收高频小 token SSE；
4. 在允许范围内附加接近 96 KiB、65,536 像素的图片；
5. 接收接近上限的远程图片；
6. 切换四档案并返回；
7. 退出、重启，重复两轮。

观察是否出现 OOM、白屏、输入延迟、重绘闪烁、历史串档或 RMS 写坏。若设备没有堆监视器，以“重复执行后功能仍稳定”作为行为证据，并记录失败发生前的消息/图片大小。

修改预算时同步更新：源码常量、`ARCHITECTURE.md` 上限表、用户指南中的限制和边界测试。

## 10. Java ME 兼容编码规则

- 保持 Java 1.3 语法；集合使用原始 `Vector`/`Hashtable`。
- 不假设 `StringBuilder`、regex、`java.nio` 或现代 TLS API 存在。
- 设备 API 用接口/小类隔离；JSR-75 路径不要进入普通文字启动的必需类初始化。
- 关闭 `InputStream`、`OutputStream`、`HttpConnection`、`FileConnection` 和 `RecordStore`，避免依赖 finalizer。
- 不在网络线程直接切换 LCDUI Displayable。
- 字节长度按 UTF-8 计算，不能用 `String.length()` 代替 HTTP Content-Length。
- 所有外部长度、计数、嵌套深度和文件路径都应在分配前验证。
- 捕获 OOM 只能作为最后防线；先设确定性上限并尽早释放大数组/Image。
- 保留稳定 profile ID 与 RMS 名；修改格式必须增加版本并提供迁移。

## 11. 发布检查

### 11.1 版本与描述符

- [ ] `config/manifest.mf` 与生成 JAD 都是 `MIDlet-Version: 0.2.0`。
- [ ] Name/Vendor 与 v0.1 完全相同，大小写和空格也相同。
- [ ] JAD 的 `MIDlet-Jar-Size` 等于最终 JAR 实际字节数。
- [ ] 必需权限只有 HTTP/HTTPS；JSR-75 file read/write 为可选权限。
- [ ] 签名身份与旧版本一致，或明确发布为同一未签名 suite。

### 11.2 质量门槛

- [ ] `tools/build.ps1` 完整通过。
- [ ] `node gateway/self-test.js` 通过。
- [ ] MicroEmulator 验收通过。
- [ ] 目标真机清单通过。
- [ ] v0.1 → v0.2 覆盖更新与恢复矩阵通过。
- [ ] `provisioner/index.html` 断网生成/读回通过。
- [ ] JAR/JAD/生成器/网关不含真实 Key、私人端点或测试对话。
- [ ] 文档中的版本、命令、限制与最终二进制一致。

### 11.3 发布产物

建议归档：

```text
J2ME-LLM-0.2.0/
  J2ME-LLM.jar
  J2ME-LLM.jad
  provisioner/index.html
  provisioner/README.md
  gateway/
  docs/
  SHA256SUMS.txt
```

Java ME 手机本身未必校验 SHA-256，但电脑端发布校验和能发现下载或镜像损坏。不要把含密钥的 `.j2cfg` 放进发布目录。

## 12. 问题报告模板

```text
应用版本与 JAR SHA-256：
设备/模拟器与固件：
CLDC/MIDP/JSR-75 能力：
安装方式（JAD/JAR、覆盖/全新、是否签名）：
当前档案与模型（不要提供 Key）：
直连 HTTPS 或网关：
流式/思考模式/多模态：
消息字符数与图片字节数：
复现步骤：
期望结果：
实际提示或行为：
重启后是否仍发生：
```

报告中必须删除 Authorization、`.j2cfg` payload、真实敏感提示词和可识别的私人图片。

本文按 2026-07-22 的 v0.2 工具链与源码结构编写。








