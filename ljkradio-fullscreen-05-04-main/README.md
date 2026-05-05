***

AIGC:
ContentProducer: Minimax Agent AI
ContentPropagator: Minimax Agent AI
Label: AIGC
ProduceID: "00000000000000000000000000000000"
PropagateID: "00000000000000000000000000000000"
ReservedCode1: 3046022100adaae23e8380057f676079b0e17bb31ea5d3600912f5318538d943be9d48a1c20221008d5dbb3039fa4cf127108c060b4423b9bcbecf7e956ee10f0b665425301429ac
ReservedCode2: 3045022054cf744e035d08603b8f78829564ffa5ac83a21c313beafdf2edabddb6fad482022100c5eb49799822d23d5828581e64610276fb9ae017c75663433bfdedb46a054c65
-------------------------------------------------------------------------------------------------------------------------------------------------------------

# IjkPlayer 网络电台播放器
双引擎版，在android5机器上正常播放,有遥控器支持，无歌词显示

一个基于 B站开源 IjkPlayer 和EXOplayer实现的 Android 网络电台应用，支持电台列表管理、流媒体播放、音量调节和播放状态保存等功能。

## 功能特性

- **电台管理**：添加、删除电台列表
- **流媒体播放**：支持 HTTP/HTTPS 协议的音频流播放
- **音量控制**：实时音量调节
- **播放状态**：显示播放、缓冲、暂停、错误等状态
- **网络监听**：自动检测网络连接状态
- **状态恢复**：自动恢复上次播放的电台
- **硬解码支持**：启用硬件加速解码（可配置）
- **缓冲优化**：针对弱网环境的缓冲策略

## 项目结构

```
ijk-radio-player/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/ijkradio/
│   │   │   ├── MainActivity.kt              # 主界面
│   │   │   ├── data/
│   │   │   │   ├── Station.kt               # 电台数据类
│   │   │   │   └── StationStorage.kt        # SharedPreferences 存储
│   │   │   ├── player/
│   │   │   │   └── IjkPlayerManager.kt      # IjkMediaPlayer 封装单例
│   │   │   ├── ui/
│   │   │   │   ├── StationAdapter.kt        # RecyclerView 适配器
│   │   │   │   └── PlaybackState.kt         # 播放状态密封类
│   │   │   └── utils/
│   │   │       └── NetworkHelper.kt         # 网络状态检测
│   │   ├── res/
│   │   │   ├── layout/                      # 布局文件
│   │   │   ├── drawable/                    # 图标和背景
│   │   │   └── values/                      # 字符串、颜色、主题
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
├── gradle.properties
└── README.md
```

## 开发环境

- Android Studio Hedgehog 或更高版本
- Gradle 8.0+
- minSdk 21（Android 5.0）
- targetSdk 33

## 核心依赖

- IjkPlayer Java + armv7a 库 (0.8.8)
- AndroidX RecyclerView (1.3.2)
- Material Design (1.11.0)
- Gson (2.10.1)
- Kotlin 协程 (1.7.3)
- AndroidX Lifecycle (2.7.0)

## 快速开始

### 1. 导入项目

使用 Android Studio 打开项目目录，或通过 File → New → Import Project 选择 `ijk-radio-player` 文件夹。

### 2. 同步 Gradle

等待 Android Studio 自动下载依赖，或手动点击 "Sync Now"。

### 3. 构建运行

1. 连接 Android 设备或启动模拟器
2. 点击 Run → Run 'app' 或使用快捷键 Shift + F10

### 4. 添加电台

- 点击右下角的 "+" 按钮添加新电台
- 输入电台名称和流媒体URL
- 可选添加描述信息

## 默认电台列表

应用内置了以下默认电台：test

## 架构说明

### IjkPlayerManager 单例

核心播放器管理类，负责：

- 播放器初始化和配置
- 播放控制（播放、暂停、停止）
- 音量调节
- 状态监听和回调
- 资源释放

### 播放状态管理

使用 Kotlin 密封类 `PlaybackState` 表示播放器的各种状态：

- `Stopped` - 停止
- `Buffering` - 缓冲中
- `Playing` - 正在播放
- `Paused` - 暂停
- `Error` - 错误

### 数据持久化

使用 SharedPreferences 存储：

- 电台列表（JSON格式）
- 上次播放的电台ID
- 音量设置
- 播放位置

## 配置说明

### 硬解码配置

在 `IjkPlayerManager.kt` 中可以修改硬解码设置：

```kotlin
hardwareDecodeEnabled = true  // 启用硬解码
setHardwareDecode(false)       // 禁用硬解码，使用软解
```

### 缓冲策略

修改缓冲区大小（单位：字节）：

```kotlin
setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 1024)
```

## 注意事项

1. **网络权限**：应用需要 INTERNET 和 ACCESS\_NETWORK\_STATE 权限
2. **明文流量**：由于部分电台使用 HTTP 协议，需要设置 `android:usesCleartextTraffic="true"`
3. **架构兼容**：如需支持其他架构，添加对应 IjkPlayer 库（arm64、x86）

## 常见问题

**Q: 播放失败或无声音？**
A: 开启日志调试，设置 `IjkMediaPlayer.setLogLevel(IjkMediaPlayer.IJK_LOG_DEBUG)`，观察 Logcat 输出。

**Q: 硬解码不兼容？**
A: 在 IjkPlayerManager 中将 mediacodec 选项设为 0，回退到软解。

**Q: 缓冲很慢？**
A: 调整 `max-buffer-size` 或启用无限缓冲（不推荐移动网络）。

## 扩展建议

- 后台播放：迁移到 ForegroundService
- 网络监听：断网时提示，恢复后重连
- 通知栏控制：添加 MediaStyle 通知
- 均衡器：结合 AudioFX

## 许可证

本项目仅供学习参考使用。
