# 配置文件迁移指南

## 概述

当你更新代码并修改配置文件结构时，配置迁移系统会自动：
1. 保留玩家已输入的内容
2. 添加新的配置项
3. 删除废弃的配置项
4. 创建备份文件

## 如何更新配置版本

### 1. 修改配置结构

在 `VoiceCastClientConfig.java` 中修改 `defaultSettings()` 方法：

```java
private static JsonObject defaultSettings() {
    JsonObject root = new JsonObject();
    root.addProperty(SETTINGS_VERSION_KEY, CURRENT_SETTINGS_VERSION);
    root.addProperty(INPUT_DEVICE_KEY, "");
    // 添加新字段
    root.addProperty("newField", "defaultValue");
    // 删除旧字段（不再添加）
    // root.addProperty("oldField", "");
    return root;
}
```

### 2. 增加版本号

修改 `CURRENT_SETTINGS_VERSION` 常量：

```java
private static final int CURRENT_SETTINGS_VERSION = 3; // 从 2 增加到 3
```

### 3. 添加配置访问方法（如果需要）

```java
public static String getNewField() {
    return getSettingString("newField", "defaultValue");
}
```

### 4. 迁移逻辑

`mergeSettingsFileIfNeeded()` 方法会自动：
- 检测配置文件版本
- 如果版本低于当前版本，执行迁移
- 保留所有在 `defaultSettings()` 中定义的字段的用户值
- 删除不在 `defaultSettings()` 中的废弃字段
- 创建 `client_settings.json.backup` 备份文件

## 迁移示例

### 旧配置（版本 1）
```json
{
  "preferredInputDeviceId": "用户的麦克风",
  "recognitionMode": "hybrid",
  "onlineProvider": "baidu",
  "baiduApiKey": "user_key",
  "baiduSecretKey": "user_secret",
  "tencentSecretId": "",
  "tencentSecretKey": "",
  "longSentenceThreshold": 15
}
```

### 新配置（版本 2）
```json
{
  "_settings_version": 2,
  "preferredInputDeviceId": "用户的麦克风",
  "recognitionMode": "hybrid",
  "tencentSecretId": "",
  "tencentSecretKey": "",
  "longSentenceThreshold": 15
}
```

**迁移结果：**
- ✅ 保留：`preferredInputDeviceId`, `recognitionMode`, `longSentenceThreshold`（用户的值）
- ✅ 保留：`tencentSecretId`, `tencentSecretKey`（用户的值）
- ❌ 删除：`onlineProvider`, `baiduApiKey`, `baiduSecretKey`（废弃字段）
- ✅ 添加：`_settings_version: 2`（版本标记）
- ✅ 创建：`client_settings.json.backup`（备份文件）

## 注意事项

1. **版本号必须递增**：每次修改配置结构时，必须增加 `CURRENT_SETTINGS_VERSION`
2. **向后兼容**：迁移逻辑会自动处理旧版本配置，无需手动编写迁移代码
3. **备份文件**：每次迁移都会创建备份，玩家可以恢复旧配置
4. **日志记录**：迁移过程会记录详细日志，方便调试

## 测试迁移

在开发环境中测试迁移：

1. 创建旧版本配置文件
2. 启动游戏
3. 检查日志中的迁移信息
4. 验证配置文件内容是否正确
5. 确认备份文件已创建

## 日志示例

```
[VoiceCastAddon] Migrating settings from version 1 to 2
[VoiceCastAddon] Preserved setting: preferredInputDeviceId = "用户的麦克风"
[VoiceCastAddon] Preserved setting: recognitionMode = "hybrid"
[VoiceCastAddon] Removed obsolete setting: onlineProvider
[VoiceCastAddon] Removed obsolete setting: baiduApiKey
[VoiceCastAddon] Removed obsolete setting: baiduSecretKey
[VoiceCastAddon] Created settings backup at: config/voicecastaddon/client_settings.json.backup
[VoiceCastAddon] Settings migrated successfully to version 2
```
