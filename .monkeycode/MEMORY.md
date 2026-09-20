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
  - 版本号在 app/build.gradle.kts versionCode/versionName 维护；当前 v1.4.0/15

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
