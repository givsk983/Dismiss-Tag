# DismissTag（应用隐私配置）

一个按包名配置的 Android LSPosed 模块。APK 最低支持 Android 8.0，运行需要支持 Modern API 101 或以上以及远程配置的 LSPosed。手动检查更新时会连接 GitHub。

## 安装与使用

1. 安装 [DismissTag.apk](https://github.com/givsk983/Dismiss-Tag/releases/download/v0.0.1/DismissTag.apk)，应用版本为 0.0.1。这是用于试用的调试签名 APK。
2. 打开 LSPosed 管理器，启用「应用隐私配置」，在作用域勾选要配置的应用。如果管理器提示重启，请按提示操作。
3. 打开「应用隐私配置」，确认首页显示框架已连接。选择 TikTok、Claude 或输入其他应用的完整包名。勾选「启用此应用的配置」，设置需要隐藏或替换的项目，再保存。
4. 保存后点「应用信息」，**强行停止目标应用**，然后重新打开。每个进程在启动时读取一次配置。关闭配置并保存后，也需要同样重启目标应用。

例如，勾选「隐藏真实 SIM 与运营商」，选择「美国 · T-Mobile」，再从列表选择语言与地区 `en-US` 和时区 `America/New_York`。也可以让语言与时区跟随系统。运营商预设会替换应用读取的运营商名称、MCC/MNC 和国家码，保留蜂窝连接状态和移动数据通路。应用服务端仍可按出口 IP 或账号地区判断可用性。

从 v0.3.0 升级时，已保存的美国、日本和英国国家码会对应到新版运营商预设，原先留空或其他国家码会使用美国 T-Mobile。

从 v0.2.0 升级时，配置存储已从旧式共享文件迁移到 LSPosed 远程配置。直接安装新版后，如旧配置未导入，可在新版重新填写并保存。

## 覆盖范围

| 配置 | 处理的读取路径 |
| --- | --- |
| SIM 和运营商 | 常见 `TelephonyManager`、`SubscriptionInfo`、`SubscriptionManager`、`ServiceState` 与 UICC/eSIM getter。隐藏手机号及 IMSI/ICCID/EID 等字段。按预设替换运营商名称、MCC/MNC 和 SIM/网络国家码。部分 Java 系统属性也会同步处理。 |
| 语言与地区 | Java/ICU 的默认语言、`LocaleList`、Android 13 起的 `LocaleManager.getSystemLocales()`，以及应用读取的资源配置。地区由语言标签决定，如 `en-US` 里的 `US`。 |
| 时区 | `TimeZone.getDefault()`、`ZoneId.systemDefault()`、ICU 默认时区与 Android Java 时区属性。 |
| 隔离设备标识 | 为该应用的 `Settings.Secure.ANDROID_ID` 分配稳定的随机值。屏蔽常见 Wi-Fi 名称和地址、本机蓝牙名称和地址，以及 Google 广告 ID SDK 的 Java getter。 |
| 隐藏系统使用记录 | 屏蔽 `UsageStatsManager` 的应用使用时长、配置变更和使用事件查询。 |

作用范围是目标进程中的上述 Android/Java 读取路径。设备型号、安装 ID、原生设备指纹、IP、GPS、账号和应用自身观察到的点击、停留时长等行为由各自的来源决定。应用界面语言仍可能取决于资源和应用内设置。

在 LSPosed 日志中搜索 `AppPrivacyProfile`。出现「已加载配置」表示目标进程读取到了启用的配置。若出现「无法挂钩」，对应接口可能仍返回设备原值。远程配置存储开关、替换值和生成的独立标识。

## 从源码构建

准备 JDK 17 或以上和 Android SDK 35。Windows 下运行 `gradlew.bat :app:assembleDebug`，生成文件位于 `app/build/outputs/apk/debug/app-debug.apk`。首次构建需要下载 Gradle、Android Gradle Plugin 和 libxposed 依赖。安装升级时需要沿用原签名。

参考文档 [libxposed Modern API](https://github.com/libxposed/api)、[libxposed 远程配置服务](https://github.com/libxposed/service)、[Android 使用统计](https://developer.android.com/reference/android/app/usage/UsageStatsManager)。
