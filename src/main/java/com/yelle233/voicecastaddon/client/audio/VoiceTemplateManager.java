package com.yelle233.voicecastaddon.client.audio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Manages voice templates for spells.
 * Each template is a recorded audio sample converted to MFCC features.
 */
public class VoiceTemplateManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TEMPLATES_DIR = "voice-templates";

    private static final Map<ResourceLocation, List<double[][]>> templates = new HashMap<>();
    private static final MfccExtractor mfccExtractor = new MfccExtractor();

    /**
     * Save a voice template for a spell.
     */
    public static void saveTemplate(ResourceLocation spellId, byte[] audioBytes) throws IOException {
        double[][] features = mfccExtractor.extractFeatures(audioBytes);

        if (features.length == 0) {
            throw new IOException("Failed to extract features from audio");
        }

        Path templateFile = getTemplateFile(spellId);
        Files.createDirectories(templateFile.getParent());

        List<double[][]> existing = loadTemplatesFromFile(templateFile);
        existing.add(features);

        JsonObject root = new JsonObject();
        root.addProperty("spellId", spellId.toString());
        root.addProperty("sampleCount", existing.size());
        root.add("samples", GSON.toJsonTree(existing));

        Files.writeString(templateFile, GSON.toJson(root), StandardCharsets.UTF_8);

        templates.put(spellId, existing);
        LOGGER.info("[VoiceCastAddon] Saved voice template for {} (total samples: {})", spellId, existing.size());
    }

    /**
     * Delete all templates for a spell.
     */
    public static void deleteTemplates(ResourceLocation spellId) throws IOException {
        Path templateFile = getTemplateFile(spellId);
        Files.deleteIfExists(templateFile);
        templates.remove(spellId);
        LOGGER.info("[VoiceCastAddon] Deleted voice templates for {}", spellId);
    }

    /**
     * Load all templates from disk.
     */
    public static void loadAllTemplates() {
        templates.clear();

        Path templatesDir = getTemplatesDir();
        if (!Files.exists(templatesDir)) {
            return;
        }

        try (Stream<Path> files = Files.list(templatesDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                 .forEach(file -> {
                     try {
                         String json = Files.readString(file, StandardCharsets.UTF_8);
                         JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                         String spellIdStr = root.get("spellId").getAsString();
                         ResourceLocation spellId = ResourceLocation.parse(spellIdStr);

                         List<double[][]> samples = loadTemplatesFromFile(file);
                         if (!samples.isEmpty()) {
                             templates.put(spellId, samples);
                         }
                     } catch (Exception e) {
                         LOGGER.error("[VoiceCastAddon] Failed to load template file: {}", file, e);
                     }
                 });
        } catch (IOException e) {
            LOGGER.error("[VoiceCastAddon] Failed to scan templates directory", e);
        }

        LOGGER.info("[VoiceCastAddon] Loaded {} voice templates", templates.size());
    }

    /**
     * Match audio against all templates.
     * @return Spell ID with best match, or null if no good match
     */
    public static ResourceLocation matchAudio(byte[] audioBytes, double threshold) {
        double[][] features = mfccExtractor.extractFeatures(audioBytes);

        if (features.length == 0) {
            return null;
        }

        ResourceLocation bestMatch = null;
        double bestDistance = Double.MAX_VALUE;

        for (Map.Entry<ResourceLocation, List<double[][]>> entry : templates.entrySet()) {
            for (double[][] template : entry.getValue()) {
                double distance = DtwMatcher.calculateDistance(features, template);

                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestMatch = entry.getKey();
                }
            }
        }

        if (bestDistance <= threshold) {
            LOGGER.info("[VoiceCastAddon] Matched spell {} with distance {}", bestMatch, bestDistance);
            return bestMatch;
        }

        LOGGER.info("[VoiceCastAddon] No match found (best distance: {})", bestDistance);
        return null;
    }

    /**
     * Get all spells that have templates.
     */
    public static Set<ResourceLocation> getTemplatedSpells() {
        return new HashSet<>(templates.keySet());
    }

    /**
     * Get number of samples for a spell.
     */
    public static int getSampleCount(ResourceLocation spellId) {
        List<double[][]> samples = templates.get(spellId);
        return samples != null ? samples.size() : 0;
    }

    /**
     * Check if a spell has templates.
     */
    public static boolean hasTemplates(ResourceLocation spellId) {
        return templates.containsKey(spellId) && !templates.get(spellId).isEmpty();
    }

    private static List<double[][]> loadTemplatesFromFile(Path file) {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (!root.has("samples")) {
                return new ArrayList<>();
            }

            double[][][] samples = GSON.fromJson(root.get("samples"), double[][][].class);
            return new ArrayList<>(Arrays.asList(samples));
        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to load templates from {}", file, e);
            return new ArrayList<>();
        }
    }

    private static Path getTemplatesDir() {
        return FMLPaths.CONFIGDIR.get().resolve("voicecastaddon").resolve(TEMPLATES_DIR);
    }

    private static Path getTemplateFile(ResourceLocation spellId) {
        String filename = spellId.getNamespace() + "_" + spellId.getPath() + ".json";
        return getTemplatesDir().resolve(filename);
    }

    private VoiceTemplateManager() {
    }
}
