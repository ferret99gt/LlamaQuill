package com.llamaquill.imports;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.ImageRepository;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.model.StoryImage;
import com.llamaquill.util.Ids;
import com.llamaquill.util.Timestamps;
import javafx.scene.image.Image;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class AIDungeonImports
{
    private static final int MAX_JSON_DOCUMENT_BYTES = 64 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;
    private static final long MAX_TOTAL_IMAGE_BYTES = 256L * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 16_384;
    private static final long MAX_IMAGE_PIXELS = 100_000_000L;
    private static final String UNTITLED_CARD = "Untitled";
    private static final Pattern ACTION_CHUNK_PATTERN =
            Pattern.compile("(?i)^(?:.*[\\\\/])?actions-(\\d+)\\.json$");
    private static final HttpClient IMAGE_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final Database database;
    private final StoryRepository storyRepository;
    private final BlockRepository blockRepository;
    private final StoryCardRepository cardRepository;
    private final ImageRepository imageRepository;
    private final String defaultSystemPrompt;
    private final AdventureImageFetcher imageFetcher;

    public AIDungeonImports(Database database, StoryRepository storyRepository, BlockRepository blockRepository,
            StoryCardRepository cardRepository, ImageRepository imageRepository, String defaultSystemPrompt)
    {
        this(database, storyRepository, blockRepository, cardRepository, imageRepository, defaultSystemPrompt,
                AIDungeonImports::downloadAdventureImage);
    }

    AIDungeonImports(Database database, StoryRepository storyRepository, BlockRepository blockRepository,
            StoryCardRepository cardRepository, ImageRepository imageRepository, String defaultSystemPrompt,
            AdventureImageFetcher imageFetcher)
    {
        this.database = database;
        this.storyRepository = storyRepository;
        this.blockRepository = blockRepository;
        this.cardRepository = cardRepository;
        this.imageRepository = imageRepository;
        this.defaultSystemPrompt = defaultSystemPrompt;
        this.imageFetcher = imageFetcher;
    }

    public int importStoryCards(Path path, String storyId, boolean replaceExisting) throws Exception
    {
        String raw;
        try (InputStream stream = Files.newInputStream(path))
        {
            raw = readUtf8(stream, MAX_JSON_DOCUMENT_BYTES, "Story Card import");
        }
        JSONArray array = new JSONArray(raw);
        List<CardImport> imports = parseExportedStoryCards(array);

        return database.transaction(connection ->
        {
            List<StoryCard> existing = cardRepository.listForStory(storyId);
            Map<CardSignature, Integer> signatureCounts = new HashMap<>();
            for (StoryCard card : existing)
            {
                incrementSignature(signatureCounts, signatureOf(card));
            }

            if (replaceExisting)
            {
                cardRepository.deleteForStory(storyId);
                signatureCounts.clear();
            }

            int imported = 0;
            for (CardImport cardImport : imports)
            {
                CardSignature newSignature = cardImport.signature();
                if (signatureCounts.getOrDefault(newSignature, 0) > 0)
                {
                    continue;
                }

                StoryCard created = new StoryCard(Ids.newId(), storyId, cardImport.title(),
                        cardImport.triggers(), cardImport.content(), cardImport.type(), cardImport.notes(), false);
                cardRepository.insert(created);
                incrementSignature(signatureCounts, newSignature);
                imported++;
            }
            return imported;
        });
    }

    public Story importAdventure(Path path) throws Exception
    {
        try (ZipFile zipFile = new ZipFile(path.toFile()))
        {
            JSONObject metadata = readZipJson(zipFile, "metadata.json");
            if (metadata == null)
            {
                throw new IllegalStateException("metadata.json not found in archive.");
            }

            JSONObject adventure = metadata.optJSONObject("adventure");
            JSONObject state = metadata.optJSONObject("state");
            if (adventure == null)
            {
                throw new IllegalStateException("metadata.json does not contain an adventure object.");
            }

            String title = adventure.optString("title", "").trim();
            if (title.isBlank())
            {
                title = "Imported Adventure";
            }
            String plotEssentials = adventure.optString("memory", "");
            String authorNote = adventure.optString("authorsNote", "");

            String systemPrompt = extractAdventureSystemPrompt(state);
            if (systemPrompt.isBlank())
            {
                systemPrompt = defaultSystemPrompt;
            }

            String now = Timestamps.now();
            Story story = new Story(Ids.newId(), title, systemPrompt, plotEssentials, authorNote, now, now);
            List<StoryCard> cards = parseAdventureStoryCards(state, adventure, story.id());
            List<ActionChunk> actionChunks = loadAdventureActions(zipFile);
            List<ImportedAdventureBlock> importedBlocks = new ArrayList<>();
            long totalImageBytes = 0L;
            int position = 1;
            for (ActionChunk chunk : actionChunks)
            {
                for (JSONObject action : chunk.actions)
                {
                    AdventureAction mapped = mapAdventureAction(action);
                    if (mapped == null)
                    {
                        continue;
                    }
                    String blockText = mapped.text();
                    StoryImage storyImage = null;
                    if (mapped.role() == Role.IMAGE)
                    {
                        DownloadedAdventureImage downloaded = imageFetcher.fetch(mapped.imageUrl());
                        totalImageBytes += downloaded.bytes().length;
                        if (totalImageBytes > MAX_TOTAL_IMAGE_BYTES)
                        {
                            throw new IOException("AI Dungeon images exceed the "
                                    + MAX_TOTAL_IMAGE_BYTES + "-byte total import limit.");
                        }
                        String imageId = Ids.newId();
                        storyImage = new StoryImage(imageId, story.id(), mapped.text(), downloaded.mimeType(),
                                downloaded.width(), downloaded.height(), "", downloaded.bytes(), Timestamps.now());
                        blockText = imageId;
                    }
                    else if (blockText.isBlank())
                    {
                        continue;
                    }
                    Block block = new Block(Ids.newId(), story.id(), mapped.role(), blockText, Timestamps.now(),
                            position++);
                    importedBlocks.add(new ImportedAdventureBlock(block, storyImage));
                }
            }

            return database.transaction(connection ->
            {
                storyRepository.insert(story);
                for (StoryCard card : cards)
                {
                    cardRepository.insert(card);
                }
                for (ImportedAdventureBlock importedBlock : importedBlocks)
                {
                    if (importedBlock.image() != null)
                    {
                        imageRepository.insert(importedBlock.image());
                    }
                    blockRepository.insert(importedBlock.block());
                }
                return story;
            });
        }
    }

    private JSONObject readZipJson(ZipFile zipFile, String name) throws IOException
    {
        ZipEntry entry = findZipEntry(zipFile, name);
        if (entry == null)
        {
            return null;
        }
        try (var stream = zipFile.getInputStream(entry))
        {
            String raw = readUtf8(stream, MAX_JSON_DOCUMENT_BYTES, entry.getName());
            return new JSONObject(raw);
        }
    }

    private ZipEntry findZipEntry(ZipFile zipFile, String expectedName) throws IOException
    {
        ZipEntry exact = zipFile.getEntry(expectedName);
        if (exact != null && !exact.isDirectory())
        {
            return exact;
        }

        ZipEntry match = null;
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements())
        {
            ZipEntry candidate = entries.nextElement();
            if (candidate.isDirectory() || !baseName(candidate.getName()).equalsIgnoreCase(expectedName))
            {
                continue;
            }
            if (match != null)
            {
                throw new IOException("Archive contains multiple " + expectedName + " files.");
            }
            match = candidate;
        }
        return match;
    }

    private String extractAdventureSystemPrompt(JSONObject state)
    {
        if (state == null)
        {
            return "";
        }
        Object instructions = state.opt("instructions");
        if (instructions instanceof JSONObject obj)
        {
            String selectedType = obj.optString("type", "").trim();
            if (!selectedType.isBlank())
            {
                String selected = readInstructionText(obj.opt(selectedType));
                if (!selected.isBlank())
                {
                    return selected;
                }
                // A selected mode with no text-bearing field represents a model-default
                // or otherwise empty instruction choice. Do not resurrect an inactive
                // Scenario or Custom draft merely because AI Dungeon retained it.
                return "";
            }

            String[] keys = { "custom", "scenario", "text", "prompt", "content", "value" };
            for (String key : keys)
            {
                String value = readInstructionText(obj.opt(key));
                if (!value.isBlank())
                {
                    return value;
                }
            }
        }
        else if (instructions instanceof String text)
        {
            return text.trim();
        }
        return "";
    }

    private String readInstructionText(Object value)
    {
        if (value instanceof String text)
        {
            return text.trim();
        }
        if (value instanceof JSONObject obj)
        {
            String[] keys = { "text", "prompt", "content", "value", "scenario" };
            for (String key : keys)
            {
                String nested = readInstructionText(obj.opt(key));
                if (!nested.isBlank())
                {
                    return nested;
                }
            }
        }
        return "";
    }

    private List<StoryCard> parseAdventureStoryCards(JSONObject state, JSONObject adventure, String storyId)
    {
        JSONArray array = findStoryCardArray(state, adventure);
        if (array == null || array.length() == 0)
        {
            return List.of();
        }

        Map<CardSignature, StoryCard> uniqueCards = new LinkedHashMap<>();
        for (int i = 0; i < array.length(); i++)
        {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null)
            {
                continue;
            }
            String explicitTitle = readFirstNonBlank(obj, "title", "name");
            String title = explicitTitle.isBlank() ? UNTITLED_CARD : explicitTitle;
            String content = readFirstNonBlank(obj, "entry", "content", "text", "value");
            String triggers = readStoryCardTriggers(obj);
            String type = readFirstNonBlank(obj, "type");
            String notes = readFirstNonBlank(obj, "notes", "description");
            boolean pinned = obj.optBoolean("pinned", false);
            StoryCard card = new StoryCard(Ids.newId(), storyId, title, triggers, content, type, notes, pinned);
            CardSignature signature = signatureOf(card);
            StoryCard duplicate = uniqueCards.get(signature);
            if (duplicate == null || pinned && !duplicate.pinned())
            {
                uniqueCards.put(signature, card);
            }
        }
        return new ArrayList<>(uniqueCards.values());
    }

    private JSONArray findStoryCardArray(JSONObject... sources)
    {
        JSONArray empty = null;
        for (JSONObject source : sources)
        {
            if (source == null)
            {
                continue;
            }
            for (String key : List.of("storyCards", "worldInfo"))
            {
                JSONArray candidate = source.optJSONArray(key);
                if (candidate == null)
                {
                    continue;
                }
                if (candidate.length() > 0)
                {
                    return candidate;
                }
                if (empty == null)
                {
                    empty = candidate;
                }
            }
        }
        return empty;
    }

    private String readFirstNonBlank(JSONObject obj, String... keys)
    {
        for (String key : keys)
        {
            String value = obj.optString(key, "").trim();
            if (!value.isBlank())
            {
                return value;
            }
        }
        return "";
    }

    private String readStoryCardTriggers(JSONObject obj)
    {
        Object triggers = obj.opt("triggers");
        String value = joinJsonList(triggers);
        if (!value.isBlank())
        {
            return value;
        }
        Object keys = obj.opt("keys");
        value = joinJsonList(keys);
        if (!value.isBlank())
        {
            return value;
        }
        Object keywords = obj.opt("keywords");
        value = joinJsonList(keywords);
        if (!value.isBlank())
        {
            return value;
        }
        return "";
    }

    private String joinJsonList(Object value)
    {
        if (value instanceof String text)
        {
            return text.trim();
        }
        if (value instanceof JSONArray array)
        {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < array.length(); i++)
            {
                Object entry = array.get(i);
                if (entry instanceof String s)
                {
                    if (!s.isBlank())
                    {
                        parts.add(s.trim());
                    }
                }
                else if (entry instanceof JSONObject obj)
                {
                    String text = readFirstNonBlank(obj, "text", "value", "name");
                    if (!text.isBlank())
                    {
                        parts.add(text);
                    }
                }
            }
            return String.join(", ", parts);
        }
        return "";
    }

    private List<ActionChunk> loadAdventureActions(ZipFile zipFile) throws IOException
    {
        List<ActionChunk> chunks = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements())
        {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory())
            {
                continue;
            }
            Matcher matcher = ACTION_CHUNK_PATTERN.matcher(entry.getName());
            if (!matcher.matches())
            {
                continue;
            }

            int filenamePart;
            try
            {
                filenamePart = Integer.parseInt(matcher.group(1));
            }
            catch (NumberFormatException e)
            {
                throw new IOException("Invalid action chunk number: " + entry.getName(), e);
            }

            try (var stream = zipFile.getInputStream(entry))
            {
                String raw = readUtf8(stream, MAX_JSON_DOCUMENT_BYTES, entry.getName());
                JSONObject obj = new JSONObject(raw);
                int part = obj.optInt("partNumber", filenamePart);
                JSONArray actions = obj.optJSONArray("actions");
                if (actions == null)
                {
                    throw new IOException(entry.getName() + " does not contain an actions array.");
                }
                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; i < actions.length(); i++)
                {
                    JSONObject action = actions.optJSONObject(i);
                    if (action != null)
                    {
                        list.add(action);
                    }
                }
                chunks.add(new ActionChunk(part, entry.getName(), list));
            }
        }
        chunks.sort(Comparator.comparingInt((ActionChunk chunk) -> chunk.partNumber)
                .thenComparing(chunk -> chunk.entryName));
        return chunks;
    }

    private AdventureAction mapAdventureAction(JSONObject action)
    {
        String type = action.optString("type", "").trim().toLowerCase(Locale.ROOT);
        String rawText = readActionText(action);
        if ("see".equals(type))
        {
            String imageUrl = action.optString("imageUrl", "").trim();
            return imageUrl.isBlank() ? null : new AdventureAction(Role.IMAGE, rawText, imageUrl);
        }
        if (rawText.isBlank())
        {
            return null;
        }

        return switch (type)
        {
        // AI Dungeon Story-mode inputs are raw narrative prose and are indistinguishable
        // from model output in the assembled story, so they belong on the continuation side.
        case "story", "continue", "start" -> new AdventureAction(Role.ASSISTANT, rawText, "");
        case "do" -> new AdventureAction(Role.USER, normalizeAdventureActionText(rawText, false), "");
        case "say" -> new AdventureAction(Role.USER, normalizeAdventureActionText(rawText, true), "");
        default -> null;
        };
    }

    private String readActionText(JSONObject action)
    {
        Object text = action.opt("text");
        if (text instanceof String value && !value.isBlank())
        {
            return value;
        }
        Object rawText = action.opt("rawText");
        return rawText instanceof String value ? value : "";
    }

    private String normalizeAdventureActionText(String text, boolean isSay)
    {
        String normalized = text.trim();
        boolean renderedAction = normalized.startsWith(">");
        if (renderedAction)
        {
            normalized = normalized.substring(1).trim();
        }
        // Rendered AI Dungeon actions may use a named third-person character instead of
        // "You". Preserve that authored form rather than trying to infer and rewrite it.
        if (renderedAction)
        {
            return normalized;
        }
        if (isSay)
        {
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("you say"))
            {
                String payload = normalized;
                if (lower.startsWith("you "))
                {
                    payload = normalized.substring(4).trim();
                }
                String trimmed = payload.trim();
                if (!(trimmed.startsWith("\"") && trimmed.endsWith("\"")))
                {
                    trimmed = "\"" + trimmed.replaceAll("^\"|\"$", "") + "\"";
                }
                normalized = "You say " + trimmed;
            }
        }
        else
        {
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("you "))
            {
                normalized = "You " + normalized;
            }
        }
        return normalized.trim();
    }

    private static DownloadedAdventureImage downloadAdventureImage(String rawUrl)
            throws IOException, InterruptedException
    {
        URI uri = parseAdventureImageUri(rawUrl);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "image/*")
                .GET()
                .build();
        HttpResponse<InputStream> response = IMAGE_HTTP_CLIENT.send(
                request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body())
        {
            if (response.statusCode() != 200)
            {
                throw new IOException("AI Dungeon image download returned HTTP " + response.statusCode() + ".");
            }
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (declaredLength > MAX_IMAGE_BYTES)
            {
                throw new IOException("AI Dungeon image exceeds the " + MAX_IMAGE_BYTES + "-byte import limit.");
            }

            byte[] bytes = body.readNBytes(MAX_IMAGE_BYTES + 1);
            if (bytes.length > MAX_IMAGE_BYTES)
            {
                throw new IOException("AI Dungeon image exceeds the " + MAX_IMAGE_BYTES + "-byte import limit.");
            }
            if (bytes.length == 0)
            {
                throw new IOException("AI Dungeon image download returned an empty body.");
            }

            String mimeType = normalizeImageMimeType(
                    response.headers().firstValue("Content-Type").orElse(""), bytes);
            Image decoded = new Image(new ByteArrayInputStream(bytes));
            if (decoded.isError() || decoded.getWidth() <= 0 || decoded.getHeight() <= 0)
            {
                throw new IOException("AI Dungeon image could not be decoded.", decoded.getException());
            }
            int width = (int) Math.round(decoded.getWidth());
            int height = (int) Math.round(decoded.getHeight());
            if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                    || (long) width * height > MAX_IMAGE_PIXELS)
            {
                throw new IOException("AI Dungeon image dimensions exceed the import safety limit.");
            }
            return new DownloadedAdventureImage(bytes, mimeType, width, height);
        }
    }

    private static URI parseAdventureImageUri(String rawUrl) throws IOException
    {
        URI uri;
        try
        {
            uri = new URI(rawUrl);
        }
        catch (URISyntaxException e)
        {
            throw new IOException("AI Dungeon image URL is invalid.", e);
        }
        String host = uri.getHost();
        boolean aidungeonHost = host != null
                && (host.equalsIgnoreCase("aidungeon.com")
                || host.toLowerCase(Locale.ROOT).endsWith(".aidungeon.com"));
        if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || !aidungeonHost
                || uri.getUserInfo() != null)
        {
            throw new IOException("AI Dungeon image URL must use HTTPS on an aidungeon.com host.");
        }
        return uri;
    }

    private static String normalizeImageMimeType(String contentType, byte[] bytes) throws IOException
    {
        int separator = contentType.indexOf(';');
        String normalized = (separator < 0 ? contentType : contentType.substring(0, separator))
                .trim()
                .toLowerCase(Locale.ROOT);
        if (normalized.startsWith("image/"))
        {
            return normalized;
        }

        String detected = detectImageMimeType(bytes);
        if (detected.isBlank())
        {
            throw new IOException("AI Dungeon image response did not contain a supported image media type.");
        }
        return detected;
    }

    private static String detectImageMimeType(byte[] bytes)
    {
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == '\r' && bytes[5] == '\n' && bytes[6] == 0x1a && bytes[7] == '\n')
        {
            return "image/png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff)
        {
            return "image/jpeg";
        }
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a')
        {
            return "image/gif";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P')
        {
            return "image/webp";
        }
        return "";
    }

    private static final class ActionChunk
    {
        private final int partNumber;
        private final String entryName;
        private final List<JSONObject> actions;

        private ActionChunk(int partNumber, String entryName, List<JSONObject> actions)
        {
            this.partNumber = partNumber;
            this.entryName = entryName;
            this.actions = actions;
        }
    }

    @FunctionalInterface
    interface AdventureImageFetcher
    {
        DownloadedAdventureImage fetch(String imageUrl) throws Exception;
    }

    record DownloadedAdventureImage(byte[] bytes, String mimeType, int width, int height)
    {
    }

    private record ImportedAdventureBlock(Block block, StoryImage image)
    {
    }

    private record AdventureAction(Role role, String text, String imageUrl)
    {
    }

    private List<CardImport> parseExportedStoryCards(JSONArray array)
    {
        List<CardImport> imports = new ArrayList<>();
        for (int i = 0; i < array.length(); i++)
        {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null || !obj.has("keys") || !obj.has("value"))
            {
                continue;
            }

            Object keys = obj.opt("keys");
            Object value = obj.opt("value");
            if (!(keys instanceof String || keys instanceof JSONArray) || !(value instanceof String content))
            {
                continue;
            }

            String explicitTitle = obj.opt("title") instanceof String title ? title.trim() : "";
            String title = explicitTitle.isBlank() ? UNTITLED_CARD : explicitTitle;
            String triggers = joinJsonList(keys);
            String type = obj.opt("type") instanceof String cardType ? cardType.trim() : "";
            String notes = obj.opt("notes") instanceof String cardNotes
                    ? cardNotes
                    : obj.opt("description") instanceof String description ? description : "";
            imports.add(new CardImport(title, triggers, content.trim(), type, notes));
        }
        return imports;
    }

    private static String readUtf8(InputStream stream, int maxBytes, String label) throws IOException
    {
        byte[] bytes = stream.readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes)
        {
            throw new IOException(label + " exceeds the " + maxBytes + "-byte import limit.");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static String baseName(String entryName)
    {
        int slash = Math.max(entryName.lastIndexOf('/'), entryName.lastIndexOf('\\'));
        return slash < 0 ? entryName : entryName.substring(slash + 1);
    }

    private static CardSignature signatureOf(StoryCard card)
    {
        return new CardSignature(card.triggers(), card.content(), card.type(), card.notes());
    }

    private static void incrementSignature(Map<CardSignature, Integer> counts, CardSignature signature)
    {
        counts.merge(signature, 1, Integer::sum);
    }

    private record CardImport(String title, String triggers, String content, String type, String notes)
    {
        private CardSignature signature()
        {
            return new CardSignature(triggers, content, type, notes);
        }
    }

    private record CardSignature(String triggers, String content, String type, String notes)
    {
    }

}
