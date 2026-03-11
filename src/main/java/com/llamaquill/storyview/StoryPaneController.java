package com.llamaquill.storyview;

import com.llamaquill.generation.TurnInputPane;
import com.llamaquill.image.StoryImageDialogs;
import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import com.llamaquill.model.StoryImage;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
    private static final int ASSISTANT_FLOW_CHUNK_HARD_CHAR_LIMIT = 12000;
    private static final int ASSISTANT_FLOW_CHUNK_HARD_BLOCK_LIMIT = 96;

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

    private final Stage owner;
    private final Runnable onSubmitTurn;
    private final Function<String, StoryImage> imageLoader;
    private final Consumer<StoryImage> imageSaver;
    private final BlockDeletionHandler blockDeletionHandler;
    private final BlockTextPersister blockTextPersister;
    private final BiConsumer<String, Exception> errorHandler;

    private final ObservableList<Region> storyRows = FXCollections.observableArrayList();
    private final ListView<Region> storyListView = new ListView<>(storyRows);
    private final PauseTransition storyViewportRefreshDebounce = new PauseTransition(Duration.millis(120));
    private final TurnInputPane turnInputPane;

    private List<Block> currentBlocks = List.of();
    private HBox storyActionRow;
    private DoubleBinding storyContentWidthBinding;
    private DoubleBinding storyRowContentWidthBinding;
    private String activeAssistantEditId;
    private TextFlow activeAssistantFlow;
    private TextArea activeAssistantEditor;

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
        turnInputPane = new TurnInputPane(onSubmitTurn, () -> showTurnInput(false));
    }

    public BorderPane buildCenterPane(Button takeTurnButton, Button continueButton, Button seeButton, Button retryButton,
            Button retryHistoryButton, Button deleteButton, Button promptButton)
    {
        BorderPane centerPane = new BorderPane();
        centerPane.getStyleClass().add("center-pane");

        StackPane storyViewport = new StackPane(storyListView);
        Rectangle viewportClip = new Rectangle();
        viewportClip.widthProperty().bind(storyViewport.widthProperty());
        viewportClip.heightProperty().bind(storyViewport.heightProperty());
        storyViewport.setClip(viewportClip);
        centerPane.setCenter(storyViewport);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        storyActionRow = new HBox(8, takeTurnButton, continueButton, seeButton, retryButton, retryHistoryButton, deleteButton,
                spacer, promptButton);
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

    public void renderBlocks(List<Block> blocks, boolean forceScroll)
    {
        currentBlocks = blocks == null ? List.of() : blocks;
        storyRows.clear();
        if (currentBlocks.isEmpty())
        {
            return;
        }

        String latestAssistantId = findLatestAssistantId();
        List<Block> assistantGroup = new ArrayList<>();

        for (Block block : currentBlocks)
        {
            if (block.role() == Role.ASSISTANT)
            {
                assistantGroup.add(block);
                continue;
            }

            addAssistantGroup(assistantGroup, latestAssistantId);
            assistantGroup.clear();
            if (block.role() == Role.USER)
            {
                storyRows.add(buildUserBlockNode(block));
            }
            else if (block.role() == Role.IMAGE)
            {
                storyRows.add(buildImageBlockNode(block));
            }
        }

        addAssistantGroup(assistantGroup, latestAssistantId);
        storyListView.getSelectionModel().clearSelection();
        storyListView.getFocusModel().focus(-1);

        if (forceScroll)
        {
            scrollToBottom();
        }
    }

    private void initializeStoryListView()
    {
        storyListView.getStyleClass().add("story-list");
        storyListView.setFocusTraversable(false);
        storyListView.setPadding(new Insets(10, 0, 10, 0));
        storyListView.setCellFactory(list -> new ListCell<>()
        {
            private final StackPane clippedGraphic = new StackPane();
            private final Rectangle graphicClip = new Rectangle();

            {
                clippedGraphic.setAlignment(Pos.TOP_LEFT);
                graphicClip.widthProperty().bind(clippedGraphic.widthProperty());
                graphicClip.heightProperty().bind(clippedGraphic.heightProperty());
                clippedGraphic.setClip(graphicClip);
            }

            @Override
            protected void updateItem(Region item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null)
                {
                    clippedGraphic.getChildren().clear();
                    setGraphic(null);
                    setPadding(Insets.EMPTY);
                    setMinHeight(Region.USE_COMPUTED_SIZE);
                    setPrefHeight(Region.USE_COMPUTED_SIZE);
                    setMaxHeight(Region.USE_COMPUTED_SIZE);
                    return;
                }
                clippedGraphic.getChildren().setAll(item);
                setGraphic(clippedGraphic);
                setPadding(Insets.EMPTY);
                setMinHeight(Region.USE_COMPUTED_SIZE);
                setPrefHeight(Region.USE_COMPUTED_SIZE);
                setMaxHeight(Region.USE_COMPUTED_SIZE);
            }
        });

        storyContentWidthBinding = Bindings.max(0.0, storyListView.widthProperty().subtract(52));
        storyRowContentWidthBinding = Bindings.max(0.0, storyContentWidthBinding.subtract(24));
        storyViewportRefreshDebounce.setOnFinished(event -> refreshStoryLayoutPreserveViewport());
        storyListView.widthProperty().addListener((obs, oldValue, newValue) -> scheduleStoryViewportRefresh());
        storyListView.heightProperty().addListener((obs, oldValue, newValue) -> scheduleStoryViewportRefresh());
    }

    private void addAssistantGroup(List<Block> group, String latestAssistantId)
    {
        if (group.isEmpty())
        {
            return;
        }
        for (List<Block> chunk : splitAssistantGroup(group))
        {
            storyRows.add(buildAssistantFlow(chunk, latestAssistantId));
        }
    }

    private TextFlow buildAssistantFlow(List<Block> group, String latestAssistantId)
    {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(6);
        flow.setPadding(new Insets(2, 10, 2, 10));
        flow.prefWidthProperty().bind(contentWidthBinding());
        flow.maxWidthProperty().bind(contentWidthBinding());

        for (Block block : group)
        {
            boolean highlight = block.id().equals(latestAssistantId);
            Text textNode = new Text(block.text());
            textNode.setFill(Color.web("#e6e1d8"));
            if (highlight)
            {
                textNode.setStyle("-fx-underline: true;");
            }
            textNode.setOnMouseEntered(event -> textNode.setUnderline(true));
            textNode.setOnMouseExited(event ->
            {
                if (!highlight)
                {
                    textNode.setUnderline(false);
                }
            });
            textNode.setOnMouseClicked(event -> beginAssistantInlineEdit(block, flow, textNode));
            flow.getChildren().add(textNode);
        }
        Text sentinel = new Text("");
        sentinel.setUserData("sentinel");
        flow.getChildren().add(sentinel);
        return flow;
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
            current.add(0, block);
            charCount += blockChars;

            boolean softLimitReached = current.size() >= ASSISTANT_FLOW_CHUNK_BLOCK_LIMIT
                    || charCount >= ASSISTANT_FLOW_CHUNK_CHAR_LIMIT;
            boolean hardLimitReached = current.size() >= ASSISTANT_FLOW_CHUNK_HARD_BLOCK_LIMIT
                    || charCount >= ASSISTANT_FLOW_CHUNK_HARD_CHAR_LIMIT;
            boolean hasPrevious = i > 0;
            if (!hasPrevious)
            {
                continue;
            }

            Block previous = group.get(i - 1);
            boolean naturalBoundary = hasHardBreakBetween(previous, block);
            boolean shouldSplit = hardLimitReached || (softLimitReached && naturalBoundary);
            if (!shouldSplit)
            {
                continue;
            }
            chunks.add(0, current);
            current = new ArrayList<>();
            charCount = 0;
        }

        if (!current.isEmpty())
        {
            chunks.add(0, current);
        }
        return chunks;
    }

    private boolean hasHardBreakBetween(Block left, Block right)
    {
        String leftText = left == null ? "" : left.text();
        String rightText = right == null ? "" : right.text();
        return endsWithNewline(leftText) || startsWithNewline(rightText);
    }

    private boolean endsWithNewline(String text)
    {
        return text != null && !text.isEmpty() && (text.endsWith("\n") || text.endsWith("\r"));
    }

    private boolean startsWithNewline(String text)
    {
        return text != null && !text.isEmpty() && (text.startsWith("\n") || text.startsWith("\r"));
    }

    private String findLatestAssistantId()
    {
        for (int i = currentBlocks.size() - 1; i >= 0; i--)
        {
            Block block = currentBlocks.get(i);
            if (block.role() == Role.ASSISTANT)
            {
                return block.id();
            }
        }
        return null;
    }

    private void beginAssistantInlineEdit(Block block, TextFlow flow, Text textNode)
    {
        clearStoryListSelection();
        if (activeAssistantEditor != null)
        {
            commitAssistantEdit(activeAssistantEditor.getText());
        }
        activeAssistantEditId = block.id();
        activeAssistantFlow = flow;

        TextArea editor = new TextArea(block.text());
        editor.setWrapText(true);
        editor.setMinHeight(Region.USE_PREF_SIZE);
        editor.setMaxHeight(Double.MAX_VALUE);
        editor.prefWidthProperty().bind(contentWidthBinding());
        editor.maxWidthProperty().bind(contentWidthBinding());

        int index = flow.getChildren().indexOf(textNode);
        if (index < 0)
        {
            activeAssistantEditId = null;
            activeAssistantFlow = null;
            return;
        }
        flow.getChildren().set(index, editor);
        Platform.runLater(() ->
        {
            flow.requestLayout();
            refreshStoryRow(flow);
            refreshStoryLayoutPreserveViewport();
        });

        activeAssistantEditor = editor;

        editor.focusedProperty().addListener((obs, oldValue, newValue) ->
        {
            if (!newValue && activeAssistantEditor == editor)
            {
                commitAssistantEdit(editor.getText());
            }
        });

        Platform.runLater(() ->
        {
            editor.requestFocus();
            editor.positionCaret(editor.getText().length());
        });
    }

    private void commitAssistantEdit(String newText)
    {
        if (activeAssistantEditId == null || activeAssistantEditor == null || activeAssistantFlow == null)
        {
            return;
        }

        String blockId = activeAssistantEditId;
        TextArea editor = activeAssistantEditor;
        TextFlow flow = activeAssistantFlow;
        activeAssistantEditId = null;
        activeAssistantEditor = null;
        activeAssistantFlow = null;

        Block originalBlock = findBlockById(blockId);
        if (originalBlock == null)
        {
            return;
        }

        String normalized = newText == null ? "" : newText;
        if (normalized.equals(originalBlock.text()))
        {
            replaceAssistantEditorWithText(flow, editor, originalBlock, originalBlock.text());
            clearStoryListSelection();
            return;
        }

        editor.setDisable(true);
        blockTextPersister.persist(blockId, normalized, () ->
        {
            Block updatedBlock = updateBlockText(blockId, normalized);
            if (updatedBlock == null)
            {
                updatedBlock = originalBlock;
            }
            replaceAssistantEditorWithText(flow, editor, updatedBlock, normalized);
            clearStoryListSelection();
        }, e ->
        {
            replaceAssistantEditorWithText(flow, editor, originalBlock, originalBlock.text());
            clearStoryListSelection();
            errorHandler.accept("Failed to update block", e);
        });
    }

    private Region buildUserBlockNode(Block block)
    {
        Label icon = new Label(">");
        icon.setStyle("-fx-padding: 4 6 0 0;");
        icon.setTextOverrun(OverrunStyle.CLIP);
        icon.setMinWidth(Region.USE_PREF_SIZE);
        icon.setPrefWidth(Region.USE_COMPUTED_SIZE);
        icon.setMaxWidth(Region.USE_PREF_SIZE);

        Label label = new Label(block.text());
        label.setWrapText(true);
        label.setPadding(Insets.EMPTY);
        label.setTextOverrun(OverrunStyle.CLIP);
        label.setMinWidth(0);
        label.prefWidthProperty().bind(contentWidthBinding());
        label.maxWidthProperty().bind(contentWidthBinding());
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setPrefHeight(Region.USE_COMPUTED_SIZE);
        label.setMaxHeight(Double.MAX_VALUE);

        TextArea editor = new TextArea(block.text());
        editor.setWrapText(true);
        editor.setVisible(false);
        editor.setManaged(false);
        editor.setPrefRowCount(3);
        editor.setMinHeight(Region.USE_PREF_SIZE);
        editor.prefWidthProperty().bind(contentWidthBinding());
        editor.maxWidthProperty().bind(contentWidthBinding());

        StackPane textStack = new StackPane(label, editor);
        textStack.prefWidthProperty().bind(rowContentWidthBinding());
        textStack.maxWidthProperty().bind(rowContentWidthBinding());
        HBox row = new HBox(6, icon, textStack);
        HBox.setHgrow(textStack, Priority.ALWAYS);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-border-color: rgba(255,255,255,0.25); -fx-border-width: 0 0 0 2;");
        row.prefWidthProperty().bind(contentWidthBinding());
        row.maxWidthProperty().bind(contentWidthBinding());

        label.setOnMouseClicked(event -> beginInlineEdit(label, editor, row));
        label.setOnMouseEntered(event -> label.setUnderline(true));
        label.setOnMouseExited(event -> label.setUnderline(false));
        editor.focusedProperty().addListener((obs, oldValue, newValue) ->
        {
            if (!newValue)
            {
                commitInlineEdit(block.id(), editor.getText(), label, editor, row, false);
            }
        });
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
                owner,
                storyImage,
                image,
                () -> imageSaver.accept(storyImage),
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

    private void beginInlineEdit(Label label, TextArea editor, Region row)
    {
        clearStoryListSelection();
        String current = stripTrailingSpace(label.getText());
        editor.setText(current);
        label.setVisible(false);
        label.setManaged(false);
        editor.setVisible(true);
        editor.setManaged(true);
        refreshStoryRow(row);
        refreshStoryLayoutPreserveViewport();
        editor.requestFocus();
        editor.positionCaret(editor.getText().length());
    }

    private void commitInlineEdit(String blockId, String newText, Label label, TextArea editor, Region row,
            boolean trailingSpace)
    {
        String normalized = newText == null ? "" : newText;
        Block originalBlock = findBlockById(blockId);
        if (originalBlock == null)
        {
            label.setVisible(true);
            label.setManaged(true);
            editor.setVisible(false);
            editor.setManaged(false);
            clearStoryListSelection();
            return;
        }
        String previousDisplay = trailingSpace ? originalBlock.text() + " " : originalBlock.text();

        label.setText(trailingSpace ? normalized + " " : normalized);
        label.setVisible(true);
        label.setManaged(true);
        editor.setVisible(false);
        editor.setManaged(false);
        refreshStoryRow(row);
        refreshStoryLayoutPreserveViewport();

        if (normalized.equals(originalBlock.text()))
        {
            clearStoryListSelection();
            return;
        }

        blockTextPersister.persist(blockId, normalized,
                () ->
                {
                    updateBlockText(blockId, normalized);
                    clearStoryListSelection();
                },
                e ->
                {
                    label.setText(previousDisplay);
                    refreshStoryRow(row);
                    clearStoryListSelection();
                    errorHandler.accept("Failed to update block", e);
                });
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
            if (text.equals(block.text()))
            {
                return block;
            }
            Block updated = new Block(block.id(), block.storyId(), block.role(), text, block.createdAt(), block.position());
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

    private void replaceAssistantEditorWithText(TextFlow flow, TextArea editor, Block block, String text)
    {
        if (flow == null || editor == null || block == null)
        {
            return;
        }
        Text updatedText = new Text(text == null ? "" : text);
        updatedText.setFill(Color.web("#e6e1d8"));
        String latestId = findLatestAssistantId();
        boolean highlight = block.id().equals(latestId);
        if (highlight)
        {
            updatedText.setStyle("-fx-underline: true;");
        }
        updatedText.setOnMouseEntered(event -> updatedText.setUnderline(true));
        updatedText.setOnMouseExited(event ->
        {
            if (!highlight)
            {
                updatedText.setUnderline(false);
            }
        });
        updatedText.setOnMouseClicked(event -> beginAssistantInlineEdit(block, flow, updatedText));

        int index = flow.getChildren().indexOf(editor);
        if (index >= 0)
        {
            flow.getChildren().set(index, updatedText);
            flow.requestLayout();
            refreshStoryRow(flow);
            refreshStoryLayoutPreserveViewport();
        }
    }

    private static String stripTrailingSpace(String text)
    {
        if (text == null || text.isEmpty())
        {
            return "";
        }
        if (text.endsWith(" "))
        {
            return text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String snippetFor(String text)
    {
        if (text == null || text.isBlank())
        {
            return "";
        }
        String single = text.replaceAll("\\s+", " ").trim();
        if (single.length() <= 80)
        {
            return single;
        }
        return single.substring(0, 77) + "...";
    }

    private void scrollToBottom()
    {
        Platform.runLater(() ->
        {
            if (storyRows.isEmpty())
            {
                return;
            }
            storyListView.refresh();
            storyListView.applyCss();
            storyListView.layout();
            storyListView.scrollTo(storyRows.size() - 1);
            Platform.runLater(() ->
            {
                storyListView.refresh();
                storyListView.applyCss();
                storyListView.layout();
                for (Node node : storyListView.lookupAll(".scroll-bar"))
                {
                    if (!(node instanceof ScrollBar bar))
                    {
                        continue;
                    }
                    if (bar.getOrientation() == Orientation.HORIZONTAL)
                    {
                        bar.setVisible(false);
                        bar.setManaged(false);
                        bar.setMinHeight(0);
                        bar.setPrefHeight(0);
                        bar.setMaxHeight(0);
                        continue;
                    }
                    if (bar.getOrientation() == Orientation.VERTICAL)
                    {
                        bar.setValue(bar.getMax());
                    }
                }
            });
        });
    }

    private void clearStoryListSelection()
    {
        storyListView.getSelectionModel().clearSelection();
    }

    private void refreshStoryLayoutPreserveViewport()
    {
        ScrollBar before = findStoryVerticalScrollBar();
        double previousValue = before == null ? Double.NaN : before.getValue();
        boolean stickToBottom = before != null && previousValue >= (before.getMax() - 0.5);
        Platform.runLater(() ->
        {
            storyListView.refresh();
            storyListView.applyCss();
            storyListView.layout();
            if (Double.isNaN(previousValue))
            {
                return;
            }
            ScrollBar after = findStoryVerticalScrollBar();
            if (after == null)
            {
                return;
            }
            hideStoryHorizontalScrollBar();
            if (stickToBottom)
            {
                after.setValue(after.getMax());
            }
            else
            {
                double clamped = Math.max(after.getMin(), Math.min(after.getMax(), previousValue));
                after.setValue(clamped);
            }
        });
    }

    private void scheduleStoryViewportRefresh()
    {
        storyViewportRefreshDebounce.playFromStart();
    }

    private void refreshStoryRow(Region row)
    {
        int index = storyRows.indexOf(row);
        if (index >= 0)
        {
            storyRows.set(index, row);
        }
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

    private void hideStoryHorizontalScrollBar()
    {
        for (Node node : storyListView.lookupAll(".scroll-bar"))
        {
            if (!(node instanceof ScrollBar bar) || bar.getOrientation() != Orientation.HORIZONTAL)
            {
                continue;
            }
            bar.setVisible(false);
            bar.setManaged(false);
            bar.setMinHeight(0);
            bar.setPrefHeight(0);
            bar.setMaxHeight(0);
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
}
