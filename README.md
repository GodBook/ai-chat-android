# AI BOTOY

AI BOTOY 是面向 Android 10 至 Android 16 的原生 AI 聊天应用。配置 OpenAI 兼容接口后，可在类似即时通信软件的界面中进行流式文本或图片对话。

源码仓库：[github.com/GodBook/ai-chat-android](https://github.com/GodBook/ai-chat-android)

## 功能

- 管理多个独立聊天，支持新建、重命名和删除
- 流式显示回复，并支持停止生成、失败重试和复制
- 渲染标题、段落、粗体、斜体、行内代码、链接、列表、引用、GFM 表格与代码块
- 长按 AI 回复，使用 Android 系统选文工具精确选择文字
- 从系统相册选图，以 Base64 data URL 发送给支持视觉输入的模型
- 可选开启后台音量下键截屏问答：截取当前屏幕、创建独立会话发送给 AI，并用类似 QQ 消息通知的横幅在屏幕顶部显示回答
- 可在设置中开启选择/判断题简版回答模式：选择题显示正确选项所在的 A-D 灰色方块，判断题左侧为正确、右侧为错误，持续约 1 秒且不弹出文字回答
- 顶部悬浮回答保持固定大小，可上下滑动查看完整回答、左右滑动关闭，也会在显示一段时间后自动消失
- 可为悬浮回答选择浅蓝、薄荷、杏橙、玫瑰或雾灰背景，并可开启半透明毛玻璃效果
- 可自定义截图随附的 AI 提示词，并可一键恢复默认提示词
- 通过 HTTPS 更新清单检查、下载并安装新版 APK
- 使用 Room 保存聊天记录，DataStore 保存普通设置，Android Keystore 加密 API Key

## 开始使用

1. 打开右上角的“设置”。
2. 填写模型服务的 HTTPS Base URL、模型名称和 API Key；需要发送图片时开启“支持图片”。
3. 点击“保存设置”，返回聊天列表并进入一个聊天。
4. 输入文字或选择图片后发送。生成期间可点击停止按钮，失败或中断的回复可手动重试。

在“设置”中开启“音量下键后台截图问答”后，开关会立即保存，进入后台或重新打开应用不会将其关闭，只有用户手动关闭开关才会停用功能。Android 11 及以上需要开启悬浮窗和音量监听（无障碍服务），截图由无障碍服务直接完成；Android 10 还需要单独授予屏幕捕获权限。授权完成后，即使应用退到后台，按音量下键也会创建一个“截屏问答”会话并显示 AI 回答。

默认截图提示词为：“回答这张图片里的题目，先告诉我答案，然后再给出简短的解析。如果没有题目，就只回复没有识别到题目”。可在后台截图设置区域直接修改，保存设置后生效；“恢复默认提示词”可随时还原。

悬浮回答背景可在设置页独立选择，点选颜色或切换毛玻璃后会立即保存，并从下一次悬浮回答开始生效。Android 12 及以上开启“半透明毛玻璃”时会使用系统背景模糊；Android 10 和 11 会自动降级为半透明背景。

设置页的“立即测试截图”可以在不切换应用的情况下检查截图、AI 请求和悬浮回答链路。Android 10 的屏幕捕获授权绑定当前应用进程；应用进程被系统回收后，需要回到设置重新授权。Android 11 及以上没有这一限制，但系统或厂商的省电设置仍可能暂停无障碍服务，如音量键没有响应，请确认系统设置中的“AI BOTOY”无障碍服务仍为开启状态。

选择/判断题简版回答模式可在后台截图设置区域单独开启。识别到选择题时，屏幕顶端从左到右对应 A、B、C、D；识别到判断题时，左侧表示正确、右侧表示错误。该模式只显示灰色方块约 1 秒，不显示文字悬浮回答。

应用由手机直接连接所填写的模型服务，Base URL 必须使用 HTTPS。API Key 只会加密保存在本机，但会在请求时发送给该服务，请仅使用可信接口。

### 管理聊天

- 点击聊天列表右上角的加号，输入名称并创建聊天。
- 点击聊天行右侧的菜单，或长按聊天行，可选择“重命名”或“删除”。
- 每个聊天的消息互相隔离。删除聊天会同时删除其中的消息和私有图片，且无法恢复。
- 删除最后一个聊天后，应用会自动创建一个新的默认聊天。

### 选择回答文字

在 AI 回复的文字上长按，然后拖动 Android 系统选区手柄即可选择所需内容，并使用系统菜单复制。代码块也提供独立的复制按钮。

## 在线更新

应用内置的更新清单地址为：

`https://github.com/GodBook/ai-chat-android/releases/latest/download/latest.json`

它指向 GitHub Releases 的最新正式版本。首次安装可从仓库的 Releases 页面下载 APK；安装后打开“设置”即可检查更新，也可以在设置页替换为自己的清单地址。

### 在应用内更新

1. 在“设置”的“在线更新”区域确认或修改 HTTPS 更新清单地址。点击“保存设置”可保留该地址。
2. 点击“检查更新”。发现新版本后，可查看版本号和发布说明。
3. 点击“下载更新”。应用会下载 APK，并校验 SHA-256、包名、版本号和签名。
4. 下载完成后点击“安装”，再按 Android 系统安装程序的提示确认更新。
5. 如果系统要求“允许安装未知应用”，请为“AI BOTOY”开启该权限并返回；应用会继续发起安装。

这是覆盖安装流程。更新成功后，Room 聊天记录、图片、DataStore 设置和 Keystore 中的 API Key 均会保留。

> [!IMPORTANT]
> 更新 APK 必须与已安装应用使用相同包名 `com.example.aichat` 和相同签名证书，其 `versionCode` 必须高于已安装版本。更新清单中的 `versionCode` 还必须与 APK 完全一致，否则应用会拒绝安装。

> [!WARNING]
> 不要先卸载旧版，也不要清除应用数据。本项目关闭了 Android 备份和设备迁移；卸载或清除数据会删除聊天记录、图片、设置和 API Key，之后无法由系统备份恢复。

### 发布更新

仓库已配置 `.github/workflows/发布.yml`。推送与 `versionName` 一致的标签（例如 `v1.4.4`）后，GitHub Actions 会自动运行测试和 Lint，签名 APK，计算 SHA-256，生成 `latest.json`，并创建公开 Release：

```powershell
git tag v1.4.4
git push origin v1.4.4
```

发布前先在仓库 Settings → Secrets and variables → Actions 中配置以下四个 Secret。签名文件只会在 Actions runner 的临时目录使用，不会进入 Git 历史：

- `AI_CHAT_SIGNING_KEYSTORE_B64`
- `AI_CHAT_SIGNING_KEYSTORE_PASSWORD`
- `AI_CHAT_SIGNING_KEY_ALIAS`
- `AI_CHAT_SIGNING_KEY_PASSWORD`

首个公开版本沿用当前交付 APK 的签名证书，以便已经安装的 `1.1` 版本可以覆盖更新。证书 SHA-256 指纹为 `B5:79:D6:4D:B9:B1:CA:5A:B8:40:84:82:3D:FC:AE:5C:62:5D:E4:BC:D6:DD:38:FE:4B:DC:FC:1D:F5:FA:37:0A`。请把该签名 Secret 当作生产凭据保管；后续版本必须一直使用同一签名证书，不能直接换钥。

每次发布都要递增 `versionCode`，并让 Git 标签与 `versionName` 完全一致。工作流会拒绝不一致的标签，且不会对 Pull Request 暴露签名 Secret。Release 资产中的 `latest.json` 由工作流生成，下载地址指向具体标签的 APK，避免清单和文件在更新过程中错配。

清单格式如下（仅供参考，实际 SHA-256 和下载地址由工作流填写）：

```json
{
  "versionCode": 11,
  "versionName": "1.4.4",
  "downloadUrl": "https://github.com/GodBook/ai-chat-android/releases/download/v1.4.4/ai-botoy-1.4.4.apk",
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "releaseNotes": "修复选择/判断题简版回答模式在无障碍截图流程中未生效的问题"
}
```

`versionCode` 可以是 JSON 数字或数字字符串。清单地址和 `downloadUrl` 在生产环境中都必须使用 HTTPS。可通过 Gradle 属性 `UPDATE_MANIFEST_URL` 覆盖内置地址；用户仍可在设置页覆盖并保存该地址。应用不会静默安装，下载完成后会交给 Android 系统安装程序，并可能要求开启“允许安装未知应用”。

旧版数据库会通过 Room 迁移保留消息，并归入默认聊天。更新配置单独保存在 `UpdateConfigStore` 中，不会覆盖模型配置或聊天数据。

## 构建

需要 JDK 17 和 Android SDK 36。项目的 `minSdk` 为 29，`targetSdk` 和 `compileSdk` 均为 36。

```powershell
.\gradlew.bat :app:assembleDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## 主要技术

- Kotlin、Jetpack Compose、Material 3
- Room、DataStore、Android Keystore
- OkHttp 与自定义 SSE 流解析
- Coil、CommonMark
