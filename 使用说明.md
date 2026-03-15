# 语音施法附属 - 指南

## 简介

为 Iron's Spells 'n Spellbooks 模组添加语音识别功能，通过说话来释放法术。

## 前置要求

- Minecraft 1.21.1 + NeoForge
- Iron's Spells 'n Spellbooks 模组

## 安装步骤

### 1. 安装模组

### 2. 生成法术配置

首次进入游戏后，运行命令生成法术配置：

```
/voicecast generate
```

这会自动扫描所有已安装的法术并生成配置文件。

### 3. 选择识别方法

具体可看下方"识别方法选择"。

### 4. 绑定按键

在游戏设置中找到"语音施法附属"分类，绑定"按住以语音施法"按键。


## 自定义触发词

编辑配置文件：`config/voicecastaddon/spell_aliases.json`

```json
{
  "irons_spellbooks:fireball": [
    "fireball",
    "火球",
    "火球术"  // 可以添加多个别名
  ]
}
```
> 十分重要！

修改后运行 `/voicecast reload` 热重载配置。


## 常用命令

- `/voicecast list` - 查看游戏中所有的法术
- `/voicecast generate` - 自动生成/更新法术配置
- `/voicecast reload` - 重新加载配置文件

## 麦克风设备设置

游戏内按 `ESC` → `模组` → `VoiceCastAddon` → `配置` 可以选择麦克风设备。

## 常见问题

**Q: 提示"未拥有此法术"？**
A: 两种可能，第一是你真的没有这个法术，第二就是有两个法术触发词重复了，识别后释放的是你没有的那个法术，请检查配置文件中是否有重复的触发词，确保唯一。

**Q: 如何跳过法术读条？**
A: 在配置文件中设置 `"skipCastTime": true`，语音施法将立即释放法术。这样说话时间可以弥补读条时间，适合快速战斗。

**Q: 如何无视法术冷却？**
A: 在配置文件中设置 `"ignoreCooldown": true`，语音施法将无视冷却时间。注意这仅影响语音施法，正常施法仍受冷却限制。

## 识别方法选择

### 双模式识别：离线 + 在线

模组支持三种识别模式和四种在线提供商：

#### 1. 离线模式（默认）
- 使用内置 VOSK 模型
- 完全离线，无需网络
- 隐私安全，响应快速
- 适合简短触发词

#### 2. 在线模式
- 支持多个语音识别服务提供商
- 需要网络连接和 API 密钥
- 识别准确度高，支持长句子、诗句、网络梗词
- 适合复杂触发词

**支持的提供商：**
- **腾讯云**
- **百度AI**
- **阿里云**
- **讯飞**

#### 3. 混合模式（推荐）
- 优先使用在线识别，失败时自动回退到离线模式
- 兼顾准确度和可用性

### 配置在线识别

编辑配置文件：`config/voicecastaddon/client_settings.json`

#### 使用腾讯云
```json
{
  "recognitionMode": "hybrid",
  "onlineProvider": "tencent",
  "tencentSecretId": "你的SecretId",
  "tencentSecretKey": "你的SecretKey"
}
```

#### 使用百度AI
```json
{
  "recognitionMode": "hybrid",
  "onlineProvider": "baidu",
  "baiduApiKey": "你的API_Key",
  "baiduSecretKey": "你的Secret_Key"
}
```

#### 使用阿里云
```json
{
  "recognitionMode": "hybrid",
  "onlineProvider": "aliyun",
  "aliyunAccessKeyId": "你的AccessKeyId",
  "aliyunAccessKeySecret": "你的AccessKeySecret",
  "aliyunAppKey": "你的AppKey"
}
```

#### 使用讯飞
```json
{
  "recognitionMode": "hybrid",
  "onlineProvider": "xfyun",
  "xfyunAppId": "你的APPID",
  "xfyunApiKey": "你的APIKey",
  "xfyunApiSecret": "你的APISecret"
}
```

**配置说明：**
- `recognitionMode`: 识别模式
  - `"offline"` - 离线模式（默认）
  - `"online"` - 在线模式
  - `"hybrid"` - 混合模式（推荐）
- `onlineProvider`: 在线提供商（`tencent`、`baidu`、`aliyun`、`xfyun`）
- `skipCastTime`: 跳过法术读条（`true` 或 `false`，默认 `false`）
  - 开启后，语音施法将立即释放法术，无需读条
  - 说话时间弥补读条时间
- `ignoreCooldown`: 无视法术冷却（`true` 或 `false`，默认 `false`）
  - 开启后，语音施法将无视冷却时间限制
  - 冷却期间也可以释放法术
  - 注意：仅影响语音施法，正常施法仍受冷却限制
- `longSentenceThreshold`: 长句子阈值（混合模式下，超过此字数使用在线识别）

### 获取 API 密钥

#### 腾讯云
1. 访问腾讯云语音识别
2. 注册并登录账号
3. 进入控制台 → 访问管理 → API密钥管理
4. 创建密钥，获取 SecretId 和 SecretKey

#### 百度AI（推荐）
1. 访问百度AI开放平台
2. 注册并登录账号
3. 创建语音识别应用
4. 获取 API Key 和 Secret Key

#### 阿里云
1. 访问阿里云控制台
2. 创建AccessKey
3. 开通智能语音交互服务
4. 创建项目并获取AppKey

#### 讯飞
1. 访问讯飞开放平台
2. 注册并登录账号
3. 创建语音听写应用
4. 获取 APPID、APIKey、APISecret


**结语**：
- 模组已内置语音识别模型，开箱即用
- 配置文件支持热重载，修改后运行 `/voicecast reload` 即可生效
- 首次使用建议先运行 `/voicecast generate` 生成完整的法术配置
- 若对离线语音识别模型不满意再考虑更换AI大模型语音识别
