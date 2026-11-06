# DismissTag

DismissTag 是一个用于管理应用独立配置的 Android LSPosed 模块。配置按应用保存，可分别调整语言与地区、时区、位置预设和部分隐私选项。

## 安装与使用

1. 从 [GitHub Releases](https://github.com/givsk983/Dismiss-Tag/releases/latest) 下载并安装带版本号的 APK，例如 `DismissTag-v0.0.2.apk`。APK 使用调试签名，覆盖安装需要沿用原签名。
2. 在 LSPosed 管理器中启用模块，并将需要配置的应用加入作用域。
3. 打开 DismissTag（坐忘道），确认框架已连接。选择应用或输入包名，启用并保存配置。
4. 强行停止并重新打开目标应用，让它读取新配置。

需要设置位置时，可以从城市列表选取参考坐标，也可以手动调整。位置配置在应用收到 Android 定位结果时生效。各项设置的适用范围取决于应用实际使用的 Android 接口。

## 运行与构建

- Android 8.0 或更高版本，LSPosed Modern API 101+，并启用远程配置支持。
- 手动检查更新时，应用会连接 GitHub。
- 从源码构建需要 JDK 17 或以上以及 Android SDK 35。Windows 下运行 `gradlew.bat :app:assembleDebug`，输出文件位于 `app/build/outputs/apk/debug/app-debug.apk`。

[libxposed Modern API](https://github.com/libxposed/api) · [远程配置服务](https://github.com/libxposed/service)
