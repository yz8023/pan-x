# User Instruction Memory

This file records user instructions, preferences, and teachings for reference in future interactions.

## Format

### User Instruction Entry
User instruction entries should follow this format:

[User Instruction Summary]
- Date: [YYYY-MM-DD]
- Context: [Mentioned scenario or time]
- Instructions:
  - [Content of user teaching or instruction, described line by line]

### Project Knowledge Entry
Entries discovered by the Agent during task execution should follow this format:

[Project Knowledge Summary]
- Date: [YYYY-MM-DD]
- Context: Discovered by Agent while performing [specific task description]
- Category: [Operations & Deployment|Build Methods|Testing Methods|Troubleshooting & Debugging|Workflow & Collaboration|Environment Configuration]
- Instructions:
  - [Specific knowledge points, described line by line]

## Deduplication Strategy
- Before adding a new entry, check for similar or identical instructions.
- If a duplicate is found, skip the new entry or merge it with the existing one.
- When merging, update the context or date information.
- This helps avoid redundant entries and keeps the memory file tidy.

## Entries

[Project Knowledge Summary]
- Date: 2026-09-19
- Context: Discovered by Agent while implementing alipan (阿里云盘) share-link parsing feature and releasing v1.3.0
- Category: Build Methods
- Instructions:
  - Build command: `cd /workspace && ./gradlew assembleDebug testDebugUnitTest -Dorg.gradle.jvmargs=-Xmx1024m --max-workers=2`；必须用后台终端执行（memory 受限，8GB 系统/cgroup 3.89GiB）
  - 编译失败修复后重跑即可（增量编译约 1-2 分钟）

[Project Knowledge Summary]
- Date: 2026-09-19
- Context: Discovered by Agent while implementing alipan (阿里云盘) share-link parsing feature and releasing v1.3.0
- Category: Operations & Deployment
- Instructions:
  - 发布目标仓库为 `yz8023/pan-x`（fork 自 CYQawa/YunX）；远程 origin 已指向该仓库，分支 main，无 submodules
  - Release 流程：git add + commit → push main → git tag vX.Y.Z → push tag → `export GH_TOKEN=$(echo -e "protocol=https\nhost=github.com\n" | git credential fill | awk -F= '$1=="password"{print $2}')` → `gh release create vX.Y.Z <apk> --repo yz8023/pan-x`
  - 发布 APK 用 debug 包（唯一签名配置），命名 `YunX-v<版本>-debug.apk`
  - 更新检测 UpdateChecker.kt 的 RELEASES_LATEST_URL 必须指向本仓库（yz8023/pan-x）而非上游
  - 版本号在 app/build.gradle.kts versionCode/versionName 维护；当前 v1.4.5/20

[Project Knowledge Summary]
- Date: 2026-09-20
- Context: Discovered by Agent while implementing 115/蓝奏云/PikPak 分享解析 feature
- Category: Build Methods
- Instructions:
  - Kotlin 语言版本 2.1：inline lambda 内禁止 `break`/`continue`（需 while 标志位），`MediaType.parse` 已废弃（用 `"..".toMediaType()`），`withContext` 内非局部 `return` 被禁止（改表达式/局部返回）
  - `if (credential.isNullOrBlank() && !anonymous) return@launch` 守卫会破坏对 `credential` 的智能转换（anonymous 为 true 时不返回），之后传 String 参数处需 `.orEmpty()`
  - PikPak 请求库函数需为 suspend 才能在 error_code=9 时刷新 captcha token 重试
  - 三个新平台全部编译通过、testDebugUnitTest 全绿；接入点：ShareLinkParser 正则、DownloadPlatform、SettingsScreen.threadPlatforms、ResolveViewModel（canSave 保持 false）、DriveScreen 卡片、MainScreen DI

[Project Knowledge Summary]
- Date: 2026-09-19
- Context: Discovered by Agent while implementing alipan feature
- Category: Troubleshooting & Debugging
- Instructions:
  - 新增 SharePlatform 枚举值后，所有 `when (platform)` 必须补分支（编译期强制 exhaustive）：ResolveScreen.platformLabel、ResolveViewModel.currentCredential/currentRepo/currentDefaultDirFid/platformName/enqueueDownload、ResolveHistoryRepository.platformLabel
  - 新增平台需接线点：DownloadPlatform 常量、SettingsScreen.threadPlatforms、AuthBackupManager 备份/导入、MainScreen DI（VM Factory + resolve repo + AccountSheet）、DriveScreen 卡片
  - Room 新增表必须写 Migration（版本+1），不能依赖 fallbackToDestructiveMigration（会丢凭证）
  - 各平台 AccountSheet 的 InfoRow 是文件私有函数，新写 Sheet 需自带

[Project Knowledge Summary]
- Date: 2026-09-20
- Context: Discovered by Agent while fixing alipan 登录 + 百度 wap/init 链接无法解析
- Category: Troubleshooting & Debugging
- Instructions:
  - 阿里云盘登录页必须用 `https://www.alipan.com/sign/in`；官网首页 `https://www.alipan.com/` 是营销落地页，无登录表单
  - 阿里云盘官方密码登录接口 `POST /v2/account/token/login_by_password` 已下线（auth.alipan.com 与 auth.aliyundrive.com 均 404），第三方无法做原生账密登录；只能走官方登录页（/sign/in）提取 localStorage `token` JSON 的 refresh_token
  - 阿里云盘 /sign/in 登录页 localStorage 键名仍为 `token`（rememberLogin 开启时写入，默认开）
  - 百度网盘移动端分享短链格式 `pan.baidu.com/wap/init?surl=XXX&pwd=XXX`：surl 参数即接口所需 share_id，不能像 /s/1xxx 那样去掉前导 1（/s/1xxx 去掉 1，wap/init 不去）
  - 解析页「登录已失效/请先登录」错误通过 ResolveUiState.Error.loginPlatform 携带平台，ResolveScreen 展示「重新登录」按钮，MainScreen 的 onReLogin 路由到对应登录页

[Project Knowledge Summary]
- Date: 2026-09-20
- Context: Discovered by Agent while fixing ForegroundServiceDidNotStartInTimeException crash
- Category: Troubleshooting & Debugging
- Instructions:
  - Android 12+ 前台服务竞态：startForegroundService 后若任务极快完成/失败，stopService 会先于 onStartCommand 的 startForeground 执行，系统在超时窗口（Android 12+ 5s，Android 16 6s）内未收到 startForeground 即抛 ForegroundServiceDidNotStartInTimeException
  - 修复模式：在 Service.onCreate() 中立即调用 startForeground（占位通知），保证任何命令/停止处理前服务已处于前台；onStartCommand 再更新通知内容
  - 若异常发生在有 <service android:foregroundServiceType="dataSync"> + FOREGROUND_SERVICE_DATA_SYNC 权限的情况下，多为启动时序竞态而非配置缺失
  - ★ 仅有 onCreate 占位 startForeground 仍不足：DownloadService 生命周期由 Dispatchers.Default 后台线程的 onTaskStarted/onTaskFinished 驱动，任务极快结束时间歇性复现。根治：companion stop() 严禁直接 stopService，改发 ACTION_STOP 意图走 startForegroundService，由服务内 onStartCommand 在 startForeground 之后 stopSelf() 安全停止；同时通知构建包 try/catch 回退最简通知，保证 startForeground 永不因构建异常被跳过
  - DownloadService 用 @Volatile stopPending 防重入：多个任务并发结束时只发一次 ACTION_STOP，避免服务反复重建

[Project Knowledge Summary]
- Date: 2026-09-20
- Context: Discovered by Agent while fixing 百度转存获取 bdstoken 失败
- Category: Troubleshooting & Debugging
- Instructions:
  - gettemplatevariable 是 BaiduApi 中唯一必须补 Referer 的请求：百度 WAF 对无 Referer 的 API 请求即使携带有效 BDUSS 也返回 errno=-6（会话无效）；需带 `Referer: https://pan.baidu.com/disk/main` + `channel=chunlei` 参数
  - 百度错误码：errno=0 成功；errno=-6 会话无效/未登录；errno=2 鉴权/参数缺失（BDCLND 缺失时 transfer 报此码）
  - BaiduApi 是 MainScreen `remember { BaiduApi() }` 单例，跨账号复用：bdstoken/缓存必须按 Cookie 中 BDUSS 维度隔离，否则切换账号串 token
  - 登录校验不能只看 Cookie 是否含 BDUSS，要以 gettemplatevariable 取昵称成功为准，否则「登录成功但转存时取不到 bdstoken」

[Project Knowledge Summary]
- Date: 2026-09-20
- Context: Discovered by Agent while fixing 下载保存缺存储权限报错 + 升级后百度重登（v1.4.5）
- Category: Troubleshooting & Debugging
- Instructions:
  - 存储权限弹窗只能在 Activity 前台时可靠展示：权限申请要放在「下载入队时」（用户点下载必在前台），不能放在「下载完成保存前」（前台服务后台场景弹窗会失败/挂死）
  - Android 10（Q）MediaStore 保存失败会回退传统路径，仍需 WRITE 权限（仅 Android 11+ 免权限）；storagePermissionProvider 的免权限判定要用 `SDK_INT >= R` 而非 `>= Q`
  - storagePermissionProvider 是单一 CompletableDeferred 槽位，多任务并发保存互相覆盖会挂死，需用 Mutex 串行化
  - 百度「升级后每次都要重登」排查结论：无版本号驱动的清数据逻辑；debug.keystore 稳定 + Room 迁移非破坏性，就地覆盖安装不丢登录；唯一自清路径是 SecureAccountDaos.decryptOrClear 解密失败即 clear() 删行 → 修复为解密失败保留密文行返回 null（Keystore 暂不可用不永久丢账号）
