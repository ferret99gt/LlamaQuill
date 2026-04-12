package com.llamaquill.image;

import com.llamaquill.autocards.AutoCards;
import com.llamaquill.autocards.AutoCardsService;
import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.ImageRepository;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.AppAutoCardsSettings;
import com.llamaquill.model.AppSettings;
import com.llamaquill.model.Block;
import com.llamaquill.model.ModelAutoCardsSettings;
import com.llamaquill.model.ModelSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.model.StoryImage;
import com.llamaquill.serviceClients.ComfyUiClient;
import com.llamaquill.util.Ids;
import com.llamaquill.util.Timestamps;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ImageGenerationCoordinator
{
    private final ImageRepository imageRepository;
    private final BlockRepository blockRepository;
    private final StoryRepository storyRepository;
    private final StoryCardRepository storyCardRepository;
    private final AutoCardsService autoCardsService;
    private final ComfyUiClient comfyUiClient;
    private final Map<String, StoryImage> storyImageCache = new HashMap<>();
    private final Map<String, String> workflowTemplateCache = new HashMap<>();

    public ImageGenerationCoordinator(ImageRepository imageRepository, BlockRepository blockRepository,
            StoryRepository storyRepository, StoryCardRepository storyCardRepository, AutoCardsService autoCardsService,
            ComfyUiClient comfyUiClient)
    {
        this.imageRepository = Objects.requireNonNull(imageRepository, "imageRepository");
        this.blockRepository = Objects.requireNonNull(blockRepository, "blockRepository");
        this.storyRepository = Objects.requireNonNull(storyRepository, "storyRepository");
        this.storyCardRepository = Objects.requireNonNull(storyCardRepository, "storyCardRepository");
        this.autoCardsService = Objects.requireNonNull(autoCardsService, "autoCardsService");
        this.comfyUiClient = Objects.requireNonNull(comfyUiClient, "comfyUiClient");
    }

    public String generateImagePrompt(Story story, String request, AppSettings appSettings, ModelSettings modelSettings,
            AppAutoCardsSettings appAutoCardsSettings, ModelAutoCardsSettings modelAutoCardsSettings) throws Exception
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(appSettings, "appSettings");
        Objects.requireNonNull(modelSettings, "modelSettings");
        Objects.requireNonNull(appAutoCardsSettings, "appAutoCardsSettings");
        Objects.requireNonNull(modelAutoCardsSettings, "modelAutoCardsSettings");

        List<Block> currentBlocks = blockRepository.listForStory(story.id());
        List<StoryCard> currentCards = storyCardRepository.listForStory(story.id());

        String contextMode = AutoCards.normalizeContextMode(appAutoCardsSettings.contextMode());
        String excerpt = "";
        if (!AutoCards.CONTEXT_MODE_FULL_STORY.equals(contextMode))
        {
            excerpt = buildAutoCardsExcerpt(currentBlocks, appAutoCardsSettings.candidateWindow());
        }

        AutoCardsService.PromptContext fullStoryContext = AutoCardsService.PromptContext.empty();
        if (AutoCards.CONTEXT_MODE_FULL_STORY.equals(contextMode))
        {
            fullStoryContext = autoCardsService.buildFullStoryContext(
                    story,
                    currentBlocks,
                    currentCards,
                    appSettings,
                    modelSettings,
                    modelAutoCardsSettings);
        }

        return autoCardsService.generateImagePromptFromUserPrompt(
                request,
                excerpt,
                fullStoryContext,
                appSettings,
                modelSettings,
                modelAutoCardsSettings);
    }

    public ComfyUiClient.GenerationResult generateImages(AppSettings appSettings, String promptText)
            throws IOException, InterruptedException
    {
        Objects.requireNonNull(appSettings, "appSettings");
        String template = loadWorkflowTemplateJson(appSettings.comfyWorkflow());
        return comfyUiClient.generateImages(template, promptText,
                appSettings.comfyWidth(), appSettings.comfyHeight(), appSettings.comfyBatchSize());
    }

    public ImageMutationResult insertOrReplaceImage(Story story, PendingImage pending, String promptText, Block replaceImageBlock)
            throws SQLException
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(pending, "pending");

        StoryImage storyImage = createStoryImage(story.id(), promptText, pending.mimeType(), pending.workflowJson(), pending.bytes());
        imageRepository.insert(storyImage);
        storyImageCache.put(storyImage.id(), storyImage);

        boolean replacingImage = replaceImageBlock != null && replaceImageBlock.role() == Role.IMAGE;
        if (replacingImage)
        {
            String oldImageId = replaceImageBlock.text();
            Block updated = new Block(replaceImageBlock.id(), replaceImageBlock.storyId(), Role.IMAGE, storyImage.id(),
                    Timestamps.now(), replaceImageBlock.position());
            blockRepository.replaceHead(updated);
            deleteImageById(oldImageId);
        }
        else
        {
            int position = blockRepository.nextPosition(story.id());
            Block imageBlock = new Block(Ids.newId(), story.id(), Role.IMAGE, storyImage.id(), Timestamps.now(), position);
            blockRepository.insert(imageBlock);
        }

        Story updatedStory = touchStory(story);
        return new ImageMutationResult(updatedStory, storyImage, replacingImage);
    }

    public ImageMutationResult replaceImageFromRetryHistory(Story story, Block headBlock, String prompt, byte[] bytes,
            String mimeType, String workflowJson) throws SQLException
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(headBlock, "headBlock");

        StoryImage storyImage = createStoryImage(story.id(), prompt, mimeType, workflowJson, bytes);
        imageRepository.insert(storyImage);
        storyImageCache.put(storyImage.id(), storyImage);

        String oldImageId = headBlock.text();
        Block updated = new Block(headBlock.id(), headBlock.storyId(), Role.IMAGE, storyImage.id(), Timestamps.now(),
                headBlock.position());
        blockRepository.replaceHead(updated);
        deleteImageById(oldImageId);

        Story updatedStory = touchStory(story);
        return new ImageMutationResult(updatedStory, storyImage, true);
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
        String now = Timestamps.now();
        Story updated = new Story(story.id(), story.title(), story.systemPrompt(), story.plotEssentials(),
                story.authorNote(), story.createdAt(), now);
        storyRepository.update(updated);
        return updated;
    }

    private static String buildAutoCardsExcerpt(List<Block> currentBlocks, int window)
    {
        int start = Math.max(0, currentBlocks.size() - Math.max(1, window));
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < currentBlocks.size(); i++)
        {
            Block block = currentBlocks.get(i);
            if (block.role() != Role.USER && block.role() != Role.ASSISTANT)
            {
                continue;
            }
            sb.append(block.role() == Role.USER ? "User: " : "Story: ");
            sb.append(block.text().trim());
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    public record PendingImage(byte[] bytes, String mimeType, String workflowJson)
    {
    }

    public record ImageMutationResult(Story updatedStory, StoryImage storyImage, boolean replaced)
    {
    }
}
