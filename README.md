# DismissTag

DismissTag 是一个用于管理应用独立配置的 Android LSPosed 模块。配置按应用保存，可分别调整语言与地区、时区、位置预设和部分隐私选项。

## 安装与使用

1. 从 [GitHub Releases](https://github.com/givsk983/Dismiss-Tag/releases/latest) 下载并安装带版本号的 APK。APK 使用调试签名，覆盖安装需要沿用原签名。
2. 在 LSPosed 管理器中启用模块，并将需要配置的应用加入作用域。
3. 打开 DismissTag（坐忘道），从已安装应用中按名称搜索并选择，查看它是否已在 LSPosed 中勾选，再启用并保存配置。
4. 强行停止并重新打开目标应用，让它读取新配置。

在应用配置页也可以通过「一键应用地区预设」同时设置运营商、语言与地区、时区和位置，并保存当前配置。列表包含美国、日本、台湾、香港以及新加坡等东南亚城市，保存后可以逐项调整。

首页「已配置」列出保存过的应用，包括配置已关闭的应用。应用列表在本机读取，LSPosed 勾选状态在返回配置应用时刷新。框架不可用时，已记下的配置条目仍可查看，勾选状态显示为未知。

每个应用可以从城市列表选取自己的参考坐标，也可以手动调整。启用虚拟定位后，模块替换该应用收到的 Android 定位和受支持的 Google Play 服务融合定位结果，同时屏蔽 Android 原始卫星数据回调。自有定位引擎、IP 推断及应用服务端记录的地区取决于应用本身。

## 运行与构建

- Android 8.0 或更高版本，LSPosed Modern API 101+，并启用远程配置支持。
- 手动检查更新时，应用会连接 GitHub。
- 从源码构建需要 JDK 17 或以上、Android SDK 35 和 Android NDK 27.0.12077973。Windows 下请从纯 ASCII 路径运行 `gradlew.bat :app:assembleDebug`，输出文件位于 `app/build/outputs/apk/debug/app-debug.apk`。

[libxposed Modern API](https://github.com/libxposed/api) · [远程配置服务](https://github.com/libxposed/service)

## SIM 详细身份

开启「伪装 SIM 与运营商」后，可展开详细身份，分别填写运营商名称（SPN）、MCC/MNC、两位 ISO 国家码、IMSI、ICCID、手机号（MSISDN）、GID1 与 GID2。运营商字段留空沿用地区预设，身份字段留空保持隐藏；IMSI 前缀必须匹配运营商代码。相关 TelephonyManager、SubscriptionInfo、部分 UICC 接口及运营商系统属性使用同一份配置。双卡使用相同的伪装值，保留实际卡槽、连接与权限状态；不改变实体 SIM，也不绕过系统的号码读取权限。eSIM EID 继续隐藏。

界面采用坐忘道意象：墨绿底色、旧纸卡面、朱砂强调、红中麻将牌与法帖分节，支持浅色和深色模式。

SIM 配置回归检查可运行 `powershell -ExecutionPolicy Bypass -File tests/run-sim-checks.ps1`，使用本机 JDK 和 Android SDK，无需连接手机。

## 使用范围

请仅在有权管理的设备和应用中使用，并遵守相关服务规则。配置的适用范围取决于 Android 接口、系统权限和框架版本；不改变实体 SIM 或网络侧信息，不保证第三方服务的兼容性或账号状态。

