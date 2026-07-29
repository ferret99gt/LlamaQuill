package com.llamaquill.storyview;

import com.llamaquill.generation.TurnInputPane;
import com.llamaquill.image.StoryImageDialogs;
import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import com.llamaquill.model.StoryImage;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class StoryPaneController
{
    private static final int ASSISTANT_FLOW_CHUNK_CHAR_LIMIT = 6000;
    private static final int ASSISTANT_FLOW_CHUNK_BLOCK_LIMIT = 48;
    private static final long STREAMING_FRAME_INTERVAL_NANOS = 33_333_333L;
    private static final String STREAMING_DRAFT_ID = "__llamaquill_streaming_draft__";
    private static final String STORY_BLOCK_ID_KEY = "llamaquill-story-block-id";
    private static final String DECORATION_UPDATE_KEY = "llamaquill-decoration-update";

    @FunctionalInterface
    public interface BlockTextPersister
    {
        void persist(String blockId, String text, Runnable onSuccess, Consumer<Exception> onFailure);
    }

    @FunctionalInterface
    public interface BlockDeletionHandler
    {
        boolean delete(Block block, boolean forceScroll);
    }

    public enum StreamingMode
    {
        APPEND,
        RETRY,
        TURN
    }

    private final Stage owner;
    private final Runnable onSubmitTurn;
    private final Function<String, StoryImage> imageLoader;
    private final Consumer<StoryImage> imageSaver;
    private final BlockDeletionHandler blockDeletionHandler;
    private final BlockTextPersister blockTextPersister;
    private final BiConsumer<String, Exception> errorHandler;

    private final ObservableList<StoryItem> storyRows = FXCollections.observableArrayList();
    private final ListView<StoryItem> storyListView = new ListView<>(storyRows);
    private final PauseTransition storyViewportRefreshDebounce = new PauseTransition(Duration.millis(120));
    private boolean suppressPostScrollLayout;
    private final ChangeListener<Number> storyScrollListener =
            (observable, oldValue, newValue) ->
            {
                if (shouldSchedulePostScrollLayout())
                {
                    schedulePostScrollLayout(newValue.doubleValue());
                }
            };
    private final TurnInputPane turnInputPane;
    private final Region storyInteractionShield = new Region();
    private final Object streamingLock = new Object();
    private final StringBuilder streamingText = new StringBuilder();
    private long lastStreamingRenderNanos;
    private final AnimationTimer streamingPulse = new AnimationTimer()
    {
        @Override
        public void handle(long now)
        {
            if (lastStreamingRenderNanos == 0
                    || now - lastStreamingRenderNanos >= STREAMING_FRAME_INTERVAL_NANOS)
            {
                lastStreamingRenderNanos = now;
                renderQueuedStreamingFrame();
            }
            advanceStreamingBottomFollow();
        }
    };

    private List<Block> currentBlocks = new ArrayList<>();
    private HBox storyActionRow;
    private DoubleBinding storyContentWidthBinding;
    private DoubleBinding storyRowContentWidthBinding;
    private String activeAssistantEditId;
    private final TextArea blockEditor;
    private StoryCell activeEditorCell;
    private boolean activeEditorOpenedAtBottom;
    private double activeEditorRequestedSceneY = Double.NaN;
    private long editorAnchorSequence;
    private long queuedEditorAnchorSequence = -1;
    private ScrollBar observedStoryScrollBar;
    private boolean postScrollLayoutQueued;
    private double requestedScrollValue;
    private long viewportLayoutSequence;
    private long bottomScrollSequence;
    private int pendingStreamingBottomAnchorPasses;
    private boolean streamingFlowLayoutPending;

    private boolean streamingActive;
    private StreamingMode streamingMode;
    private List<Block> streamingOriginalBlocks = List.of();
    private Block streamingSeed;
    private long streamingSequence;
    private long streamingToken;
    private long streamingVersion;
    private long renderedStreamingVersion = -1;
    private boolean streamingDraftVisible;
    private StreamingAssistantItem streamingDraftItem;

    public StoryPaneController(Stage owner, Runnable onSubmitTurn, Function<String, StoryImage> imageLoader,
            Consumer<StoryImage> imageSaver, BlockDeletionHandler blockDeletionHandler,
            BlockTextPersister blockTextPersister, BiConsumer<String, Exception> errorHandler)
    {
        this.owner = owner;
        this.onSubmitTurn = onSubmitTurn;
        this.imageLoader = imageLoader;
        this.imageSaver = imageSaver;
        this.blockDeletionHandler = blockDeletionHandler;
        this.blockTextPersister = blockTextPersister;
        this.errorHandler = errorHandler;

        initializeStoryListView();
        blockEditor = createBlockEditor();
        turnInputPane = new TurnInputPane(onSubmitTurn, () -> showTurnInput(false));
    }

    public BorderPane buildCenterPane(Button takeTurnButton, Button continueButton, Button seeButton, Button retryButton,
            Button retryHistoryButton, Button deleteButton, Button promptButton)
    {
        BorderPane centerPane = new BorderPane();
        centerPane.getStyleClass().add("center-pane");

        storyInteractionShield.setVisible(false);
        storyInteractionShield.setManaged(false);
        storyInteractionShield.setPickOnBounds(true);
        storyInteractionShield.setStyle("-fx-background-color: transparent;");
        StackPane storyViewport = new StackPane(storyListView, storyInteractionShield);
        Rectangle viewportClip = new Rectangle();
        viewportClip.widthProperty().bind(storyViewport.widthProperty());
        viewportClip.heightProperty().bind(storyViewport.heightProperty());
        storyViewport.setClip(viewportClip);
        centerPane.setCenter(storyViewport);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        storyActionRow = new HBox(8, takeTurnButton, continueButton, seeButton, retryButton, retryHistoryButton,
                deleteButton, spacer, promptButton);
        storyActionRow.getStyleClass().add("action-row");
        storyActionRow.setAlignment(Pos.CENTER_LEFT);
        storyActionRow.setPadding(new Insets(10));

        VBox bottomBox = new VBox(8, turnInputPane.root(), storyActionRow);
        centerPane.setBottom(bottomBox);
        return centerPane;
    }

    public void setActionButtonsDisabled(boolean disabled)
    {
        if (storyActionRow != null)
        {
            storyActionRow.setDisable(disabled);
        }
    }

    public void showTurnInput(boolean show)
    {
        turnInputPane.setVisible(show);
    }

    public String turnInputText()
    {
        return turnInputPane.text();
    }

    public void clearTurnInput()
    {
        turnInputPane.clear();
    }

    public void commitActiveEdit()
    {
        if (activeAssistantEditId != null)
        {
            commitBlockEdit(blockEditor.getText());
        }
    }

    public void renderBlocks(List<Block> blocks, boolean forceScroll)
    {
        stopStreamingState();
        discardActiveBlockEditor();
        currentBlocks = new ArrayList<>(blocks == null ? List.of() : blocks);
        storyRows.setAll(buildStoryItems(currentBlocks));
        clearStoryListSelection();
        storyListView.getFocusModel().focus(-1);
        Platform.runLater(this::installStoryScrollListener);
        if (forceScroll)
        {
            scrollToBottom();
        }
    }

    public long startStreaming(StreamingMode mode)
    {
        commitActiveEdit();
        invalidateViewportPreservation();
        synchronized (streamingLock)
        {
            streamingToken = ++streamingSequence;
            streamingActive = true;
            streamingMode = mode;
            streamingOriginalBlocks = List.copyOf(currentBlocks);
            streamingSeed = null;
            streamingText.setLength(0);
            streamingVersion++;
            renderedStreamingVersion = -1;
            streamingDraftVisible = false;
            streamingDraftItem = null;
            lastStreamingRenderNanos = 0;
        }
        clearStreamingBottomFollow();
        setStoryInteractionLocked(true);
        streamingPulse.start();
        return streamingToken;
    }

    public void queueStreamingSeed(long token, Block seed)
    {
        synchronized (streamingLock)
        {
            if (!streamingActive || token != streamingToken)
            {
                return;
            }
            streamingSeed = seed;
            streamingVersion++;
        }
    }

    public void queueStreamingAttempt(long token, String generatedPrefix)
    {
        synchronized (streamingLock)
        {
            if (!streamingActive || token != streamingToken)
            {
                return;
            }
            streamingText.setLength(0);
            if (generatedPrefix != null)
            {
                streamingText.append(generatedPrefix);
            }
            streamingVersion++;
        }
    }

    public void queueStreamingText(long token, String chunk)
    {
        if (chunk == null || chunk.isEmpty())
        {
            return;
        }
        synchronized (streamingLock)
        {
            if (!streamingActive || token != streamingToken)
            {
                return;
            }
            streamingText.append(chunk);
            streamingVersion++;
        }
    }

    public void endStreaming(long token)
    {
        synchronized (streamingLock)
        {
            if (!streamingActive || token != streamingToken)
            {
                return;
            }
        }
        endStreamingState();
    }

    public void cancelStreaming(long token)
    {
        List<Block> original;
        synchronized (streamingLock)
        {
            if (!streamingActive || token != streamingToken)
            {
                return;
            }
            original = streamingOriginalBlocks;
        }
        endStreamingState();
        storyRows.setAll(buildStoryItems(original));
        scrollToBottom();
    }

    private void endStreamingState()
    {
        streamingPulse.stop();
        synchronized (streamingLock)
        {
            streamingActive = false;
            streamingText.setLength(0);
            streamingSeed = null;
            streamingOriginalBlocks = List.of();
            streamingDraftVisible = false;
            streamingDraftItem = null;
            lastStreamingRenderNanos = 0;
        }
        clearStreamingBottomFollow();
        setStoryInteractionLocked(false);
    }

    private void stopStreamingState()
    {
        streamingPulse.stop();
        synchronized (streamingLock)
        {
            streamingToken = ++streamingSequence;
            streamingActive = false;
            streamingText.setLength(0);
            streamingSeed = null;
            streamingOriginalBlocks = List.of();
            streamingDraftVisible = false;
            streamingDraftItem = null;
            lastStreamingRenderNanos = 0;
        }
        clearStreamingBottomFollow();
        setStoryInteractionLocked(false);
    }

    private void setStoryInteractionLocked(boolean locked)
    {
        storyInteractionShield.setManaged(locked);
        storyInteractionShield.setVisible(locked);
    }

    void renderQueuedStreamingFrame()
    {
        StreamingSnapshot snapshot;
        synchronized (streamingLock)
        {
            if (!streamingActive || streamingVersion == renderedStreamingVersion)
            {
                return;
            }
            renderedStreamingVersion = streamingVersion;
            snapshot = new StreamingSnapshot(streamingMode, streamingOriginalBlocks, streamingSeed,
                    streamingText.toString());
        }

        if (snapshot.text().isBlank())
        {
            List<Block> visibleBlocks = new ArrayList<>(snapshot.originalBlocks());
            boolean showCommittedTurn = snapshot.mode() == StreamingMode.TURN && snapshot.seed() != null;
            if (showCommittedTurn)
            {
                visibleBlocks.add(snapshot.seed());
            }
            if (streamingDraftVisible || showCommittedTurn)
            {
                streamingDraftItem = null;
                storyRows.setAll(buildStoryItems(visibleBlocks));
                streamingDraftVisible = showCommittedTurn;
                scrollToBottom();
            }
            return;
        }
        if (snapshot.mode() == StreamingMode.TURN && snapshot.seed() == null)
        {
            return;
        }

        if (streamingDraftItem == null)
        {
            stageStreamingDraft(snapshot);
            return;
        }
        streamingDraftItem.setText(snapshot.text());
    }

    private void stageStreamingDraft(StreamingSnapshot snapshot)
    {
        List<Block> visibleBlocks = new ArrayList<>(snapshot.originalBlocks());
        if (snapshot.mode() == StreamingMode.RETRY && !visibleBlocks.isEmpty())
        {
            visibleBlocks.removeLast();
        }
        if (snapshot.mode() == StreamingMode.TURN)
        {
            visibleBlocks.add(snapshot.seed());
        }

        List<StoryItem> stagedItems = new ArrayList<>(buildStoryItems(visibleBlocks));
        List<Block> assistantPrefix = List.of();
        if (!stagedItems.isEmpty() && stagedItems.getLast() instanceof AssistantItem assistant)
        {
            stagedItems.removeLast();
            assistantPrefix = assistant.blocks();
        }

        StreamingAssistantItem draftItem = new StreamingAssistantItem(assistantPrefix);
        draftItem.setText(snapshot.text());
        stagedItems.add(draftItem);
        streamingDraftItem = draftItem;
        streamingDraftVisible = true;
        storyRows.setAll(stagedItems);
        scrollToBottom();
    }

    private void initializeStoryListView()
    {
        storyListView.getStyleClass().add("story-list");
        storyListView.setFocusTraversable(false);
        storyListView.setPadding(new Insets(10, 0, 10, 0));
        storyListView.setCellFactory(list -> new StoryCell());
        storyListView.addEventFilter(MouseEvent.MOUSE_PRESSED, event ->
        {
            if (!(event.getTarget() instanceof Node target))
            {
                return;
            }
            boolean editorTarget = blockEditor != null && isDescendantOf(target, blockEditor);
            if (activeAssistantEditId != null && !editorTarget)
            {
                String editedBlockId = activeAssistantEditId;
                Platform.runLater(() ->
                {
                    if (editedBlockId.equals(activeAssistantEditId))
                    {
                        commitBlockEdit(blockEditor.getText());
                    }
                });
            }

            StoryCell targetCell = enclosingStoryCell(target);
            boolean imageTarget = targetCell != null && targetCell.getItem() instanceof ImageItem;
            if (targetCell != null && !imageTarget && !editorTarget && !targetsStoryBlock(target))
            {
                event.consume();
            }
        });

        storyContentWidthBinding = Bindings.max(0.0, storyListView.widthProperty().subtract(52));
        storyRowContentWidthBinding = Bindings.max(0.0, storyContentWidthBinding.subtract(24));
        storyViewportRefreshDebounce.setOnFinished(event -> relayoutStoryPreserveViewport());
        storyListView.widthProperty().addListener((obs, oldValue, newValue) -> scheduleStoryViewportRefresh());
        storyListView.heightProperty().addListener((obs, oldValue, newValue) -> scheduleStoryViewportRefresh());
        storyListView.skinProperty().addListener((obs, oldValue, newValue) ->
                Platform.runLater(this::installStoryScrollListener));
    }

    private List<StoryItem> buildStoryItems(List<Block> blocks)
    {
        if (blocks == null || blocks.isEmpty())
        {
            return List.of();
        }
        String latestAssistantId = findLatestAssistantId(blocks);
        List<StoryItem> items = new ArrayList<>();
        List<Block> assistantGroup = new ArrayList<>();
        for (Block block : blocks)
        {
            if (block.role() == Role.ASSISTANT)
            {
                assistantGroup.add(block);
                continue;
            }
            addAssistantItems(items, assistantGroup, latestAssistantId);
            assistantGroup.clear();
            if (block.role() == Role.USER)
            {
                items.add(new UserItem(block));
            }
            else if (block.role() == Role.IMAGE)
            {
                items.add(new ImageItem(block));
            }
        }
        addAssistantItems(items, assistantGroup, latestAssistantId);
        return items;
    }

    private void addAssistantItems(List<StoryItem> items, List<Block> group, String latestAssistantId)
    {
        if (group.isEmpty())
        {
            return;
        }
        for (List<Block> chunk : splitAssistantGroup(group))
        {
            items.add(new AssistantItem(chunk, latestAssistantId));
        }
    }

    private Region buildAssistantFlow(AssistantItem item)
    {
        return buildAssistantFlow(item.blocks(), item.latestAssistantId(), null);
    }

    private Region buildStreamingAssistantFlow(StreamingAssistantItem item)
    {
        List<Block> blocks = new ArrayList<>(item.prefixBlocks());
        blocks.add(new Block(STREAMING_DRAFT_ID, "", Role.ASSISTANT, "", "", Integer.MAX_VALUE));
        return buildAssistantFlow(blocks, STREAMING_DRAFT_ID, item.textProperty());
    }

    private Region buildAssistantFlow(List<Block> blocks, String latestAssistantId, StringProperty draftText)
    {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(6);
        flow.prefWidthProperty().bind(contentWidthBinding().subtract(20));
        flow.maxWidthProperty().bind(contentWidthBinding().subtract(20));
        List<Path> hoverUnderlines = new ArrayList<>();
        List<Path> latestBackgrounds = new ArrayList<>();
        List<Runnable> decorationUpdates = new ArrayList<>();

        for (Block itemBlock : blocks)
        {
            Block block = currentBlock(itemBlock);
            boolean latest = block.id().equals(latestAssistantId);
            boolean draft = STREAMING_DRAFT_ID.equals(block.id());
            Text textNode = new Text();
            if (draft && draftText != null)
            {
                textNode.textProperty().bind(draftText);
            }
            else
            {
                textNode.setText(block.text());
            }
            textNode.getProperties().put(STORY_BLOCK_ID_KEY, block.id());
            applyAssistantTextStyle(textNode, latest);
            Path hoverUnderline = latest ? null : createDottedUnderline();
            Path latestBackground = latest ? createLatestBackground() : null;
            if (hoverUnderline != null)
            {
                hoverUnderlines.add(hoverUnderline);
                decorationUpdates.add(() -> updateUnderlineGeometry(flow, hoverUnderline, textNode));
            }
            if (latestBackground != null)
            {
                latestBackgrounds.add(latestBackground);
                decorationUpdates.add(() -> updateBackgroundGeometry(flow, latestBackground, textNode));
            }
            textNode.setOnMouseEntered(event ->
            {
                hideHoverUnderlines(hoverUnderlines);
                if (latest)
                {
                    textNode.setFill(Color.web("#cfc8bc"));
                    if (latestBackground != null)
                    {
                        latestBackground.setFill(Color.web("rgba(255,255,255,0.025)"));
                    }
                }
                else if (hoverUnderline != null)
                {
                    updateUnderlineGeometry(flow, hoverUnderline, textNode);
                    hoverUnderline.setVisible(!hoverUnderline.getElements().isEmpty());
                }
            });
            textNode.setOnMouseExited(event ->
            {
                textNode.setFill(Color.web(latest ? "#fffaf0" : "#e6e1d8"));
                if (hoverUnderline != null)
                {
                    hoverUnderline.setVisible(false);
                }
                if (latestBackground != null)
                {
                    latestBackground.setFill(Color.web("rgba(255,255,255,0.065)"));
                }
            });
            if (!draft)
            {
                textNode.setOnMousePressed(event ->
                {
                    event.consume();
                    beginBlockEdit(block, textNode);
                });
            }
            flow.getChildren().add(textNode);
        }
        flow.getChildren().add(new Text(""));

        StackPane wrapper = new StackPane();
        wrapper.setAlignment(Pos.TOP_LEFT);
        wrapper.setPadding(new Insets(0, 10, 0, 10));
        wrapper.prefWidthProperty().bind(contentWidthBinding());
        wrapper.maxWidthProperty().bind(contentWidthBinding());
        for (Path background : latestBackgrounds)
        {
            background.setLayoutX(10);
            wrapper.getChildren().add(background);
        }
        wrapper.getChildren().add(flow);
        for (Path underline : hoverUnderlines)
        {
            underline.setLayoutX(10);
            wrapper.getChildren().add(underline);
        }
        Runnable updateDecorations = () -> decorationUpdates.forEach(Runnable::run);
        wrapper.getProperties().put(DECORATION_UPDATE_KEY, updateDecorations);
        flow.widthProperty().addListener((obs, oldValue, newValue) -> Platform.runLater(updateDecorations));
        Platform.runLater(updateDecorations);
        return wrapper;
    }

    private Region buildAssistantEditingPane(AssistantItem item, String editedBlockId)
    {
        VBox editorPane = new VBox(6);
        editorPane.setPadding(new Insets(0, 10, 8, 10));
        editorPane.prefWidthProperty().bind(contentWidthBinding());
        editorPane.maxWidthProperty().bind(contentWidthBinding());

        List<Block> before = new ArrayList<>();
        List<Block> after = new ArrayList<>();
        Block edited = null;
        for (Block itemBlock : item.blocks())
        {
            Block block = currentBlock(itemBlock);
            if (block.id().equals(editedBlockId))
            {
                edited = block;
            }
            else if (edited == null)
            {
                before.add(block);
            }
            else
            {
                after.add(block);
            }
        }
        if (!before.isEmpty())
        {
            editorPane.getChildren().add(buildEditingContextFlow(before, item.latestAssistantId()));
        }
        if (edited != null)
        {
            editorPane.getChildren().add(blockEditor);
        }
        if (!after.isEmpty())
        {
            editorPane.getChildren().add(buildEditingContextFlow(after, item.latestAssistantId()));
        }
        return editorPane;
    }

    private TextFlow buildEditingContextFlow(List<Block> blocks, String latestAssistantId)
    {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(6);
        flow.prefWidthProperty().bind(contentWidthBinding().subtract(20));
        flow.maxWidthProperty().bind(contentWidthBinding().subtract(20));
        for (Block block : blocks)
        {
            Text text = new Text(block.text());
            text.getProperties().put(STORY_BLOCK_ID_KEY, block.id());
            applyAssistantTextStyle(text, block.id().equals(latestAssistantId));
            text.setOnMousePressed(event ->
            {
                event.consume();
                beginBlockEdit(block, text);
            });
            flow.getChildren().add(text);
        }
        return flow;
    }

    private static Path createDottedUnderline()
    {
        Path underline = new Path();
        underline.setManaged(false);
        underline.setMouseTransparent(true);
        underline.setFill(Color.TRANSPARENT);
        underline.setStroke(Color.web("#b8b1a5"));
        underline.setStrokeWidth(1);
        underline.getStrokeDashArray().setAll(1.5, 2.5);
        underline.setVisible(false);
        return underline;
    }

    private static Path createLatestBackground()
    {
        Path background = new Path();
        background.setManaged(false);
        background.setMouseTransparent(true);
        background.setFill(Color.web("rgba(255,255,255,0.065)"));
        background.setStroke(Color.TRANSPARENT);
        return background;
    }

    private static void hideHoverUnderlines(List<Path> underlines)
    {
        underlines.forEach(underline -> underline.setVisible(false));
    }

    private void updateUnderlineGeometry(TextFlow flow, Path underline, Text text)
    {
        flow.applyCss();
        flow.layout();
        int start = textOffset(flow, text);
        int end = start + text.getText().length();
        underline.getElements().setAll(flow.getUnderlineShape(start, end));
    }

    private void updateBackgroundGeometry(TextFlow flow, Path background, Text text)
    {
        flow.applyCss();
        flow.layout();
        int start = textOffset(flow, text);
        int end = start + text.getText().length();
        background.getElements().setAll(flow.getRangeShape(start, end, true));
    }

    private static int textOffset(TextFlow flow, Text target)
    {
        int offset = 0;
        for (Node child : flow.getChildren())
        {
            if (child == target)
            {
                break;
            }
            if (child instanceof Text text)
            {
                offset += text.getText().length();
            }
        }
        return offset;
    }

    private static void applyAssistantTextStyle(Text textNode, boolean latest)
    {
        textNode.setFill(Color.web(latest ? "#fffaf0" : "#e6e1d8"));
        textNode.setUnderline(latest);
    }

    private List<List<Block>> splitAssistantGroup(List<Block> group)
    {
        List<List<Block>> chunks = new ArrayList<>();
        List<Block> current = new ArrayList<>();
        int charCount = 0;
        for (int i = group.size() - 1; i >= 0; i--)
        {
            Block block = group.get(i);
            int blockChars = block.text() == null ? 0 : block.text().length();
            current.addFirst(block);
            charCount += blockChars;

            boolean softLimitReached = current.size() >= ASSISTANT_FLOW_CHUNK_BLOCK_LIMIT
                    || charCount >= ASSISTANT_FLOW_CHUNK_CHAR_LIMIT;
            if (i == 0)
            {
                continue;
            }
            Block previous = group.get(i - 1);
            // A separate TextFlow always begins a new visual line. Only split at a
            // newline that already exists in the manuscript so chunking can never
            // alter a partial-sentence continuation.
            if (softLimitReached && hasHardBreakBetween(previous, block))
            {
                chunks.addFirst(List.copyOf(current));
                current = new ArrayList<>();
                charCount = 0;
            }
        }
        if (!current.isEmpty())
        {
            chunks.addFirst(List.copyOf(current));
        }
        return chunks;
    }

    private static boolean hasHardBreakBetween(Block left, Block right)
    {
        String leftText = left == null ? "" : left.text();
        String rightText = right == null ? "" : right.text();
        return endsWithNewline(leftText) || startsWithNewline(rightText);
    }

    private static boolean endsWithNewline(String text)
    {
        return text != null && !text.isEmpty() && (text.endsWith("\n") || text.endsWith("\r"));
    }

    private static boolean startsWithNewline(String text)
    {
        return text != null && !text.isEmpty() && (text.startsWith("\n") || text.startsWith("\r"));
    }

    private static String findLatestAssistantId(List<Block> blocks)
    {
        for (int i = blocks.size() - 1; i >= 0; i--)
        {
            Block block = blocks.get(i);
            if (block.role() == Role.ASSISTANT)
            {
                return block.id();
            }
        }
        return null;
    }

    private TextArea createBlockEditor()
    {
        TextArea editor = new TextArea();
        editor.getStyleClass().add("story-block-editor");
        editor.setWrapText(true);
        editor.setPrefRowCount(1);
        editor.prefWidthProperty().bind(rowContentWidthBinding());
        editor.maxWidthProperty().bind(rowContentWidthBinding());
        editor.addEventFilter(ScrollEvent.SCROLL, event -> event.consume());

        boolean[] resizeQueued = { false };
        Runnable queueResize = () ->
        {
            if (resizeQueued[0])
            {
                return;
            }
            resizeQueued[0] = true;
            Platform.runLater(() ->
            {
                resizeQueued[0] = false;
                if (activeAssistantEditId == null)
                {
                    return;
                }
                resizeBlockEditorToContent(editor);
                queueActiveEditorAnchor();
            });
        };
        editor.textProperty().addListener((obs, oldValue, newValue) -> queueResize.run());
        editor.widthProperty().addListener((obs, oldValue, newValue) -> queueResize.run());
        editor.focusedProperty().addListener((obs, oldValue, newValue) ->
        {
            if (!newValue && activeAssistantEditId != null)
            {
                String editedBlockId = activeAssistantEditId;
                Platform.runLater(() ->
                {
                    if (!editor.isFocused() && editedBlockId.equals(activeAssistantEditId))
                    {
                        commitBlockEdit(editor.getText());
                    }
                });
            }
        });
        return editor;
    }

    private void beginBlockEdit(Block requestedBlock, Node clickedNode)
    {
        StoryCell targetCell = enclosingStoryCell(clickedNode);
        if (targetCell == null)
        {
            return;
        }
        boolean openedAtBottom = isStoryAtBottom();
        Bounds clickedBounds = clickedNode.localToScene(clickedNode.getBoundsInLocal());
        double requestedSceneY = clickedBounds == null ? Double.NaN : clickedBounds.getMinY();
        if (activeAssistantEditId != null)
        {
            commitBlockEdit(blockEditor.getText());
        }

        invalidateViewportPreservation();
        bottomScrollSequence++;
        Block block = currentBlock(requestedBlock);
        activeAssistantEditId = block.id();
        activeEditorCell = targetCell;
        activeEditorOpenedAtBottom = openedAtBottom;
        activeEditorRequestedSceneY = requestedSceneY;
        editorAnchorSequence++;
        blockEditor.setText(block.text());
        targetCell.showBlockEditor(block);
        resizeBlockEditorToContent(blockEditor);
        Platform.runLater(() ->
        {
            if (!block.id().equals(activeAssistantEditId))
            {
                return;
            }
            resizeBlockEditorToContent(blockEditor);
            blockEditor.requestFocus();
            blockEditor.positionCaret(blockEditor.getText().length());
            queueActiveEditorAnchor();
        });
    }

    private void commitBlockEdit(String newText)
    {
        if (activeAssistantEditId == null)
        {
            return;
        }
        String blockId = activeAssistantEditId;
        Block originalBlock = findBlockById(blockId);
        StoryCell editedCell = activeEditorCell;
        boolean restoreBottom = activeEditorOpenedAtBottom;
        discardActiveBlockEditor();
        if (originalBlock == null)
        {
            return;
        }
        String normalized = newText == null ? "" : newText;
        if (normalized.equals(originalBlock.text()))
        {
            relayoutAfterBlockEdit(restoreBottom);
            return;
        }

        updateBlockText(blockId, normalized);
        if (editedCell != null)
        {
            editedCell.updateDisplayedText(blockId, normalized);
        }
        relayoutAfterBlockEdit(restoreBottom);
        blockTextPersister.persist(blockId, normalized, () ->
        {
            clearStoryListSelection();
        }, e ->
        {
            updateBlockText(blockId, originalBlock.text());
            updateVisibleBlockText(blockId, originalBlock.text());
            clearStoryListSelection();
            errorHandler.accept("Failed to update block", e);
        });
    }

    private void resizeBlockEditorToContent(TextArea editor)
    {
        editor.applyCss();
        double availableWidth = editor.getWidth();
        if (availableWidth < 100)
        {
            availableWidth = rowContentWidthBinding().get();
        }
        double textWidth = Math.max(80, availableWidth - 28);
        String value = editor.getText();
        String measuredValue = value == null || value.isEmpty() ? " " : value;
        if (endsWithNewline(measuredValue))
        {
            measuredValue += " ";
        }
        Text measurement = new Text(measuredValue);
        measurement.setFont(editor.getFont());
        measurement.setWrappingWidth(textWidth);
        Text lineMeasurement = new Text("Ag");
        lineMeasurement.setFont(editor.getFont());
        double safetyLineHeight = Math.ceil(lineMeasurement.getLayoutBounds().getHeight());
        double targetHeight = Math.max(46,
                Math.ceil(measurement.getLayoutBounds().getHeight() + safetyLineHeight + 36));
        editor.setMinHeight(targetHeight);
        editor.setPrefHeight(targetHeight);
        editor.setMaxHeight(targetHeight);
    }

    private void discardActiveBlockEditor()
    {
        if (activeEditorCell != null)
        {
            activeEditorCell.hideBlockEditor();
        }
        activeAssistantEditId = null;
        activeEditorCell = null;
        activeEditorOpenedAtBottom = false;
        activeEditorRequestedSceneY = Double.NaN;
        editorAnchorSequence++;
    }

    private Region buildUserBlockNode(Block block)
    {
        Label icon = new Label(">");
        icon.setStyle("-fx-padding: 4 6 0 0;");
        icon.setTextOverrun(OverrunStyle.CLIP);
        icon.setMinWidth(Region.USE_PREF_SIZE);
        icon.setMaxWidth(Region.USE_PREF_SIZE);

        Block current = currentBlock(block);
        Label label = new Label(current.text());
        label.getProperties().put(STORY_BLOCK_ID_KEY, current.id());
        label.setWrapText(true);
        label.setTextOverrun(OverrunStyle.CLIP);
        label.setMinWidth(0);
        label.prefWidthProperty().bind(contentWidthBinding());
        label.maxWidthProperty().bind(contentWidthBinding());
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxHeight(Double.MAX_VALUE);
        StackPane textStack = new StackPane(label);
        textStack.prefWidthProperty().bind(rowContentWidthBinding());
        textStack.maxWidthProperty().bind(rowContentWidthBinding());
        HBox row = new HBox(6, icon, textStack);
        HBox.setHgrow(textStack, Priority.ALWAYS);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-border-color: rgba(255,255,255,0.25); -fx-border-width: 0 0 0 2;");
        row.prefWidthProperty().bind(contentWidthBinding());
        row.maxWidthProperty().bind(contentWidthBinding());

        label.setOnMousePressed(event ->
        {
            event.consume();
            beginBlockEdit(current, label);
        });
        label.setOnMouseEntered(event -> label.setUnderline(true));
        label.setOnMouseExited(event -> label.setUnderline(false));
        return row;
    }

    private Region buildUserEditingNode()
    {
        Label icon = new Label(">");
        icon.setStyle("-fx-padding: 4 6 0 0;");
        icon.setTextOverrun(OverrunStyle.CLIP);
        icon.setMinWidth(Region.USE_PREF_SIZE);
        icon.setMaxWidth(Region.USE_PREF_SIZE);

        StackPane textStack = new StackPane(blockEditor);
        textStack.prefWidthProperty().bind(rowContentWidthBinding());
        textStack.maxWidthProperty().bind(rowContentWidthBinding());
        HBox row = new HBox(6, icon, textStack);
        HBox.setHgrow(textStack, Priority.ALWAYS);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-border-color: rgba(255,255,255,0.25); -fx-border-width: 0 0 0 2;");
        row.prefWidthProperty().bind(contentWidthBinding());
        row.maxWidthProperty().bind(contentWidthBinding());
        return row;
    }

    private Region buildImageBlockNode(Block block)
    {
        VBox row = new VBox(8);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(8, 10, 8, 10));
        row.prefWidthProperty().bind(contentWidthBinding());
        row.maxWidthProperty().bind(contentWidthBinding());
        row.setStyle("-fx-border-color: rgba(255,255,255,0.15); -fx-border-width: 1; -fx-border-radius: 4; "
                + "-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 4;");

        Label header = new Label("Image");
        header.setStyle("-fx-text-fill: #c7c0b5; -fx-font-size: 12px;");
        StoryImage storyImage = imageLoader.apply(block.text());
        if (storyImage == null || storyImage.imageBytes() == null || storyImage.imageBytes().length == 0)
        {
            Label missing = new Label("[Missing image: " + (block.text() == null ? "" : block.text()) + "]");
            missing.setWrapText(true);
            row.getChildren().addAll(header, missing);
            return row;
        }

        Image image;
        try
        {
            image = new Image(new ByteArrayInputStream(storyImage.imageBytes()));
        }
        catch (Exception e)
        {
            Label failed = new Label("[Failed to decode image]");
            failed.setWrapText(true);
            row.getChildren().addAll(header, failed);
            return row;
        }

        ImageView preview = new ImageView(image);
        preview.setPreserveRatio(true);
        preview.setSmooth(true);
        preview.fitWidthProperty().bind(rowContentWidthBinding());
        preview.setFitHeight(256);
        preview.setOnMouseClicked(event -> StoryImageDialogs.showImageBlockDialog(
                owner, storyImage, image, () -> imageSaver.accept(storyImage),
                () -> blockDeletionHandler.delete(block, false)));
        StackPane previewWrap = new StackPane(preview);
        previewWrap.setAlignment(Pos.CENTER);
        previewWrap.prefWidthProperty().bind(rowContentWidthBinding());
        previewWrap.maxWidthProperty().bind(rowContentWidthBinding());

        Label promptSnippet = new Label(snippetFor(storyImage.prompt()));
        promptSnippet.setWrapText(true);
        promptSnippet.setStyle("-fx-text-fill: #b8b1a5; -fx-font-size: 11px;");
        row.getChildren().addAll(header, previewWrap, promptSnippet);
        return row;
    }

    private Block updateBlockText(String blockId, String text)
    {
        for (int i = 0; i < currentBlocks.size(); i++)
        {
            Block block = currentBlocks.get(i);
            if (!block.id().equals(blockId))
            {
                continue;
            }
            Block updated = new Block(block.id(), block.storyId(), block.role(), text, block.createdAt(),
                    block.position());
            currentBlocks.set(i, updated);
            return updated;
        }
        return null;
    }

    private Block findBlockById(String blockId)
    {
        for (Block block : currentBlocks)
        {
            if (block.id().equals(blockId))
            {
                return block;
            }
        }
        return null;
    }

    private Block currentBlock(Block fallback)
    {
        if (fallback == null)
        {
            return null;
        }
        Block current = findBlockById(fallback.id());
        return current == null ? fallback : current;
    }

    private StoryCell enclosingStoryCell(Node node)
    {
        Node current = node;
        while (current != null)
        {
            if (current instanceof StoryCell cell)
            {
                return cell;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean isDescendantOf(Node node, Node ancestor)
    {
        Node current = node;
        while (current != null)
        {
            if (current == ancestor)
            {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static boolean targetsStoryBlock(Node node)
    {
        Node current = node;
        while (current != null)
        {
            if (current.getProperties().containsKey(STORY_BLOCK_ID_KEY))
            {
                return true;
            }
            if (current instanceof ListCell<?>)
            {
                return false;
            }
            current = current.getParent();
        }
        return false;
    }

    private void updateVisibleBlockText(String blockId, String text)
    {
        for (Node node : storyListView.lookupAll(".list-cell"))
        {
            if (node instanceof StoryCell cell)
            {
                cell.updateDisplayedText(blockId, text);
            }
        }
        relayoutStoryPreserveViewport();
    }

    private static Node findDisplayedBlockNode(Node root, String blockId)
    {
        if (blockId.equals(root.getProperties().get(STORY_BLOCK_ID_KEY)))
        {
            return root;
        }
        if (root instanceof javafx.scene.Parent parent)
        {
            for (Node child : parent.getChildrenUnmodifiable())
            {
                Node match = findDisplayedBlockNode(child, blockId);
                if (match != null)
                {
                    return match;
                }
            }
        }
        return null;
    }

    private static String snippetFor(String text)
    {
        if (text == null || text.isBlank())
        {
            return "";
        }
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() <= 80 ? single : single.substring(0, 77) + "...";
    }

    private void scrollToBottom()
    {
        invalidateViewportPreservation();
        long request = ++bottomScrollSequence;
        Platform.runLater(() -> settleStoryBottom(request, 2));
    }

    private void settleStoryBottom(long request, int remainingPasses)
    {
        if (request != bottomScrollSequence || storyRows.isEmpty())
        {
            return;
        }

        suppressPostScrollLayout = true;
        try
        {
            int lastIndex = storyRows.size() - 1;
            VirtualFlow<?> flow = findStoryVirtualFlow();
            if (flow == null)
            {
                storyListView.scrollTo(lastIndex);
            }
            else
            {
                flow.scrollTo(lastIndex);
            }
            storyListView.applyCss();
            storyListView.layout();
            remeasureVisibleStoryCells();
            storyListView.requestLayout();
            storyListView.layout();
            flow = findStoryVirtualFlow();
            if (flow == null)
            {
                storyListView.scrollTo(lastIndex);
            }
            else
            {
                scrollFlowCellToBottom(flow, lastIndex);
            }
            storyListView.layout();
            hideStoryHorizontalScrollBar();
            ScrollBar bar = findStoryVerticalScrollBar();
            if (bar != null)
            {
                bar.setValue(bar.getMax());
            }
        }
        finally
        {
            suppressPostScrollLayout = false;
        }

        if (remainingPasses > 1)
        {
            Platform.runLater(() -> settleStoryBottom(request, remainingPasses - 1));
        }
    }

    private void relayoutAfterBlockEdit(boolean restoreBottom)
    {
        if (restoreBottom)
        {
            scrollToBottom();
        }
        else
        {
            relayoutStoryPreserveViewport();
        }
    }

    private void queueActiveEditorAnchor()
    {
        if (activeAssistantEditId == null || activeEditorCell == null)
        {
            return;
        }
        long request = editorAnchorSequence;
        if (queuedEditorAnchorSequence == request)
        {
            return;
        }
        queuedEditorAnchorSequence = request;
        Platform.runLater(() ->
        {
            if (queuedEditorAnchorSequence == request)
            {
                queuedEditorAnchorSequence = -1;
            }
            settleActiveEditorAnchor(request, 2);
        });
    }

    private void settleActiveEditorAnchor(long request, int remainingPasses)
    {
        if (request != editorAnchorSequence || activeAssistantEditId == null
                || activeEditorCell == null || blockEditor.getParent() == null)
        {
            return;
        }

        suppressPostScrollLayout = true;
        try
        {
            activeEditorCell.remeasure();
            storyListView.applyCss();
            storyListView.layout();

            Bounds viewport = storyListView.localToScene(storyListView.getBoundsInLocal());
            Bounds editor = blockEditor.localToScene(blockEditor.getBoundsInLocal());
            if (viewport == null || editor == null)
            {
                return;
            }

            double viewportTop = viewport.getMinY() + 8;
            double viewportBottom = viewport.getMaxY() - 8;
            double requestedTop = Double.isNaN(activeEditorRequestedSceneY)
                    ? viewportTop
                    : activeEditorRequestedSceneY;
            double targetTop = editor.getHeight() >= viewportBottom - viewportTop
                    ? viewportBottom - editor.getHeight()
                    : Math.max(viewportTop,
                            Math.min(requestedTop, viewportBottom - editor.getHeight()));
            double adjustment = editor.getMinY() - targetTop;
            VirtualFlow<?> flow = findStoryVirtualFlow();
            if (flow != null && Math.abs(adjustment) > 0.5)
            {
                flow.scrollPixels(adjustment);
                flow.layout();
            }
        }
        finally
        {
            suppressPostScrollLayout = false;
        }

        if (remainingPasses > 1)
        {
            Platform.runLater(() -> settleActiveEditorAnchor(request, remainingPasses - 1));
        }
    }

    private boolean isStoryAtBottom()
    {
        ScrollBar bar = findStoryVerticalScrollBar();
        return bar == null || bar.getValue() >= bar.getMax() - 0.01;
    }

    private void invalidateViewportPreservation()
    {
        viewportLayoutSequence++;
        storyViewportRefreshDebounce.stop();
    }

    private boolean shouldSchedulePostScrollLayout()
    {
        return !suppressPostScrollLayout && !streamingActive;
    }

    private void queueStreamingBottomFollow(double heightIncrease)
    {
        if (heightIncrease <= 0)
        {
            return;
        }
        pendingStreamingBottomAnchorPasses = Math.max(pendingStreamingBottomAnchorPasses, 2);
        streamingFlowLayoutPending = true;
        settleStreamingBottomFollow();
    }

    private void advanceStreamingBottomFollow()
    {
        settleStreamingBottomFollow();
    }

    private void settleStreamingBottomFollow()
    {
        if (!streamingActive || pendingStreamingBottomAnchorPasses <= 0
                || streamingDraftItem == null)
        {
            return;
        }

        VirtualFlow<?> flow = findStoryVirtualFlow();
        if (flow == null)
        {
            clearStreamingBottomFollow();
            return;
        }

        suppressPostScrollLayout = true;
        try
        {
            if (streamingFlowLayoutPending)
            {
                flow.requestLayout();
                flow.layout();
                streamingFlowLayoutPending = false;
            }

            int draftIndex = storyRows.indexOf(streamingDraftItem);
            if (draftIndex < 0)
            {
                clearStreamingBottomFollow();
                return;
            }

            IndexedCell<?> draftCell = flow.getVisibleCell(draftIndex);
            if (draftCell == null)
            {
                flow.scrollTo(draftIndex);
                flow.layout();
                draftCell = flow.getVisibleCell(draftIndex);
            }

            Node draftNode = draftCell == null
                    ? null
                    : findDisplayedBlockNode(draftCell, STREAMING_DRAFT_ID);
            Bounds viewport = flow.localToScene(flow.getBoundsInLocal());
            Bounds draft = draftNode == null ? null : draftNode.localToScene(draftNode.getBoundsInLocal());
            if (viewport == null || draft == null)
            {
                scrollFlowCellToBottom(flow, draftIndex);
            }
            else
            {
                double viewportTop = viewport.getMinY() + 8;
                double viewportBottom = viewport.getMaxY() - 8;
                double adjustment = 0;
                if (draft.getMaxY() > viewportBottom)
                {
                    adjustment = draft.getMaxY() - viewportBottom;
                }
                else if (draft.getMaxY() < viewportTop)
                {
                    adjustment = draft.getMaxY() - viewportBottom;
                }
                if (Math.abs(adjustment) > 0.5)
                {
                    flow.scrollPixels(adjustment);
                    flow.layout();
                }

                Bounds corrected = draftNode.localToScene(draftNode.getBoundsInLocal());
                if (corrected != null && corrected.getMaxY() > viewportBottom + 1)
                {
                    scrollFlowCellToBottom(flow, draftIndex);
                }
            }
            pendingStreamingBottomAnchorPasses--;
        }
        finally
        {
            suppressPostScrollLayout = false;
        }
    }

    private VirtualFlow<?> findStoryVirtualFlow()
    {
        for (Node node : storyListView.lookupAll(".virtual-flow"))
        {
            if (node instanceof VirtualFlow<?> flow)
            {
                return flow;
            }
        }
        return null;
    }

    private static <T extends IndexedCell<?>> void scrollFlowCellToBottom(VirtualFlow<T> flow, int index)
    {
        flow.scrollTo(index);
        flow.layout();
        T cell = flow.getVisibleCell(index);
        if (cell == null)
        {
            flow.setPosition(1.0);
            flow.layout();
            cell = flow.getVisibleCell(index);
        }
        if (cell != null)
        {
            flow.scrollToBottom(cell);
        }
        else
        {
            flow.setPosition(1.0);
        }
    }

    private void clearStreamingBottomFollow()
    {
        pendingStreamingBottomAnchorPasses = 0;
        streamingFlowLayoutPending = false;
        suppressPostScrollLayout = false;
    }

    private void clearStoryListSelection()
    {
        storyListView.getSelectionModel().clearSelection();
    }

    private void relayoutStoryPreserveViewport()
    {
        long request = ++viewportLayoutSequence;
        ScrollBar before = findStoryVerticalScrollBar();
        double previousValue = before == null ? Double.NaN : before.getValue();
        storyListView.requestLayout();
        Platform.runLater(() ->
        {
            if (streamingActive || request != viewportLayoutSequence)
            {
                return;
            }
            remeasureVisibleStoryCells();
            storyListView.applyCss();
            storyListView.layout();
            hideStoryHorizontalScrollBar();
            ScrollBar after = findStoryVerticalScrollBar();
            if (after == null || Double.isNaN(previousValue))
            {
                return;
            }
            after.setValue(Math.max(after.getMin(), Math.min(after.getMax(), previousValue)));
        });
    }

    private void remeasureVisibleStoryCells()
    {
        for (Node node : storyListView.lookupAll(".list-cell"))
        {
            if (node instanceof StoryCell cell)
            {
                cell.remeasure();
            }
        }
    }

    private void scheduleStoryViewportRefresh()
    {
        storyViewportRefreshDebounce.playFromStart();
    }

    private ScrollBar findStoryVerticalScrollBar()
    {
        for (Node node : storyListView.lookupAll(".scroll-bar"))
        {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL)
            {
                return bar;
            }
        }
        return null;
    }

    private void installStoryScrollListener()
    {
        ScrollBar current = findStoryVerticalScrollBar();
        if (current == observedStoryScrollBar)
        {
            return;
        }
        if (observedStoryScrollBar != null)
        {
            observedStoryScrollBar.valueProperty().removeListener(storyScrollListener);
        }
        observedStoryScrollBar = current;
        if (observedStoryScrollBar != null)
        {
            observedStoryScrollBar.valueProperty().addListener(storyScrollListener);
        }
    }

    private void schedulePostScrollLayout(double scrollValue)
    {
        requestedScrollValue = scrollValue;
        if (postScrollLayoutQueued)
        {
            return;
        }
        postScrollLayoutQueued = true;
        long request = viewportLayoutSequence;
        Platform.runLater(() ->
        {
            if (streamingActive || suppressPostScrollLayout || request != viewportLayoutSequence)
            {
                postScrollLayoutQueued = false;
                return;
            }
            remeasureVisibleStoryCells();
            storyListView.applyCss();
            storyListView.layout();
            hideStoryHorizontalScrollBar();
            if (observedStoryScrollBar != null)
            {
                double clamped = Math.max(observedStoryScrollBar.getMin(),
                        Math.min(observedStoryScrollBar.getMax(), requestedScrollValue));
                observedStoryScrollBar.setValue(clamped);
            }
            postScrollLayoutQueued = false;
        });
    }

    private void hideStoryHorizontalScrollBar()
    {
        for (Node node : storyListView.lookupAll(".scroll-bar"))
        {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.HORIZONTAL)
            {
                bar.setVisible(false);
                bar.setManaged(false);
                bar.setMinHeight(0);
                bar.setPrefHeight(0);
                bar.setMaxHeight(0);
            }
        }
    }

    private DoubleBinding contentWidthBinding()
    {
        return storyContentWidthBinding != null ? storyContentWidthBinding : storyListView.widthProperty().subtract(32);
    }

    private DoubleBinding rowContentWidthBinding()
    {
        return storyRowContentWidthBinding != null ? storyRowContentWidthBinding : contentWidthBinding().subtract(24);
    }

    private sealed interface StoryItem permits AssistantItem, StreamingAssistantItem, UserItem, ImageItem
    {
    }

    private record AssistantItem(List<Block> blocks, String latestAssistantId) implements StoryItem
    {
        private AssistantItem
        {
            blocks = List.copyOf(blocks);
        }
    }

    private static final class StreamingAssistantItem implements StoryItem
    {
        private final List<Block> prefixBlocks;
        private final StringProperty text = new SimpleStringProperty("");

        private StreamingAssistantItem(List<Block> prefixBlocks)
        {
            this.prefixBlocks = List.copyOf(prefixBlocks);
        }

        private List<Block> prefixBlocks()
        {
            return prefixBlocks;
        }

        private StringProperty textProperty()
        {
            return text;
        }

        private void setText(String value)
        {
            text.set(value == null ? "" : value);
        }
    }

    private record UserItem(Block block) implements StoryItem
    {
    }

    private record ImageItem(Block block) implements StoryItem
    {
    }

    private record StreamingSnapshot(StreamingMode mode, List<Block> originalBlocks, Block seed, String text)
    {
    }

    private final class StoryCell extends ListCell<StoryItem>
    {
        private final StackPane clippedGraphic = new StackPane();
        private final Rectangle graphicClip = new Rectangle();
        private Region displayGraphic;
        private Region editorGraphic;
        private StreamingAssistantItem observedStreamingItem;
        private ChangeListener<String> streamingTextListener;

        private StoryCell()
        {
            clippedGraphic.setAlignment(Pos.TOP_LEFT);
            clippedGraphic.setMinWidth(0);
            clippedGraphic.prefWidthProperty().bind(Bindings.max(0.0,
                    storyListView.widthProperty().subtract(18)));
            clippedGraphic.maxWidthProperty().bind(Bindings.max(0.0,
                    storyListView.widthProperty().subtract(18)));
            graphicClip.widthProperty().bind(clippedGraphic.widthProperty());
            graphicClip.heightProperty().bind(clippedGraphic.heightProperty());
            clippedGraphic.setClip(graphicClip);
        }

        @Override
        protected void updateItem(StoryItem item, boolean empty)
        {
            detachStreamingTextListener();
            super.updateItem(item, empty);
            setText(null);
            setPadding(Insets.EMPTY);
            setMinHeight(Region.USE_COMPUTED_SIZE);
            setPrefHeight(Region.USE_COMPUTED_SIZE);
            setMaxHeight(Region.USE_COMPUTED_SIZE);
            if (empty || item == null)
            {
                if (editorGraphic != null)
                {
                    detachBlockEditor();
                }
                clippedGraphic.getChildren().clear();
                displayGraphic = null;
                editorGraphic = null;
                setGraphic(null);
                return;
            }

            if (editorGraphic != null)
            {
                detachBlockEditor();
            }
            displayGraphic = switch (item)
            {
                case AssistantItem assistant -> buildAssistantFlow(assistant);
                case StreamingAssistantItem streaming -> buildStreamingAssistantFlow(streaming);
                case UserItem user -> buildUserBlockNode(currentBlock(user.block()));
                case ImageItem image -> buildImageBlockNode(image.block());
            };
            editorGraphic = null;
            clippedGraphic.getChildren().setAll(displayGraphic);
            setGraphic(clippedGraphic);
            if (item instanceof StreamingAssistantItem streaming)
            {
                observedStreamingItem = streaming;
                streamingTextListener = (observable, oldValue, newValue) -> refreshStreamingDraftLayout();
                streaming.textProperty().addListener(streamingTextListener);
            }
            Platform.runLater(() ->
            {
                if (getItem() == item)
                {
                    remeasure();
                }
            });
        }

        private void detachStreamingTextListener()
        {
            if (observedStreamingItem != null && streamingTextListener != null)
            {
                observedStreamingItem.textProperty().removeListener(streamingTextListener);
            }
            observedStreamingItem = null;
            streamingTextListener = null;
        }

        private void refreshStreamingDraftLayout()
        {
            if (!(getItem() instanceof StreamingAssistantItem) || displayGraphic == null)
            {
                return;
            }
            double previousHeight = getPrefHeight();
            double measuredHeight = remeasure(true);
            Object decorationUpdate = displayGraphic.getProperties().get(DECORATION_UPDATE_KEY);
            if (decorationUpdate instanceof Runnable update)
            {
                update.run();
            }
            if (!Double.isNaN(measuredHeight)
                    && previousHeight != Region.USE_COMPUTED_SIZE
                    && measuredHeight > previousHeight + 0.5)
            {
                queueStreamingBottomFollow(measuredHeight - previousHeight);
            }
        }

        private void showBlockEditor(Block block)
        {
            if (displayGraphic == null || getItem() == null)
            {
                return;
            }
            hideBlockEditor();
            editorGraphic = switch (getItem())
            {
                case AssistantItem assistant when assistant.blocks().stream()
                        .anyMatch(candidate -> candidate.id().equals(block.id())) ->
                        buildAssistantEditingPane(assistant, block.id());
                case UserItem user when user.block().id().equals(block.id()) -> buildUserEditingNode();
                default -> null;
            };
            if (editorGraphic == null)
            {
                return;
            }
            displayGraphic.setVisible(false);
            displayGraphic.setManaged(false);
            clippedGraphic.getChildren().add(editorGraphic);
            remeasure();
        }

        private void hideBlockEditor()
        {
            detachBlockEditor();
            if (editorGraphic != null)
            {
                clippedGraphic.getChildren().remove(editorGraphic);
                editorGraphic = null;
            }
            if (displayGraphic != null)
            {
                displayGraphic.setVisible(true);
                displayGraphic.setManaged(true);
            }
            remeasure();
        }

        private void detachBlockEditor()
        {
            if (blockEditor != null && blockEditor.getParent() instanceof Pane parent)
            {
                parent.getChildren().remove(blockEditor);
            }
        }

        private void updateDisplayedText(String blockId, String text)
        {
            if (displayGraphic == null)
            {
                return;
            }
            Node displayed = findDisplayedBlockNode(displayGraphic, blockId);
            if (displayed instanceof Text textNode)
            {
                textNode.setText(text);
            }
            else if (displayed instanceof Label label)
            {
                label.setText(text);
            }
            else
            {
                return;
            }
            Object decorationUpdate = displayGraphic.getProperties().get(DECORATION_UPDATE_KEY);
            if (decorationUpdate instanceof Runnable update)
            {
                Platform.runLater(update);
            }
            remeasure();
        }

        private double remeasure()
        {
            return remeasure(false);
        }

        private double remeasure(boolean avoidUnchangedCellLayout)
        {
            if (isEmpty() || getItem() == null)
            {
                return Double.NaN;
            }
            double availableWidth = Math.max(0, getWidth() > 0 ? getWidth() : storyListView.getWidth());
            clippedGraphic.setMinHeight(Region.USE_COMPUTED_SIZE);
            clippedGraphic.setPrefHeight(Region.USE_COMPUTED_SIZE);
            clippedGraphic.setMaxHeight(Region.USE_COMPUTED_SIZE);
            clippedGraphic.applyCss();
            double preferredHeight = clippedGraphic.prefHeight(availableWidth);
            clippedGraphic.resize(availableWidth, preferredHeight);
            clippedGraphic.requestLayout();
            clippedGraphic.layout();
            preferredHeight = clippedGraphic.prefHeight(availableWidth);
            if (preferredHeight <= 0)
            {
                return Double.NaN;
            }
            clippedGraphic.resize(availableWidth, preferredHeight);
            clippedGraphic.requestLayout();
            clippedGraphic.layout();
            double contentHeight = clippedGraphic.getChildren().stream()
                    .mapToDouble(child -> child.getBoundsInParent().getMaxY())
                    .max()
                    .orElse(preferredHeight);
            double cellHeight = Math.max(preferredHeight, contentHeight) + 12;
            clippedGraphic.resize(availableWidth, cellHeight);
            clippedGraphic.requestLayout();
            clippedGraphic.layout();
            boolean cellHeightChanged = getPrefHeight() == Region.USE_COMPUTED_SIZE
                    || Math.abs(getPrefHeight() - cellHeight) > 0.5;
            if (!avoidUnchangedCellLayout || cellHeightChanged)
            {
                setMinHeight(cellHeight);
                setPrefHeight(cellHeight);
                setMaxHeight(cellHeight);
                requestLayout();
            }
            return cellHeight;
        }
    }
}
