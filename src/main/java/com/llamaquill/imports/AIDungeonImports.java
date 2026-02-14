package com.llamaquill.imports;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.util.Ids;
import com.llamaquill.util.Timestamps;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class AIDungeonImports
{
    private final StoryRepository storyRepository;
    private final BlockRepository blockRepository;
    private final StoryCardRepository cardRepository;
    private final String defaultSystemPrompt;

    public AIDungeonImports(StoryRepository storyRepository, BlockRepository blockRepository,
            StoryCardRepository cardRepository, String defaultSystemPrompt)
    {
        this.storyRepository = storyRepository;
        this.blockRepository = blockRepository;
        this.cardRepository = cardRepository;
        this.defaultSystemPrompt = defaultSystemPrompt;
    }

    public int importStoryCards(Path path, String storyId, boolean replaceExisting) throws Exception
    {
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        JSONArray array = new JSONArray(raw);

        List<StoryCard> existing = cardRepository.listForStory(storyId);
        Map<String, StoryCard> byTitle = new HashMap<>();
        for (StoryCard card : existing)
        {
            byTitle.put(card.title(), card);
        }

        if (replaceExisting)
        {
            cardRepository.deleteForStory(storyId);
            byTitle.clear();
        }

        int imported = 0;
        for (int i = 0; i < array.length(); i++)
        {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null)
            {
                continue;
            }
            String title = obj.optString("title", "").trim();
            String triggers = obj.optString("keys", "").trim();
            String content = obj.optString("value", "").trim();
            if (title.isBlank())
            {
                continue;
            }

            StoryCard existingCard = byTitle.get(title);
            if (existingCard != null)
            {
                StoryCard updated = new StoryCard(existingCard.id(), existingCard.storyId(), title, triggers, content, false);
                cardRepository.update(updated);
            }
            else
            {
                StoryCard created = new StoryCard(Ids.newId(), storyId, title, triggers, content, false);
                cardRepository.insert(created);
            }
            imported++;
        }
        return imported;
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

            String title = adventure == null ? "" : adventure.optString("title", "").trim();
            if (title.isBlank())
            {
                title = "Imported Adventure";
            }
            String plotEssentials = adventure == null ? "" : adventure.optString("memory", "");
            String authorNote = adventure == null ? "" : adventure.optString("authorsNote", "");

            String systemPrompt = extractAdventureSystemPrompt(state);
            if (systemPrompt.isBlank())
            {
                systemPrompt = defaultSystemPrompt;
            }

            String now = Timestamps.now();
            Story story = new Story(Ids.newId(), title, systemPrompt, plotEssentials, authorNote, now, now);
            storyRepository.insert(story);

            List<StoryCard> cards = parseAdventureStoryCards(state, story.id());
            for (StoryCard card : cards)
            {
                cardRepository.insert(card);
            }

            List<ActionChunk> actionChunks = loadAdventureActions(zipFile);
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
                    String text = mapped.text();
                    if (text.isBlank())
                    {
                        continue;
                    }
                    Block block = new Block(Ids.newId(), story.id(), mapped.role(), text, Timestamps.now(), position++);
                    blockRepository.insert(block);
                }
            }

            return story;
        }
    }

    private JSONObject readZipJson(ZipFile zipFile, String name) throws IOException
    {
        ZipEntry entry = zipFile.getEntry(name);
        if (entry == null)
        {
            return null;
        }
        try (var stream = zipFile.getInputStream(entry))
        {
            String raw = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return new JSONObject(raw);
        }
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
            Object custom = obj.opt("custom");
            if (custom instanceof String customText && !customText.isBlank())
            {
                return customText;
            }
            if (custom instanceof JSONObject customObj)
            {
                String[] keys = { "text", "prompt", "content", "value" };
                for (String key : keys)
                {
                    String value = customObj.optString(key, "").trim();
                    if (!value.isBlank())
                    {
                        return value;
                    }
                }
            }
            String fallback = obj.optString("text", "").trim();
            if (!fallback.isBlank())
            {
                return fallback;
            }
        }
        else if (instructions instanceof String text)
        {
            return text.trim();
        }
        return "";
    }

    private List<StoryCard> parseAdventureStoryCards(JSONObject state, String storyId)
    {
        if (state == null)
        {
            return List.of();
        }
        JSONArray array = state.optJSONArray("storyCards");
        if (array == null || array.length() == 0)
        {
            return List.of();
        }

        Map<String, StoryCard> byTitle = new HashMap<>();
        for (int i = 0; i < array.length(); i++)
        {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null)
            {
                continue;
            }
            String title = readFirstNonBlank(obj, "title", "name");
            if (title.isBlank())
            {
                continue;
            }
            String content = readFirstNonBlank(obj, "entry", "content", "text", "value");
            String triggers = readStoryCardTriggers(obj);
            boolean pinned = obj.optBoolean("pinned", false);
            StoryCard card = new StoryCard(Ids.newId(), storyId, title, triggers, content, pinned);
            byTitle.put(title.toLowerCase(), card);
        }
        return new ArrayList<>(byTitle.values());
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
        Pattern pattern = Pattern.compile("^actions-(\\d+)\\.json$");
        zipFile.stream()
                .filter(entry -> !entry.isDirectory())
                .forEach(entry ->
                {
                    Matcher matcher = pattern.matcher(entry.getName());
                    if (!matcher.matches())
                    {
                        return;
                    }
                    try (var stream = zipFile.getInputStream(entry))
                    {
                        String raw = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                        JSONObject obj = new JSONObject(raw);
                        int part = obj.optInt("partNumber", Integer.parseInt(matcher.group(1)));
                        JSONArray actions = obj.optJSONArray("actions");
                        if (actions == null)
                        {
                            actions = new JSONArray();
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
                        chunks.add(new ActionChunk(part, list));
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e);
                    }
                });
        chunks.sort(Comparator.comparingInt(chunk -> chunk.partNumber));
        return chunks;
    }

    private AdventureAction mapAdventureAction(JSONObject action)
    {
        String type = action.optString("type", "").trim().toLowerCase();
        String text = action.optString("text", "").trim();
        if (text.isBlank())
        {
            return null;
        }

        return switch (type)
        {
        case "story", "continue", "start" -> new AdventureAction(Role.ASSISTANT, text);
        case "do" -> new AdventureAction(Role.USER, normalizeAdventureActionText(text, false));
        case "say" -> new AdventureAction(Role.USER, normalizeAdventureActionText(text, true));
        default -> null;
        };
    }

    private String normalizeAdventureActionText(String text, boolean isSay)
    {
        String normalized = text.trim();
        if (normalized.startsWith(">"))
        {
            normalized = normalized.substring(1).trim();
        }
        if (isSay)
        {
            String lower = normalized.toLowerCase();
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
            String lower = normalized.toLowerCase();
            if (!lower.startsWith("you "))
            {
                normalized = "You " + normalized;
            }
        }
        return normalized.trim();
    }

    private static final class ActionChunk
    {
        private final int partNumber;
        private final List<JSONObject> actions;

        private ActionChunk(int partNumber, List<JSONObject> actions)
        {
            this.partNumber = partNumber;
            this.actions = actions;
        }
    }

    private record AdventureAction(Role role, String text)
    {
    }
}
