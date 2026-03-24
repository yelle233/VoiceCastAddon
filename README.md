# Voice Cast Addon | 语音施法附属

[English](#english) | [中文](#中文)

---

## English

### Overview

Voice Cast Addon is a Minecraft mod that adds voice-controlled spellcasting to **Iron's Spells 'n Spellbooks**. Instead of clicking buttons, simply speak to cast your spells!

### Requirements

- **Minecraft**: 1.21.1
- **Mod Loader**: NeoForge 21.1.219+
- **Dependencies**: Iron's Spells 'n Spellbooks


### How to Use

#### Step 1: Configure Audio Device

1. Press `ESC` → **Mod Options** → **Voice Cast Addon**, or use the keybinding to open the settings screen
2. Select your microphone from the device list
3. Click **Save**

#### Step 2: Record Voice Templates

1. Press `ESC` → **Mod Options** → **Voice Cast Addon**, or use the keybinding to open the settings screen
2. In the settings screen, click **Record Voice Templates**
3. Select a spell from the list
4. Click **Click to Record** button
5. Record the audio that will trigger this spell
6. Click **Stop Recording** to save
7. Recommend recording each trigger word 10 times (higher counts have diminishing returns, so 10 is usually sufficient) to improve accuracy and prevent false triggers
   > For example, for Fireball, if you record the word "Fireball" 10 times, and then want to add "Flame Burst" as another trigger word, you should also record that 10 times, making a total of 20 audio samples for Fireball
8. Use **Test Match** to verify your recordings work

#### Step 3: Cast Spells with Voice

1. Equip a spellbook, scroll, or weapon containing the spell
2. Click the voice cast key
3. Speak the spell name
4. Release the key
5. The spell will cast automatically

### Keybindings

- **Hold to Voice Cast**: Configurable in Controls settings

### Server Configuration

Server administrators can configure game balance settings in `config/voicecastaddon/server_settings.json` (these settings only affect voice-triggered casting, not the original keybind casting from Iron's Spells):

```json
{
  "skipCastTime": false,        // Skip spell cast time
  "ignoreCooldown": false,      // Ignore spell cooldown
  "ignoreManaRequirement": false // Ignore mana cost
}
```

### Client Configuration

Players can customize their voice recognition settings in `config/voicecastaddon/client_settings.json`:

```json
{
  "_settings_version": 2,           // Config version (do not modify)
  "preferredInputDeviceId": "",     // Microphone device ID (set via in-game GUI)
  "matchThreshold": 50.0            // Match threshold: lower = stricter, higher = more lenient
}
```

**Match Threshold Explanation:**
- **Lower values (e.g., 30.0)**: More strict matching, reduces false triggers but may miss some correct pronunciations
- **Default (50.0)**: Balanced setting for most users
- **Higher values (e.g., 70.0)**: More lenient matching, easier to trigger but may increase false positives
- Adjust based on your recording quality and environment noise

### Recognition Principle & Technical Details

#### How Voice Recognition Works

This mod uses **template-based voice recognition** instead of traditional speech-to-text:

1. **Recording Phase**: When you record a voice sample, the mod extracts audio features using MFCC (Mel-Frequency Cepstral Coefficients)
2. **Storage**: Each recording is converted into a feature matrix and stored as a template
3. **Matching Phase**: When you speak, the mod:
   - Extracts features from your live audio
   - Compares it against all stored templates using DTW (Dynamic Time Warping)
   - Calculates a "distance" score for each spell (lower = more similar)
   - Selects the spell with the lowest distance if it's below the threshold


#### Technical Specifications

- **Audio Processing**: MFCC (Mel-Frequency Cepstral Coefficients) feature extraction
- **Matching Algorithm**: DTW (Dynamic Time Warping) for time-series comparison
- **Audio Format**: 16kHz, 16-bit, mono PCM
- **Response Time**: 50-150ms (pure Java implementation, no external dependencies)
- **Match Threshold**: 50.0 (configurable in `client_settings.json`)
- **Recommended Samples**: 10 per trigger word (diminishing returns beyond this)

---

## 中文

### 概述

语音施法附属是一个为 **Iron's Spells 'n Spellbooks** 添加语音控制施法功能的模组,说话释放法术。

### 运行要求

- **Minecraft**: 1.21.1
- **模组加载器**: NeoForge 21.1.219+
- **前置模组**: Iron's Spells 'n Spellbooks


### 使用教程

#### 第一步：配置音频设备

1. 按 `ESC` → **模组选项** → **语音施法附属**，或通过绑定打开配置界面的按键来打开配置界面
2. 从设备列表中选择你的麦克风
3. 点击**保存**

#### 第二步：录制语音模板

1. 按 `ESC` → **模组选项** → **语音施法附属（VoiceCastAddon）**，或通过绑定打开配置界面的按键来打开配置界面
2. 在设置界面中，点击**录制语音模板**
3. 从列表中选择一个法术
4. 点击**点击录音**按钮
5. 录制能触发此法术的音频
6. 点击**停止录音**保存
7. 建议一个触发词录制10遍（更高的次数因为边缘递减效应，效果不会变得更好，所以一般来说10次足够了），以提高准确率与防止误触发
>比如火球术，我录制了"火球"这个词10遍，然后我又想录制"火焰熊熊"作为另一个触发词，则这个触发词也需要录制10遍,这样火球术总的音频样板数量为20个
8. 使用**测试匹配**验证录音是否有效

#### 第三步：使用语音施法

1. 装备包含该法术的法术书、卷轴或武器
2. 按住语音施法键
3. 说出法术名称
4. 松开按键
5. 法术将自动释放

### 按键绑定

- **按住以语音施法**：可在控制设置中修改语音施法的按键

### 服务端配置

服务器管理员可以在 `config/voicecastaddon/server_settings.json` 中配置游戏平衡设置(此配置只影响语音触发，对铁魔法原有的按键施法法术没有影响)：

```json
{
  "skipCastTime": false,        // 跳过法术读条
  "ignoreCooldown": false,      // 无视法术冷却
  "ignoreManaRequirement": false // 无视法力值消耗
}
```

### 客户端配置

玩家可以在 `config/voicecastaddon/client_settings.json` 中自定义语音识别设置：

```json
{
  "_settings_version": 2,           // 配置版本（请勿修改）
  "preferredInputDeviceId": "",     // 麦克风设备ID（通过游戏内GUI设置）
  "matchThreshold": 50.0            // 匹配阈值：数值越低越严格，越高越宽松
}
```

**匹配阈值说明：**
- **较低数值（如 30.0）**：更严格的匹配，减少误触发但可能漏掉一些正确发音
- **默认值（50.0）**：适合大多数用户的平衡设置
- **较高数值（如 70.0）**：更宽松的匹配，更容易触发但可能增加误触发

### 识别原理与技术说明

#### 语音识别工作原理

本模组使用**基于模板的语音识别**，而非传统的语音转文字：

1. **录制阶段**：当你录制语音样本时，模组使用 MFCC（梅尔频率倒谱系数）提取音频特征
2. **存储阶段**：每个录音被转换为特征矩阵并存储为模板
3. **匹配阶段**：当你说话时，模组会：
   - 从实时音频中提取特征
   - 使用 DTW（动态时间规整）算法与所有存储的模板进行比较
   - 为每个法术计算"距离"分数（越低 = 越相似）
   - 如果最低距离低于阈值，则选择该法术

#### 技术规格

- **音频处理**：MFCC（梅尔频率倒谱系数）特征提取
- **匹配算法**：DTW（动态时间规整）用于时间序列比较
- **音频格式**：16kHz, 16位, 单声道 PCM
- **响应时间**：50-150ms（纯 Java 实现，无外部依赖）
- **匹配阈值**：50.0（可在 `client_settings.json` 中配置）
- **推荐样本数**：每个触发词10个（超过此数量收益递减）


