# 字语输入法

字语输入法是一个原生 Android 中文输入法，重点探索“部件读音拼字”和候选读音反馈。项目使用 Java、Android `InputMethodService` 与 BSD-3-Clause 许可的 librime 1.16.1，最低支持 Android 6.0（API 23）。

## 已实现功能

- 26 键拼音输入、整句候选、简拼和用户词频学习；librime 未就绪时自动使用 6.5 万余条 Java 离线候选
- 常见声母、模糊音及前后鼻音匹配
- 拼字输入：1.1 万余种部件组合接入 Rime 表格引擎，并保留 `jiuri → 旮（gā，九+日）`、`wangyu/wangyv → 珏（jué，王+玉）`
- 中文数字墨水手写识别；只有生僻字显示拼音，常用字候选保持简洁
- 拼音、拼字、手写、英文、数字和符号模式即时切换
- 候选栏右侧的「›」可翻页查找低频字，如 `fu → 祓`、`si → 巳`
- 顶部紧凑候选栏、轻量模式栏以及主键盘逗号/句号快捷键
- 五行常用符号面板，可直接输入中英文标点、括号和网络符号
- 纸白、青玉、夜色皮肤，以及面板色/按键色自定义
- 使用 Android 文件选择器导入或导出 JSON 皮肤包
- 振动反馈和按键音开关
- 本地词频、连续表达学习和自定义词条
- 密码类输入框自动停止学习
- 用户词库、设置和输入习惯不进行云备份

## 构建环境

- JDK 17
- Android SDK 36
- Android NDK 28.0.13004108（仅重新编译 librime 时需要）
- Android Gradle Plugin 9.3.0
- Gradle 9.5.0（Wrapper 已包含）

首次构建：

```powershell
.\gradlew.bat assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。
默认构建不包含个人图片皮肤；PR 的 CI 只上传这个 APK。

由于仓库目录名含中文，部分 Windows 版本的 Gradle 测试工作进程可能无法解析测试类路径。可临时映射 ASCII 盘符：

```powershell
subst R: "C:\Users\23170\Desktop\输入法"
Set-Location R:\
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
subst R: /D
```

## 安装与启用

1. 安装调试 APK，打开“字语输入法”。
2. 点击“启用输入法”，在 Android 设置中允许字语输入法。
3. 返回应用并点击“选择当前输入法”。
4. 打开任意文本框开始输入。
5. 第一次切换到手写模式时，需要联网下载约 20MB 的简体中文识别模型。

## 数据与隐私

- 拼音、拼字和基础词库查询均离线完成。
- ML Kit 语言模型仅在首次使用手写时下载，书写轨迹在设备端识别。
- 候选选择次数、连续表达和自定义词条保存在应用私有存储中。
- 密码、可见密码、网页密码和数字密码输入框不会写入学习数据。
- 应用禁止明文网络流量，并关闭系统云备份及设备迁移数据导出。
- 设置页可暂停个性化学习或清空全部本地学习数据。

## 词库扩展

项目同时加载人工校正词条和大规模离线词库。Rime 配方与词典位于 `app/src/main/assets/rime`；Java 后备拼音数据位于 `pinyin_dictionary.tsv` 与 `pinyin_rime.tsv`，拼字数据位于 `assembly_dictionary.tsv` 与 `assembly_full.tsv`。生成命令及上游许可见 `tools/generate_dictionaries.ps1` 和 `THIRD_PARTY_NOTICES.md`。

## 开源输入引擎

主解码器采用 librime 1.16.1，通过项目自有 JNI 薄封装接入，未复制 Trime 的 GPL 界面代码。首次启动在后台部署 Rime 数据，部署过程中仍可使用轻量 Java 后备引擎，因此不会阻塞输入法唤醒。四种 ABI 的预编译库位于 `app/src/main/jniLibs`。如需从上游源码重建，可准备 librime、Boost、Android NDK 与 CMake 后执行：

```powershell
.\tools\build_librime_android.ps1 -Abi arm64-v8a
```

## 皮肤包格式

设置页可导入或导出 `.json` 皮肤包。示例文件位于 `skins/青瓷.json`：

```json
{
  "format": "ziyu-ime-skin",
  "version": 1,
  "name": "青瓷",
  "panelColor": "#C7D9D4",
  "keyColor": "#F7FBFA",
  "textColor": "#18332E",
  "accentColor": "#0F766E",
  "cornerRadius": 10
}
```

颜色使用 `#RRGGBB` 或 `#AARRGGBB`，圆角范围为 0–24。

## 验证

JVM 单元测试覆盖全拼、简拼、模糊音、大规模离线词库、拼字组合以及手写候选拼音。最终检查命令：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```
