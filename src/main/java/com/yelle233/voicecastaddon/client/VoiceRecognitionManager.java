package com.yelle233.voicecastaddon.client;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public class VoiceRecognitionManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float SAMPLE_RATE = 16000.0f;
    private static final Object MODEL_LOCK = new Object();

    private static TargetDataLine line;
    private static ByteArrayOutputStream audioBuffer;
    private static Thread captureThread;
    private static volatile boolean listening = false;
    private static volatile String lastError = "";
    private static volatile boolean warmUpStarted = false;

    private static Model model;

    public static boolean startListening() {
        if (listening) {
            return true;
        }

        try {
            initModelIfNeeded();

            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            line = (TargetDataLine) AudioSystem.getLine(info);
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

    public static String stopListeningAndRecognize() {
        if (!listening) {
            return "";
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
            return "";
        }

        try (Recognizer recognizer = new Recognizer(model, SAMPLE_RATE)) {
            recognizer.acceptWaveForm(audioBytes, audioBytes.length);
            String json = recognizer.getFinalResult();
            LOGGER.info("[VoiceCastAddon] Vosk result json = {}", json);
            return extractText(json);
        } catch (Throwable t) {
            lastError = describeThrowable(t);
            LOGGER.error("[VoiceCastAddon] Failed to recognize recorded audio: {}", lastError, t);
            return "";
        }
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
                initModelIfNeeded();
            } catch (Throwable t) {
                lastError = describeThrowable(t);
                LOGGER.error("[VoiceCastAddon] Failed to warm up voice recognition: {}", lastError, t);
            }
        }, "voicecastaddon-vosk-warmup");
        warmUpThread.setDaemon(true);
        warmUpThread.start();
    }

    private static void initModelIfNeeded() {
        if (model != null) {
            return;
        }

        synchronized (MODEL_LOCK) {
            if (model != null) {
                return;
            }

            try {
                File modelDir = tryLoadFromExternalPath();
                if (modelDir == null) {
                    modelDir = extractModelFromJar();
                }

                if (modelDir == null) {
                    throw new IllegalStateException("Could not find a Vosk model directory");
                }

                model = new Model(modelDir.getAbsolutePath());
                LOGGER.info("[VoiceCastAddon] Vosk model loaded from {}", modelDir.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException("Failed to load Vosk model files", e);
            }
        }
    }

    private static File tryLoadFromExternalPath() {
        String[] possiblePaths = {
                "client/voice-models/vosk-model-small-en-us-0.15",
                "voice-models/vosk-model-small-en-us-0.15",
                "run/client/voice-models/vosk-model-small-en-us-0.15"
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

    private static File extractModelFromJar() {
        try {
            Path tempDir = Files.createTempDirectory("voicecastaddon-vosk-model");
            File modelDir = tempDir.toFile();
            modelDir.deleteOnExit();

            String resourcePath = "/assets/voicecastaddon/voice-models/vosk-model-small-en-us-0.15";
            copyResourceDirectory(resourcePath, modelDir.toPath());

            LOGGER.info("[VoiceCastAddon] Extracted model from jar to {}", modelDir.getAbsolutePath());
            return modelDir;
        } catch (IOException e) {
            LOGGER.error("[VoiceCastAddon] Failed to extract model from jar", e);
            return null;
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
        String marker = "\"text\"";
        int markerIndex = json.indexOf(marker);
        if (markerIndex < 0) {
            return "";
        }

        int colon = json.indexOf(':', markerIndex);
        if (colon < 0) {
            return "";
        }

        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return "";
        }

        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return "";
        }

        return json.substring(firstQuote + 1, secondQuote).trim();
    }

    private VoiceRecognitionManager() {
    }
}
