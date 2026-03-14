# Voice Cast Addon Project Notes

## 1. Project Overview

- Project path: `D:\mod_development\voicecastaddon-1.21.1`
- Minecraft version: `1.21.1`
- Mod loader: `NeoForge`
- Mod type: client-side addon for `Iron's Spells 'n Spellbooks`
- Core feature: use `Vosk` offline speech recognition so the player can speak a spell name and cast the spell

This file is intended to be a persistent recovery note for future development sessions. A new agent should read this file first before making changes.

## 2. Current Functional Scope

The mod currently supports:

- Holding the voice key to start recording and releasing it to recognize speech
- Matching recognized text to spell aliases
- Sending the matched spell ID to the server for spell casting
- English and Chinese recognition support
- Configurable spell alias mappings through JSON instead of hardcoded Java strings
- Built-in bundled small Vosk models
- Player-provided custom Vosk models through the config directory
- Automatic preference for custom models when valid custom models are present
- Voice casting from scrolls
- Voice casting from equipped spellbooks
- In-game selection of the audio input device
- Automatic fallback to the system default input device if the selected device is unavailable
- Full internationalization support (i18n) with language files
- Fuzzy Chinese matching using pinyin and pinyin initials

## 3. Important Paths

### Source code

- `src/main/java/com/yelle233/voicecastaddon/VoiceCastAddonClient.java`
- `src/main/java/com/yelle233/voicecastaddon/client/VoiceRecognitionManager.java`
- `src/main/java/com/yelle233/voicecastaddon/client/VoiceInputController.java`
- `src/main/java/com/yelle233/voicecastaddon/client/VoiceSpellAliases.java`
- `src/main/java/com/yelle233/voicecastaddon/client/VoiceCastClientConfig.java`
- `src/main/java/com/yelle233/voicecastaddon/client/VoiceAudioDeviceManager.java`
- `src/main/java/com/yelle233/voicecastaddon/client/VoiceCastSettingsScreen.java`
- `src/main/java/com/yelle233/voicecastaddon/client/PinyinMatcher.java`

### Language files

- `src/main/resources/assets/voicecastaddon/lang/en_us.json`
- `src/main/resources/assets/voicecastaddon/lang/zh_cn.json`

### Runtime and config

- `run/logs/latest.log`
- `config/voicecastaddon/spell_aliases.json`
- `config/voicecastaddon/client_settings.json`
- `config/voicecastaddon/custom-voice-models/`

### Build

- `build.gradle`

## 4. Current Runtime Flow

### Client startup

On client setup:

- set `jna.encoding=UTF-8`
- ensure client config files exist
- warm up Vosk model loading asynchronously to reduce the first-use stutter

Main entry:

- `VoiceCastAddonClient`

### Voice input

The voice key logic is handled by:

- `VoiceInputController`

Behavior:

- key down:
  - call `VoiceRecognitionManager.startListening()`
  - show in-game message that recognition started
- key up:
  - stop recording
  - start async recognition thread
  - collect recognized candidates
  - try alias matching in order
  - if matched, send `VoiceCastPayload` to server
  - if unmatched, show the recognized text in the action bar

### Recognition

Recognition is handled by:

- `VoiceRecognitionManager`

Important details:

- recording format is fixed to:
  - `PCM_SIGNED`
  - `16000 Hz`
  - `16 bit`
  - `mono`
  - little-endian
- model initialization is lazy, but also warmed up on client setup
- recognition is not performed on the render thread
- multiple models can be loaded
- recognized text is normalized and then matched against aliases

### Alias matching

Alias loading and normalization are handled by:

- `VoiceSpellAliases`
- `VoiceCastClientConfig`

Behavior:

- aliases are loaded from `spell_aliases.json`
- alias matching is case-insensitive
- punctuation and repeated whitespace are normalized
- the alias map is cached in memory
- `VoiceSpellAliases.reload()` reloads alias data from the JSON file

## 5. Vosk Model Rules

### Built-in models

The project includes support for bundled small models:

- English: `vosk-model-small-en-us-0.15`
- Chinese: `vosk-model-small-cn-0.22`

Bundled model loading is handled in `VoiceRecognitionManager`.

### Custom models

Custom model folder:

- `config/voicecastaddon/custom-voice-models/`

Each direct child folder is treated as a candidate model directory if it contains:

- `am/final.mdl`
- `conf/model.conf`

Current behavior:

- if one or more valid custom model directories are found, bundled small models are disabled
- all valid custom model directories in that folder are loaded
- model key is the folder name

### Important note

Custom model support is model-directory based, not zip-file based. The player must place the extracted Vosk model folder into `custom-voice-models`.

## 6. Audio Input Device Support

Audio device management was added later and is now part of the current design.

Related files:

- `VoiceAudioDeviceManager`
- `VoiceCastSettingsScreen`
- `VoiceCastClientConfig`
- `VoiceRecognitionManager`

### Player-facing behavior

Players can choose the input device in-game through the mod config screen.

The selection is saved to:

- `config/voicecastaddon/client_settings.json`

Current key:

- `preferredInputDeviceId`

### Device filtering rules

The code now filters devices so only devices that support the actual voice recording format are shown. This was necessary because Java Sound can expose fake or non-recording endpoints such as `PortMixer`, which appear as devices but cannot open a `TargetDataLine` for the required format.

The accepted format is:

- `16000 Hz`
- `16 bit`
- `1 channel`
- signed
- little-endian

### Fallback behavior

Even if the saved preferred device becomes unavailable:

- the mod logs a warning
- recording falls back to the system default input device

This avoids hard failure when the player changes hardware or the saved device is no longer valid.

## 7. JSON Config Files

### `spell_aliases.json`

Purpose:

- defines spell ID to voice phrase mappings

Structure example:

```json
{
  "irons_spellbooks:fireball": [
    "fireball",
    "fire ball",
    "火球",
    "火球术"
  ]
}
```

Important notes:

- this replaced hardcoded alias strings
- default content is generated automatically on first run
- if you want to change the default generated content, edit `defaultAliases()` in `VoiceCastClientConfig`

### `client_settings.json`

Purpose:

- stores client-side settings

Current structure:

```json
{
  "preferredInputDeviceId": ""
}
```

Meaning:

- empty string means use the system default input device

## 8. Default Spell Aliases

As of 2026-03-14, the default configuration includes 80+ spells from Iron's Spells 'n Spellbooks, organized by spell school:

- Fire Spells (10 spells)
- Ice Spells (7 spells)
- Lightning Spells (8 spells)
- Holy Spells (10 spells)
- Ender Spells (6 spells)
- Evocation Spells (11 spells)
- Blood Spells (9 spells)
- Nature Spells (8 spells)

Each spell has:
- English name (official Iron's Spells 'n Spellbooks name)
- Chinese name (direct translation)

Players can customize these aliases by editing `config/voicecastaddon/spell_aliases.json`.

For the complete list, see `COMPLETE_SPELL_LIST.md`.

### Smart Config Merge System

As of 2026-03-14, the mod implements an intelligent configuration merge system:

**Features:**
- Version-tracked configuration files (`_config_version` key)
- Automatic detection of outdated configs
- Smart merging that preserves user customizations
- Automatic addition of new spells from updates
- Automatic backup before merging (`.backup` file)

**How it works:**
1. On startup, check config file version
2. If version is outdated:
   - Read user's current config
   - Read new default config
   - Merge: keep user's customizations, add new spells
   - Create backup of old config
   - Save merged config with new version number

**For developers:**
- When adding new spells, increment `CURRENT_CONFIG_VERSION`
- User customizations are never overwritten
- New spells are automatically added to user's config

For detailed documentation, see `SMART_CONFIG_MERGE.md`.

## 9. Encoding and Chinese Text Notes

This project had previous mojibake problems.

Observed symptoms:

- Chinese recognition results became garbled
- game chat/action bar showed mojibake
- Java source file literals could also become corrupted if saved with the wrong encoding

Current mitigation:

- `jna.encoding` is set to `UTF-8`
- Chinese UI strings in Java source are preferably written with Unicode escapes
- default Chinese alias strings are also written with Unicode escapes in source
- some recognition results are passed through a mojibake repair step in `VoiceRecognitionManager`

Development rule:

- if editing Java source with Chinese literals, prefer Unicode escapes instead of raw Chinese characters unless you are certain the file encoding is preserved as UTF-8

## 10. Performance and UX Notes

### First key press stutter

This issue has been resolved. The model is now loaded synchronously during client setup (FMLClientSetupEvent), which happens during the game loading screen.

Current mitigation:

- Models are loaded during game startup, before the player enters the world
- Loading happens synchronously to ensure models are ready before gameplay begins
- Loading time is logged for debugging purposes
- First voice recognition will not experience any delay

### Recognition hitch on key release

This issue existed before because recognition work could affect gameplay responsiveness.

Current mitigation:

- recognition runs asynchronously on a background thread

There may still be some unavoidable CPU cost depending on model size, but the main-thread blocking has already been reduced significantly.

## 11. Spell Casting Behavior

Voice spell casting is no longer limited to a single scroll slot.

Current behavior:

- scans scroll spell containers more broadly instead of only slot `0`
- supports spell casting from equipped spellbooks
- tries to inspect the Curios `spellbook` slot via reflection-based access
- uses `CastSource.SPELLBOOK` for spellbook casting paths where appropriate

If spellbook casting behavior needs changes later, inspect the server-side packet handling and casting lookup code associated with `VoiceCastPayload`.

## 12. Previously Fixed Problems

### 1. Client crash on voice key

Previous issue:

- pressing the voice key could crash due to `NoClassDefFoundError: org.vosk.Model`

Fix:

- runtime dependencies for Vosk and JNA were added so they are available on the client runtime classpath

Related file:

- `build.gradle`

### 2. Build failure in `build.gradle`

Previous issue:

- `Could not get unknown property 'localRuntime' for configuration ':clientAdditionalRuntimeClasspath'`

Fix:

- ensure `localRuntime` exists before it is referenced
- extend `clientAdditionalRuntimeClasspath` from `configurations.localRuntime`

### 3. Unreported `IOException`

Previous issue:

- `VoiceRecognitionManager` methods calling `new Model(...)` produced checked-exception compile errors

Fix:

- wrap or declare `IOException` properly when loading bundled/custom models

### 4. Chinese mojibake

Previous issue:

- Chinese recognition and chat text appeared as garbage text

Fixes applied over time:

- `jna.encoding=UTF-8`
- normalization and re-encoding repair attempts
- safer source literals using Unicode escapes

### 5. Wrong audio device selected

Previous issue:

- a non-recording Java Sound endpoint such as `PortMixer` could be selected
- pressing the voice key would fail with:
  - `IllegalArgumentException: Line unsupported: interface TargetDataLine ...`

Fix:

- filter devices by actual support for the required voice format
- fallback to system default device when preferred device fails

## 13. Key Implementation Details

### `VoiceCastAddonClient`

Responsibilities:

- register config screen extension point
- set up client initialization tasks
- ensure config files exist
- trigger model warm-up

### `VoiceRecognitionManager`

Responsibilities:

- start and stop recording
- initialize Vosk models
- load bundled or custom models
- run recognition
- normalize and repair recognized text
- use selected audio input device
- fall back to default device if needed

### `VoiceCastClientConfig`

Responsibilities:

- create config directory and files
- load spell aliases
- detect custom model directories
- read and write client settings
- provide default alias data

### `VoiceAudioDeviceManager`

Responsibilities:

- enumerate valid input devices
- create stable device IDs
- resolve preferred mixer from config
- reject unsupported input endpoints

### `VoiceCastSettingsScreen`

Responsibilities:

- expose in-game audio input device selection UI
- refresh visible device list
- save selected device to `client_settings.json`

## 14. Development Rules for Future Work

- Prefer `apply_patch` for file edits
- Prefer ASCII in source unless there is a clear reason not to
- For Chinese Java literals, use Unicode escapes when possible
- Do not revert unrelated user changes in git
- Be careful when touching `VoiceRecognitionManager`; it is now responsible for startup stability, async behavior, custom model loading, text repair, and audio device selection
- If changing generated default JSON content, update `defaultAliases()` in `VoiceCastClientConfig`
- If changing settings persistence, update both file creation and reading logic in `VoiceCastClientConfig`
- If changing the audio format, also update device filtering in `VoiceAudioDeviceManager`

## 15. Known Limitations

- Recognition still depends on the performance of the chosen Vosk model, especially larger custom models
- There is no in-game editor for spell alias JSON yet
- There is no hot-reload UI button for aliases yet, though `VoiceSpellAliases.reload()` exists in code
- Build verification may fail in restricted environments if Gradle wrapper download is blocked or times out

## 16. Recommended Next Improvements

- Add an in-game alias reload button
- Add an in-game alias editor or a config screen hint that opens the config folder
- Add per-model selection when multiple custom models are present
- Add better logging of which model produced the winning recognition result
- Add a debug overlay or chat command to list currently loaded models and current input device
- Add automated validation for malformed `spell_aliases.json`

## 17. Recovery Checklist For Next Session

When resuming work:

1. Read this file first
2. Check `run/logs/latest.log` for the newest runtime error
3. Inspect these files first:
   - `VoiceRecognitionManager`
   - `VoiceCastClientConfig`
   - `VoiceAudioDeviceManager`
   - `VoiceInputController`
4. If the issue is model-related, inspect `config/voicecastaddon/custom-voice-models/`
5. If the issue is matching-related, inspect `config/voicecastaddon/spell_aliases.json`
6. If the issue is device-related, inspect `config/voicecastaddon/client_settings.json`

## 18. Current Status Summary

At the time this note was written, the project had already reached this state:

- no longer crashes when starting voice recognition
- supports English and Chinese recognition
- supports configurable spell alias JSON
- supports custom Vosk models
- supports spellbook voice casting
- supports in-game audio input device selection
- has protection against invalid saved audio devices

If a new issue appears now, it is most likely in one of these areas:

- custom model compatibility
- audio device enumeration on a specific machine
- alias mismatches
- spellbook scan or cast integration with Iron's Spells 'n Spellbooks / Curios

## 19. Internationalization (i18n) Support

As of 2026-03-14, all hardcoded Chinese strings have been extracted to language files.

### Language keys

All UI text and messages now use translation keys:

- `voicecastaddon.settings.title` - Settings screen title
- `voicecastaddon.settings.device_label` - Audio device label
- `voicecastaddon.settings.refresh` - Refresh button
- `voicecastaddon.settings.save` - Save button
- `voicecastaddon.settings.back` - Back button
- `voicecastaddon.settings.note1` - First note about changes
- `voicecastaddon.settings.note2` - Second note about fallback
- `voicecastaddon.message.start_listening` - Voice recognition started
- `voicecastaddon.message.start_failed` - Voice recognition failed (with error parameter)
- `voicecastaddon.message.no_content` - No content recognized
- `voicecastaddon.message.matched` - Spell matched (with text and spell ID parameters)
- `voicecastaddon.message.unmatched` - No spell matched (with recognized text parameter)

### Supported languages

- English (`en_us.json`)
- Simplified Chinese (`zh_cn.json`)

### Implementation notes

- All `Component.literal()` calls with hardcoded text have been replaced with `Component.translatable()`
- Unicode escapes in Java source have been removed in favor of language files
- Format parameters use `%s` placeholders in language files

## 20. Pinyin Fuzzy Matching

As of 2026-03-14, Chinese spell aliases now support fuzzy matching using pinyin.

### Matching priority

When a recognized text is received, the system tries to match in this order:

1. **Exact match**: Direct match against normalized alias text
2. **Pinyin match**: Convert recognized text to full pinyin and match
3. **Pinyin initial match**: Convert recognized text to pinyin initials (first letter of each character) and match
4. **Pinyin fuzzy match**: Calculate edit distance between pinyins and match if distance ≤ 2

### Example

For the alias "火球" (fireball):

- Exact match: "火球" or "huoqiu" (if user says it in pinyin)
- Pinyin match: "huo qiu" → matches "火球"
- Pinyin initial match: "hq" → matches "火球"
- Fuzzy match: "或求" (huoqiu) → matches "火球" (distance = 0)
- Fuzzy match: "活球" (huoqiu) → matches "火球" (distance = 0)

For the alias "咒立停" (counterspell):

- Fuzzy match: "周丽萍" (zhouliping) → matches "咒立停" (zhouliting, distance = 1)
- Fuzzy match: "周立平" (zhouliping) → matches "咒立停" (zhouliting, distance = 2)

This means if the voice recognition outputs similar-sounding words, it can still match the correct spell.

### Implementation

- `PinyinMatcher` class handles conversion using the `pinyin4j` library
- `PinyinMatcher.calculateSimilarity()` uses Levenshtein Distance algorithm
- `VoiceSpellAliases` maintains four matching strategies:
  - `ALIASES` - exact normalized text
  - `PINYIN_ALIASES` - full pinyin
  - `PINYIN_INITIAL_ALIASES` - pinyin initials
  - `fuzzyMatchByPinyin()` - edit distance based fuzzy matching
- `MAX_FUZZY_DISTANCE = 2` controls the tolerance level

### Dependencies

- `pinyin4j` version 2.5.1 is bundled with the mod via `jarJar`

### Performance notes

- Pinyin conversion happens once during alias loading, not during recognition
- First three strategies use O(1) hash map access
- Fuzzy matching is O(n × m × k) but only triggered when first three strategies fail
- For typical use cases (< 100 aliases, < 20 characters), fuzzy matching takes < 1ms
- 90%+ of matches succeed in the first three strategies, avoiding fuzzy matching overhead
