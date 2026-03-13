package com.yelle233.voicecastaddon.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

public class VoiceRecognitionManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float SAMPLE_RATE = 16000.0f;
    private static final Object MODEL_LOCK = new Object();
    private static final String ENGLISH_MODEL_DIR = "vosk-model-small-en-us-0.15";
    private static final String CHINESE_MODEL_DIR = "vosk-model-small-cn-0.22";

    private static TargetDataLine line;
    private static ByteArrayOutputStream audioBuffer;
    private static Thread captureThread;
    private static volatile boolean listening = false;
    private static volatile String lastError = "";
    private static volatile boolean warmUpStarted = false;

    private static final Map<String, Model> MODELS = new LinkedHashMap<>();

    public static boolean startListening() {
        if (listening) {
            return true;
        }

        try {
            initModelsIfNeeded();

            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            Mixer preferredMixer = VoiceAudioDeviceManager.getPreferredMixer();
            line = preferredMixer != null
                    ? (TargetDataLine) preferredMixer.getLine(info)
                    : (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            audioBuffer = new ByteArrayOutputStream();
            listening = true;

            captureThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (listening) {
                    int count = line.read(buffer, 0, buffer.length);
                    if (count > 0) {
                        audioBuffer.write(buffer, 0, count);
                    }
                }
            }, "voicecastaddon-capture");

            captureThread.setDaemon(true);
            captureThread.start();
            lastError = "";
            LOGGER.info("[VoiceCastAddon] Voice capture started");
            return true;
        } catch (Throwable t) {
            listening = false;
            closeLine();
            lastError = describeThrowable(t);
            LOGGER.error("[VoiceCastAddon] Failed to start voice recognition: {}", lastError, t);
            return false;
        }
    }

    public static List<String> stopListeningAndRecognizeCandidates() {
        if (!listening) {
            return List.of();
        }

        listening = false;
        closeLine();

        if (captureThread != null) {
            try {
                captureThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] audioBytes = audioBuffer != null ? audioBuffer.toByteArray() : new byte[0];
        if (audioBytes.length == 0) {
            return List.of();
        }

        List<ModelResult> results = MODELS.entrySet().stream()
                .map(entry -> CompletableFuture.supplyAsync(() ->
                        new ModelResult(entry.getKey(), recognizeWithModel(entry.getKey(), entry.getValue(), audioBytes))))
                .toList()
                .stream()
                .map(future -> {
                    try {
                        return future.join();
                    } catch (CompletionException e) {
                        LOGGER.error("[VoiceCastAddon] Model recognition task failed", e);
                        return null;
                    }
                })
                .filter(result -> result != null && result.text != null && !result.text.isBlank())
                .sorted(Comparator.comparingInt(ModelResult::priority))
                .toList();

        List<String> candidates = new ArrayList<>();
        for (ModelResult result : results) {
            if (!candidates.contains(result.text)) {
                candidates.add(result.text);
            }
        }
        return candidates;
    }

    public static boolean isListening() {
        return listening;
    }

    public static String getLastError() {
        return lastError;
    }

    public static void warmUpAsync() {
        if (warmUpStarted) {
            return;
        }

        synchronized (MODEL_LOCK) {
            if (warmUpStarted) {
                return;
            }
            warmUpStarted = true;
        }

        Thread warmUpThread = new Thread(() -> {
            try {
                initModelsIfNeeded();
            } catch (Throwable t) {
                lastError = describeThrowable(t);
                LOGGER.error("[VoiceCastAddon] Failed to warm up voice recognition: {}", lastError, t);
            }
        }, "voicecastaddon-vosk-warmup");
        warmUpThread.setDaemon(true);
        warmUpThread.start();
    }

    private static void initModelsIfNeeded() {
        if (!MODELS.isEmpty()) {
            return;
        }

        synchronized (MODEL_LOCK) {
            if (!MODELS.isEmpty()) {
                return;
            }

            List<Path> customModelDirs = VoiceCastClientConfig.findCustomModelDirectories();
            if (!customModelDirs.isEmpty()) {
                for (Path customModelDir : customModelDirs) {
                    try {
                        loadModel(customModelDir.getFileName().toString(), customModelDir.toFile());
                    } catch (IOException e) {
                        LOGGER.error("[VoiceCastAddon] Failed to load custom model {}", customModelDir, e);
                    }
                }
                LOGGER.info("[VoiceCastAddon] Using {} custom Vosk model(s); bundled small models are disabled", customModelDirs.size());
            } else {
                loadBundledModel("en_us", ENGLISH_MODEL_DIR, true);
                loadBundledModel("zh_cn", CHINESE_MODEL_DIR, false);
            }

            if (MODELS.isEmpty()) {
                throw new IllegalStateException("Could not find any Vosk model directory");
            }
        }
    }

    private static void loadBundledModel(String key, String modelDirectoryName, boolean allowJarFallback) {
        try {
            File modelDir = tryLoadModelFromExternalPath(modelDirectoryName);
            if (modelDir == null && allowJarFallback) {
                modelDir = extractModelFromJar(modelDirectoryName);
            }

            if (modelDir == null) {
                LOGGER.info("[VoiceCastAddon] Model {} not found, skipping", modelDirectoryName);
                return;
            }

            loadModel(key, modelDir);
        } catch (Throwable t) {
            LOGGER.error("[VoiceCastAddon] Failed to load model {}: {}", modelDirectoryName, describeThrowable(t), t);
        }
    }

    private static void loadModel(String key, File modelDir) throws IOException {
        MODELS.put(key, new Model(modelDir.getAbsolutePath()));
        LOGGER.info("[VoiceCastAddon] Vosk model {} loaded from {}", key, modelDir.getAbsolutePath());
    }

    private static File tryLoadModelFromExternalPath(String modelDirectoryName) {
        String[] possiblePaths = {
                "client/voice-models/" + modelDirectoryName,
                "voice-models/" + modelDirectoryName,
                "run/client/voice-models/" + modelDirectoryName
        };

        for (String path : possiblePaths) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                LOGGER.info("[VoiceCastAddon] Loading model from external path {}", dir.getAbsolutePath());
                return dir;
            }
        }
        return null;
    }

    private static File extractModelFromJar(String modelDirectoryName) {
        try {
            Path tempDir = Files.createTempDirectory("voicecastaddon-vosk-model");
            File modelDir = tempDir.toFile();
            modelDir.deleteOnExit();

            String resourcePath = "/assets/voicecastaddon/voice-models/" + modelDirectoryName;
            copyResourceDirectory(resourcePath, modelDir.toPath());

            LOGGER.info("[VoiceCastAddon] Extracted model from jar to {}", modelDir.getAbsolutePath());
            return modelDir;
        } catch (IOException e) {
            LOGGER.error("[VoiceCastAddon] Failed to extract model {} from jar", modelDirectoryName, e);
            return null;
        }
    }

    private static String recognizeWithModel(String modelKey, Model model, byte[] audioBytes) {
        try (Recognizer recognizer = new Recognizer(model, SAMPLE_RATE)) {
            recognizer.acceptWaveForm(audioBytes, audioBytes.length);
            String json = recognizer.getFinalResult();
            String text = repairMojibake(extractText(json));
            LOGGER.info("[VoiceCastAddon] Vosk result ({}) = {}", modelKey, json);
            return text;
        } catch (Throwable t) {
            lastError = describeThrowable(t);
            LOGGER.error("[VoiceCastAddon] Failed to recognize recorded audio with {}: {}", modelKey, lastError, t);
            return "";
        }
    }

    private static void copyResourceDirectory(String resourcePath, Path targetDir) throws IOException {
        URL resourceUrl = VoiceRecognitionManager.class.getResource(resourcePath);
        if (resourceUrl == null) {
            throw new IOException("Missing resource: " + resourcePath);
        }

        try {
            if (resourceUrl.toString().startsWith("jar:")) {
                copyFromJar(resourcePath, targetDir);
            } else {
                Path sourcePath = Paths.get(resourceUrl.toURI());
                copyDirectory(sourcePath, targetDir);
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid model resource path", e);
        }
    }

    private static void copyFromJar(String resourcePath, Path targetDir) throws IOException {
        String jarPath = VoiceRecognitionManager.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();

        try (FileSystem fs = FileSystems.newFileSystem(Paths.get(jarPath), (ClassLoader) null)) {
            Path source = fs.getPath(resourcePath);
            copyDirectory(source, targetDir);
        } catch (Exception e) {
            copyResourceFilesManually(resourcePath, targetDir);
        }
    }

    private static void copyResourceFilesManually(String resourcePath, Path targetDir) throws IOException {
        String[] files = {
                "README",
                "am/final.mdl",
                "conf/mfcc.conf",
                "conf/model.conf",
                "graph/disambig_tid.int",
                "graph/HCLr.fst",
                "graph/Gr.fst",
                "graph/phones/word_boundary.int",
                "ivector/final.dubm",
                "ivector/final.ie",
                "ivector/final.mat",
                "ivector/global_cmvn.stats",
                "ivector/online_cmvn.conf",
                "ivector/splice.conf"
        };

        for (String file : files) {
            String fullPath = resourcePath + "/" + file;
            try (InputStream is = VoiceRecognitionManager.class.getResourceAsStream(fullPath)) {
                if (is != null) {
                    Path targetFile = targetDir.resolve(file);
                    Files.createDirectories(targetFile.getParent());
                    Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private static void closeLine() {
        if (line != null) {
            try {
                line.stop();
            } catch (Exception ignored) {
            }
            try {
                line.close();
            } catch (Exception ignored) {
            }
            line = null;
        }
    }

    private static String describeThrowable(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String rootMessage = root.getMessage();
        if (rootMessage == null || rootMessage.isBlank()) {
            rootMessage = t.getMessage();
        }
        if (rootMessage == null || rootMessage.isBlank()) {
            rootMessage = "no detail";
        }

        return t.getClass().getSimpleName() + ": " + rootMessage;
    }

    private static String extractText(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }

        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            if (!object.has("text")) {
                return "";
            }
            return VoiceSpellAliases.normalize(object.get("text").getAsString());
        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to parse recognition json: {}", json, e);
            return "";
        }
    }

    private static String repairMojibake(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = VoiceSpellAliases.normalize(text);
        if (VoiceSpellAliases.match(normalized) != null) {
            return normalized;
        }

        String repaired = tryReencode(normalized, Charset.forName("GBK"), StandardCharsets.UTF_8);
        if (!repaired.equals(normalized) && VoiceSpellAliases.match(repaired) != null) {
            LOGGER.info("[VoiceCastAddon] Repaired mojibake text from '{}' to '{}'", normalized, repaired);
            return repaired;
        }

        repaired = tryReencode(normalized, StandardCharsets.ISO_8859_1, StandardCharsets.UTF_8);
        if (!repaired.equals(normalized) && VoiceSpellAliases.match(repaired) != null) {
            LOGGER.info("[VoiceCastAddon] Repaired mojibake text from '{}' to '{}'", normalized, repaired);
            return repaired;
        }

        return normalized;
    }

    private static String tryReencode(String text, Charset wrongCharset, Charset targetCharset) {
        try {
            return VoiceSpellAliases.normalize(new String(text.getBytes(wrongCharset), targetCharset));
        } catch (Exception ignored) {
            return text;
        }
    }

    private record ModelResult(String modelKey, String text) {
        private int priority() {
            return "zh_cn".equals(modelKey) ? 0 : 1;
        }
    }

    private VoiceRecognitionManager() {
    }
}
