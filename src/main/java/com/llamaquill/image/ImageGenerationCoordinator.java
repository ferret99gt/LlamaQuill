package com.llamaquill.image;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.ImageRepository;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.generation.AuxiliaryGenerationService;
import com.llamaquill.model.AppSettings;
import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.ConversationLayout;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.model.StoryImage;
import com.llamaquill.prompt.PromptAuxiliaryInput;
import com.llamaquill.serviceClients.ComfyUiClient;
import com.llamaquill.util.Ids;
import com.llamaquill.util.BoundedLruCache;
import com.llamaquill.util.Timestamps;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ImageGenerationCoordinator
{
    private static final int MAX_CACHED_STORY_IMAGES = 8;
    private static final int MAX_CACHED_WORKFLOW_TEMPLATES = 16;

    private final Database database;
    private final ImageRepository imageRepository;
    private final BlockRepository blockRepository;
    private final StoryRepository storyRepository;
    private final StoryCardRepository storyCardRepository;
    private final AuxiliaryGenerationService auxiliaryGenerationService;
    private final ComfyUiClient comfyUiClient;
    private final BoundedLruCache<String, StoryImage> storyImageCache =
            new BoundedLruCache<>(MAX_CACHED_STORY_IMAGES);
    private final BoundedLruCache<String, String> workflowTemplateCache =
            new BoundedLruCache<>(MAX_CACHED_WORKFLOW_TEMPLATES);

    public ImageGenerationCoordinator(Database database, ImageRepository imageRepository, BlockRepository blockRepository,
            StoryRepository storyRepository, StoryCardRepository storyCardRepository,
            AuxiliaryGenerationService auxiliaryGenerationService, ComfyUiClient comfyUiClient)
    {
        this.database = Objects.requireNonNull(database, "database");
        this.imageRepository = Objects.requireNonNull(imageRepository, "imageRepository");
        this.blockRepository = Objects.requireNonNull(blockRepository, "blockRepository");
        this.storyRepository = Objects.requireNonNull(storyRepository, "storyRepository");
        this.storyCardRepository = Objects.requireNonNull(storyCardRepository, "storyCardRepository");
        this.auxiliaryGenerationService = Objects.requireNonNull(
                auxiliaryGenerationService, "auxiliaryGenerationService");
        this.comfyUiClient = Objects.requireNonNull(comfyUiClient, "comfyUiClient");
    }

    public String generateImagePrompt(Story story, String request, GenerationSettings settings) throws Exception
    {
        return generateImagePromptResult(story, request, settings).content();
    }

    public AuxiliaryGenerationService.Result generateImagePromptResult(
            Story story, String request, GenerationSettings settings) throws Exception
    {
        return generateImagePromptResult(story, request, settings, true);
    }

    public AuxiliaryGenerationService.Result generateImagePromptResult(
            Story story, String request, GenerationSettings settings, boolean ignoreResponseLength) throws Exception
    {
        return generateImagePromptResult(story, "", request, settings, ignoreResponseLength);
    }

    public AuxiliaryGenerationService.Result generateImagePromptResult(
            Story story, String stylePrompt, String request, GenerationSettings settings,
            boolean ignoreResponseLength) throws Exception
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(settings, "settings");

        List<Block> currentBlocks = blockRepository.listForStory(story.id());
        List<StoryCard> currentCards = storyCardRepository.listForStory(story.id());
        String userPrompt = """
                Your job is to generate a prompt for an image generator that describes the most recent scene in the story. The prompt must describe each of the important subjects (gender, age, hair, eyes, build, clothing, and other visible details), what they are doing (eating, walking, talking, holding an object, using a weapon, etc), and where they are doing it (describe the room and theme, such as "an opulent castle bedroom during the morning").""";
        String trimmedStylePrompt = stylePrompt == null ? "" : stylePrompt.trim();
        if (!trimmedStylePrompt.isBlank())
        {
            userPrompt += "\n\n# Style preset\n" + trimmedStylePrompt;
        }
        String trimmedRequest = request == null ? "" : request.trim();
        if (!trimmedRequest.isBlank())
        {
            userPrompt += "\n\n# User specific request\n" + trimmedRequest;
        }
        PromptAuxiliaryInput auxiliaryInput = new PromptAuxiliaryInput(
                List.of(
                        new ChatMessage("system", "You create image generation prompts for story scenes."),
                        new ChatMessage("user", userPrompt)),
                "",
                null);
        GenerationSettings seeSettings = settings.withConversationLayout(ConversationLayout.ROLE_AWARE);
        if (ignoreResponseLength)
        {
            seeSettings = seeSettings.withoutNumPredict();
        }
        return auxiliaryGenerationService.generate(
                story, currentBlocks, currentCards, seeSettings, auxiliaryInput);
    }

    public ComfyUiClient.GenerationResult generateImages(AppSettings appSettings, String promptText)
            throws IOException, InterruptedException
    {
        Objects.requireNonNull(appSettings, "appSettings");
        String template = loadWorkflowTemplateJson(appSettings.comfyWorkflow());
        return comfyUiClient.generateImages(template, promptText,
                appSettings.comfyWidth(), appSettings.comfyHeight(), appSettings.comfyBatchSize());
    }

    public ImageMutationResult insertOrReplaceImage(Story story, String expectedHeadId, PendingImage pending, String promptText,
            Block replaceImageBlock)
            throws SQLException
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(pending, "pending");

        StoryImage storyImage = createStoryImage(story.id(), promptText, pending.mimeType(), pending.workflowJson(), pending.bytes());
        boolean replacingImage = replaceImageBlock != null && replaceImageBlock.role() == Role.IMAGE;
        String oldImageId = replacingImage ? replaceImageBlock.text() : null;
        ImageMutationResult result = database.transaction(connection ->
        {
            if (!blockRepository.isCurrentHead(story.id(), expectedHeadId))
            {
                return new ImageMutationResult(story, null, false, true);
            }
            imageRepository.insert(storyImage);
            if (replacingImage)
            {
                Block updated = new Block(replaceImageBlock.id(), replaceImageBlock.storyId(), Role.IMAGE, storyImage.id(),
                        Timestamps.now(), replaceImageBlock.position());
                blockRepository.replaceHead(updated);
                imageRepository.deleteById(oldImageId);
            }
            else
            {
                int position = blockRepository.nextPosition(story.id());
                Block imageBlock = new Block(Ids.newId(), story.id(), Role.IMAGE, storyImage.id(), Timestamps.now(), position);
                blockRepository.insert(imageBlock);
            }

            Story updatedStory = touchStory(story);
            return new ImageMutationResult(updatedStory, storyImage, replacingImage, false);
        });
        if (result.stale())
        {
            return result;
        }
        storyImageCache.put(storyImage.id(), storyImage);
        if (oldImageId != null)
        {
            storyImageCache.remove(oldImageId);
        }
        return result;
    }

    public ImageMutationResult replaceImageFromRetryHistory(Story story, Block headBlock, String prompt, byte[] bytes,
            String mimeType, String workflowJson) throws SQLException
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(headBlock, "headBlock");

        StoryImage storyImage = createStoryImage(story.id(), prompt, mimeType, workflowJson, bytes);
        String oldImageId = headBlock.text();
        ImageMutationResult result = database.transaction(connection ->
        {
            if (!blockRepository.isCurrentHead(story.id(), headBlock.id()))
            {
                return new ImageMutationResult(story, null, false, true);
            }
            imageRepository.insert(storyImage);
            Block updated = new Block(headBlock.id(), headBlock.storyId(), Role.IMAGE, storyImage.id(), Timestamps.now(),
                    headBlock.position());
            blockRepository.replaceHead(updated);
            imageRepository.deleteById(oldImageId);
            Story updatedStory = touchStory(story);
            return new ImageMutationResult(updatedStory, storyImage, true, false);
        });
        if (result.stale())
        {
            return result;
        }
        storyImageCache.put(storyImage.id(), storyImage);
        storyImageCache.remove(oldImageId);
        return result;
    }

    public StoryImage loadStoryImage(String imageId) throws SQLException
    {
        if (imageId == null || imageId.isBlank())
        {
            return null;
        }
        StoryImage cached = storyImageCache.get(imageId);
        if (cached != null)
        {
            return cached;
        }

        Optional<StoryImage> loaded = imageRepository.findById(imageId);
        if (loaded.isPresent())
        {
            storyImageCache.put(imageId, loaded.get());
            return loaded.get();
        }
        return null;
    }

    public void deleteImageById(String imageId) throws SQLException
    {
        if (imageId == null || imageId.isBlank())
        {
            return;
        }
        imageRepository.deleteById(imageId);
        storyImageCache.remove(imageId);
    }

    private String loadWorkflowTemplateJson(String workflowName) throws IOException
    {
        String selected = workflowName == null ? "" : workflowName.trim();
        if (selected.isBlank())
        {
            selected = AppSettings.DEFAULT_COMFY_WORKFLOW;
        }
        String cached = workflowTemplateCache.get(selected);
        if (cached != null && !cached.isBlank())
        {
            return cached;
        }

        String resource = "/comfyui/" + selected + ".json";
        try (InputStream in = ImageGenerationCoordinator.class.getResourceAsStream(resource))
        {
            if (in == null)
            {
                throw new IOException("Missing workflow resource: " + resource);
            }
            String template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            workflowTemplateCache.put(selected, template);
            return template;
        }
    }

    private StoryImage createStoryImage(String storyId, String promptText, String mimeType, String workflowJson, byte[] bytes)
    {
        Image decoded = new Image(new ByteArrayInputStream(bytes == null ? new byte[0] : bytes));
        int width = (int) Math.round(decoded.getWidth());
        int height = (int) Math.round(decoded.getHeight());
        return new StoryImage(
                Ids.newId(),
                storyId,
                promptText == null ? "" : promptText,
                mimeType == null || mimeType.isBlank() ? "image/png" : mimeType,
                Math.max(0, width),
                Math.max(0, height),
                workflowJson == null ? "" : workflowJson,
                bytes,
                Timestamps.now());
    }

    private Story touchStory(Story story) throws SQLException
    {
        return storyRepository.touch(story.id(), Timestamps.now());
    }

    public record PendingImage(byte[] bytes, String mimeType, String workflowJson)
    {
    }

    public record ImageMutationResult(Story updatedStory, StoryImage storyImage, boolean replaced, boolean stale)
    {
    }
}
