package com.llamaquill;

import com.llamaquill.autocards.AutoCards;
import com.llamaquill.autocards.AutoCardsService;
import com.llamaquill.db.ImageRepository;
import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.db.AppSettingsRepository;
import com.llamaquill.db.ModelSettingsRepository;
import com.llamaquill.db.AppAutoCardsRepository;
import com.llamaquill.db.StoryAutoCardsRepository;
import com.llamaquill.db.ModelAutoCardsRepository;
import com.llamaquill.generation.GenerationCoordinator;
import com.llamaquill.image.ImageGenerationCoordinator;
import com.llamaquill.model.Block;
import com.llamaquill.model.AppSettings;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.ModelSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.model.StoryImage;
import com.llamaquill.model.AppAutoCardsSettings;
import com.llamaquill.model.StoryAutoCardsSettings;
import com.llamaquill.model.ModelAutoCardsSettings;
import com.llamaquill.imports.AIDungeonImports;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.serviceClients.ComfyUiClient;
import com.llamaquill.serviceClients.OllamaClient;
import com.llamaquill.settings.SettingsCoordinator;
import com.llamaquill.util.Ids;
import com.llamaquill.util.Timestamps;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Slider;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.stage.FileChooser;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public class App extends Application
{
    private static final String DEFAULT_SYSTEM_PROMPT = "You're a masterful storyteller and gamemaster. "
            + "Write in second person present tense (You are), crafting vivid, engaging narratives with authority and confidence.";

    private static final int MAX_STORY_LINES = 20;
    private static final int LEFT_SIDEBAR_WIDTH = 480;
    private static final int RIGHT_SIDEBAR_WIDTH = 480;

    private Connection connection;
    private StoryRepository storyRepository;
    private BlockRepository blockRepository;
    private StoryCardRepository cardRepository;
    private AppSettingsRepository appSettingsRepository;
    private ModelSettingsRepository modelSettingsRepository;
    private AppAutoCardsRepository appAutoCardsRepository;
    private StoryAutoCardsRepository storyAutoCardsRepository;
    private ModelAutoCardsRepository modelAutoCardsRepository;
    private PromptCompiler promptCompiler;
    private GenerationCoordinator generationCoordinator;
    private ImageGenerationCoordinator imageGenerationCoordinator;
    private AutoCardsService autoCardsService;
    private AIDungeonImports aiDungeonImports;
    private OllamaClient ollamaClient;
    private ComfyUiClient comfyUiClient;
    private AppSettings appSettings;
    private ModelSettings activeModelSettings;
    private GenerationSettings settings;
    private AppAutoCardsSettings appAutoCardsSettings;
    private StoryAutoCardsSettings storyAutoCardsSettings;
    private ModelAutoCardsSettings modelAutoCardsSettings;
    private ExecutorService executor;

    private Story activeStory;
    private List<Block> blocks = new ArrayList<>();

    private final ObservableList<Story> storyItems = FXCollections.observableArrayList();
    private final ObservableList<StoryCard> cardItems = FXCollections.observableArrayList();

    private Stage primaryStage;
    private ListView<Region> storyListView;
    private final ObservableList<Region> storyRows = FXCollections.observableArrayList();
    private Label statusLabel;
    private Button continueButton;
    private Button takeTurnButton;
    private Button retryButton;
    private Button deleteButton;
    private Button retryHistoryButton;
    private Button seeButton;
    private HBox storyActionRow;

    private Button newStoryButton;
    private Button importAdventureButton;
    private Button collapseLeftButton;
    private ListView<Story> storyList;
    private VBox leftSideBar;
    private Label storyHeader;

    private Button collapseRightButton;
    private VBox rightSidebar;
    private TabPane rightTabs;

    private TextArea systemPromptArea;
    private TextArea plotEssentialsArea;
    private TextArea authorNoteArea;

    private Button newCardButton;
    private Button generateCardButton;
    private Button autoCardsRunButton;
    private Button importCardsButton;
    private ListView<StoryCard> cardList;

    private CheckBox autoCardsEnabledBox;
    private Spinner<Integer> autoCardsCooldownSpinner;
    private Spinner<Integer> autoCardsMaxPerRunSpinner;
    private Spinner<Integer> autoCardsWindowSpinner;
    private CheckBox autoCardsUpdateExistingBox;
    private CheckBox autoCardsCreateNewBox;
    private CheckBox autoCardsPinNewBox;
    private CheckBox autoCardsPreviewBox;
    private Spinner<Integer> autoCardsLengthLimitSpinner;
    private CheckBox autoCardsSummarizeBox;
    private CheckBox autoCardsBulletedListsBox;

    private ComboBox<String> autoCardsCandidateSelectionMode;
    private ComboBox<String> autoCardsContextMode;

    private TextArea autoCardsCreatePrompt;
    private TextArea autoCardsUpdatePrompt;
    private TextArea autoCardsSummarizePrompt;
    private Spinner<Integer> autoCardsMaxTokensCreate;
    private Spinner<Integer> autoCardsMaxTokensUpdate;
    private Spinner<Integer> autoCardsMaxTokensSummarize;

    private boolean updatingAutoCardsControls;

    private TextArea turnInputArea;
    private VBox turnInputBox;
    private Button submitTurnButton;
    private Button cancelTurnButton;

    private final List<RetryHistoryEntry> retryHistory = new ArrayList<>();
    private int retryIndex = -1;
    private String activeAssistantEditId;
    private TextFlow activeAssistantFlow;
    private TextArea activeAssistantEditor;

    private Slider contextLimitSlider;
    private Slider responseLengthSlider;
    private Slider temperatureSlider;
    private Slider topKSlider;
    private Slider topPSlider;
    private Slider minPSlider;
    private Slider presencePenaltySlider;
    private Slider frequencyPenaltySlider;
    private Slider repetitionPenaltySlider;
    private Slider minStoryPercentSlider;
    private Spinner<Integer> storyCardLookbackSpinner;
    private Spinner<Integer> anPlacementSpinner;
    private TextField ollamaUrlField;
    private TextField comfyUiUrlField;
    private ComboBox<String> comfyWorkflowSelect;
    private Spinner<Integer> comfyWidthSpinner;
    private Spinner<Integer> comfyHeightSpinner;
    private Spinner<Integer> comfyBatchSizeSpinner;
    private ComboBox<String> modelSelect;
    private boolean updatingModelControls;

    private DoubleBinding storyContentWidthBinding;
    private DoubleBinding storyRowContentWidthBinding;
    private PauseTransition storyViewportRefreshDebounce;
    private List<String> comfyWorkflowNames = new ArrayList<>();

    private static final int ASSISTANT_FLOW_CHUNK_CHAR_LIMIT = 6000;
    private static final int ASSISTANT_FLOW_CHUNK_BLOCK_LIMIT = 48;
    private static final int ASSISTANT_FLOW_CHUNK_HARD_CHAR_LIMIT = 12000;
    private static final int ASSISTANT_FLOW_CHUNK_HARD_BLOCK_LIMIT = 96;
    private static final double TOKEN_SCALE_DEFAULT = 1.0;
    private static final double TOKEN_SCALE_MIN = 0.7;
    private static final double TOKEN_SCALE_MAX = 1.6;
    private static final double TOKEN_SCALE_ALPHA = 0.2;
    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    private final Map<String, AutoCardsRunState> autoCardsRunState = new HashMap<>();
    private final Map<String, Double> tokenScaleByModel = new HashMap<>();

    private static final String DEFAULT_SEE_REQUEST = "Generate an image prompt for the most recent scene in the story.";

    private static final class AutoCardsRunState
    {
        private final int assistantCount;

        private AutoCardsRunState(int assistantCount)
        {
            this.assistantCount = assistantCount;
        }
    }

    private static final class AutoCardsResult
    {
        private final int created;
        private final int updated;
        private final boolean ran;

        private AutoCardsResult(int created, int updated, boolean ran)
        {
            this.created = created;
            this.updated = updated;
            this.ran = ran;
        }
    }

    private sealed interface RetryHistoryEntry permits TextRetryHistoryEntry, ImageRetryHistoryEntry
    {
    }

    private static final class TextRetryHistoryEntry implements RetryHistoryEntry
    {
        private final String text;

        private TextRetryHistoryEntry(String text)
        {
            this.text = text == null ? "" : text;
        }
    }

    private static final class ImageRetryHistoryEntry implements RetryHistoryEntry
    {
        private final String prompt;
        private final byte[] bytes;
        private final String mimeType;
        private final String workflowJson;

        private ImageRetryHistoryEntry(String prompt, byte[] bytes, String mimeType, String workflowJson)
        {
            this.prompt = prompt == null ? "" : prompt;
            this.bytes = bytes;
            this.mimeType = mimeType == null ? "image/png" : mimeType;
            this.workflowJson = workflowJson == null ? "" : workflowJson;
        }
    }

    @Override
    public void start(Stage stage)
    {
        this.primaryStage = stage;
        try
        {
            connection = Database.open();
            Database.initialize(connection);
            storyRepository = new StoryRepository(connection);
            blockRepository = new BlockRepository(connection);
            cardRepository = new StoryCardRepository(connection);
            ImageRepository imageRepository = new ImageRepository(connection);
            appSettingsRepository = new AppSettingsRepository(connection);
            modelSettingsRepository = new ModelSettingsRepository(connection);
            appAutoCardsRepository = new AppAutoCardsRepository(connection);
            storyAutoCardsRepository = new StoryAutoCardsRepository(connection);
            modelAutoCardsRepository = new ModelAutoCardsRepository(connection);
            promptCompiler = new PromptCompiler();
            aiDungeonImports = new AIDungeonImports(storyRepository, blockRepository, cardRepository, DEFAULT_SYSTEM_PROMPT);
            ollamaClient = new OllamaClient();
            comfyUiClient = new ComfyUiClient();
            generationCoordinator = new GenerationCoordinator(blockRepository, storyRepository, cardRepository, promptCompiler,
                    ollamaClient);
            autoCardsService = new AutoCardsService(ollamaClient, promptCompiler);
            imageGenerationCoordinator = new ImageGenerationCoordinator(imageRepository, blockRepository, storyRepository,
                    cardRepository, autoCardsService, comfyUiClient);
            appSettings = loadOrCreateAppSettings();
            appAutoCardsSettings = loadOrCreateAppAutoCardsSettings();
            refreshComfyWorkflowNames();
            ensureValidComfyWorkflowSelection();
            ollamaClient.setHost(appSettings.ollamaUrl());
            comfyUiClient.setHost(appSettings.comfyUiUrl());
            syncModelsFromOllama();
            activeModelSettings = loadActiveModelSettings(appSettings.selectedModel());
            modelAutoCardsSettings = loadOrCreateModelAutoCardsSettings(activeModelSettings.modelName());
            ollamaClient.setModel(activeModelSettings.modelName());
            promptCompiler.setTokenEstimator(this::estimatePromptTokensCalibrated);
            settings = buildGenerationSettings();
            executor = Executors.newSingleThreadExecutor();

            activeStory = loadOrCreateStory();
            blocks = blockRepository.listForStory(activeStory.id());
            storyAutoCardsSettings = loadOrCreateStoryAutoCardsSettings(activeStory.id());
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Failed to initialize database", e);
        }

        storyListView = new ListView<>(storyRows);
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
        // Keep content narrower than the viewport so ListView never needs a horizontal bar.
        storyContentWidthBinding = Bindings.max(0.0, storyListView.widthProperty().subtract(52));
        storyRowContentWidthBinding = Bindings.max(0.0, storyContentWidthBinding.subtract(24));
        storyViewportRefreshDebounce = new PauseTransition(Duration.millis(120));
        storyViewportRefreshDebounce.setOnFinished(event -> refreshStoryLayoutPreserveViewport());
        storyListView.widthProperty().addListener((obs, oldValue, newValue) -> scheduleStoryViewportRefresh());
        storyListView.heightProperty().addListener((obs, oldValue, newValue) -> scheduleStoryViewportRefresh());

        continueButton = new Button("Continue");
        continueButton.setOnAction(event -> runContinue());

        takeTurnButton = new Button("Take A Turn");
        takeTurnButton.setOnAction(event -> showTurnInput(true));

        retryButton = new Button("Retry");
        retryButton.setOnAction(event -> runRetry());

        deleteButton = new Button("Erase");
        deleteButton.setOnAction(event -> deleteHeadBlock());

        retryHistoryButton = new Button("");
        retryHistoryButton.setOnAction(event -> showRetryDialog());
        updateRetryCountLabel();

        seeButton = new Button("See");
        seeButton.setOnAction(event -> showSeeDialog());

        statusLabel = new Label("Ready");

        var statusBar = new HBox(statusLabel);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(8, 12, 8, 12));

        var root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setLeft(buildStorySidebar());
        root.setCenter(buildCenterPane());
        root.setRight(buildRightSidebar());
        root.setBottom(statusBar);

        refreshStoryList(activeStory.id());
        refreshCardList(activeStory.id());
        populateStoryDetails(activeStory);
        updateAppAutoCardsControls();
        updateModelAutoCardsControls();
        updateStoryAutoCardsControls();
        renderStoryBlocks(true);
        setStoryDependentControlsEnabled(activeStory != null);

        var scene = new Scene(root, 1280, 720);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        stage.setTitle("LlamaQuill");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop()
    {
        if (executor != null)
        {
            executor.shutdownNow();
        }
        if (connection != null)
        {
            try
            {
                connection.close();
            }
            catch (SQLException ignored)
            {
                // Best effort on shutdown.
            }
        }
    }

    private VBox buildStorySidebar()
    {
        storyHeader = new Label("Stories");
        storyHeader.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        newStoryButton = new Button("New Story");
        newStoryButton.setMaxWidth(Double.MAX_VALUE);
        newStoryButton.setOnAction(event -> showNewStoryDialog());

        importAdventureButton = new Button("Import AI Dungeon Adventure");
        importAdventureButton.setMaxWidth(Double.MAX_VALUE);
        importAdventureButton.setOnAction(event -> showImportAdventureDialog());

        storyList = new ListView<>(storyItems);
        storyList.setCellFactory(list -> new ListCell<>()
        {
            @Override
            protected void updateItem(Story item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.title());
            }
        });
        storyList.setOnMouseClicked(event ->
        {
            if (event.getClickCount() == 1)
            {
                Story selected = storyList.getSelectionModel().getSelectedItem();
                if (selected != null)
                {
                    showStoryDialog(selected);
                }
            }
        });

        collapseLeftButton = new Button("<<");
        collapseLeftButton.setOnAction(event -> toggleSidebar());

        var headerRow = new HBox(8, storyHeader, collapseLeftButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(storyHeader, Priority.ALWAYS);

        leftSideBar = new VBox(8, headerRow, newStoryButton, storyList, importAdventureButton);
        leftSideBar.getStyleClass().add("sidebar");
        leftSideBar.setPadding(new Insets(10));
        leftSideBar.setPrefWidth(LEFT_SIDEBAR_WIDTH);
        leftSideBar.setMinWidth(200);

        VBox.setVgrow(storyList, Priority.ALWAYS);
        return leftSideBar;
    }

    private BorderPane buildCenterPane()
    {
        var centerPane = new BorderPane();
        centerPane.getStyleClass().add("center-pane");
        StackPane storyViewport = new StackPane(storyListView);
        Rectangle viewportClip = new Rectangle();
        viewportClip.widthProperty().bind(storyViewport.widthProperty());
        viewportClip.heightProperty().bind(storyViewport.heightProperty());
        storyViewport.setClip(viewportClip);
        centerPane.setCenter(storyViewport);

        turnInputArea = new TextArea();
        turnInputArea.setWrapText(true);
        turnInputArea.setPrefRowCount(4);

        submitTurnButton = new Button("Submit");
        submitTurnButton.setOnAction(event -> submitTurn());

        cancelTurnButton = new Button("Cancel");
        cancelTurnButton.setOnAction(event -> showTurnInput(false));

        var turnButtons = new HBox(8, submitTurnButton, cancelTurnButton);
        turnButtons.setAlignment(Pos.CENTER_RIGHT);

        turnInputBox = new VBox(6, new Label("Your turn"), turnInputArea, turnButtons);
        turnInputBox.setPadding(new Insets(10, 10, 0, 10));
        showTurnInput(false);

        storyActionRow = new HBox(8, takeTurnButton, continueButton, seeButton, retryButton, retryHistoryButton, deleteButton);
        storyActionRow.getStyleClass().add("action-row");
        storyActionRow.setAlignment(Pos.CENTER_LEFT);
        storyActionRow.setPadding(new Insets(10));

        var bottomBox = new VBox(8, turnInputBox, storyActionRow);
        centerPane.setBottom(bottomBox);

        return centerPane;
    }

    private void showTurnInput(boolean show)
    {
        turnInputBox.setVisible(show);
        turnInputBox.setManaged(show);
        if (show)
        {
            turnInputArea.requestFocus();
        }
    }

    private VBox buildRightSidebar()
    {
        collapseRightButton = new Button(">>");
        collapseRightButton.setOnAction(event -> toggleRightSidebar());

        rightTabs = new TabPane();
        rightTabs.getTabs().addAll(buildStoryTab(), buildStoryCardsTab(), buildOptionsTab());
        rightTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        rightTabs.skinProperty().addListener((obs, oldSkin, newSkin) ->
        {
            if (newSkin != null)
            {
                forceTabHeaderDark();
            }
        });

        Label rightHeader = new Label("Details");
        rightHeader.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        var headerRow = new HBox(8, collapseRightButton, rightHeader);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(rightHeader, Priority.ALWAYS);

        rightSidebar = new VBox(8, headerRow, rightTabs);
        rightSidebar.getStyleClass().add("sidebar");
        rightSidebar.setPadding(new Insets(10));
        rightSidebar.setPrefWidth(RIGHT_SIDEBAR_WIDTH);
        rightSidebar.setMinWidth(240);

        VBox.setVgrow(rightTabs, Priority.ALWAYS);
        return rightSidebar;
    }

    private Tab buildStoryTab()
    {
        systemPromptArea = buildStoryArea();
        plotEssentialsArea = buildStoryArea();
        authorNoteArea = buildStoryArea();

        attachSaveOnBlur(systemPromptArea);
        attachSaveOnBlur(plotEssentialsArea);
        attachSaveOnBlur(authorNoteArea);

        autoCardsEnabledBox = new CheckBox("Enable Automatic Story Cards");
        autoCardsEnabledBox.setOnAction(event -> updateStoryAutoCardsEnabled(autoCardsEnabledBox.isSelected()));

        autoCardsCreateNewBox = new CheckBox("Create new cards");
        autoCardsUpdateExistingBox = new CheckBox("Update existing cards");
        autoCardsPinNewBox = new CheckBox("Pin newly created cards");
        autoCardsPreviewBox = new CheckBox("Preview first");
        autoCardsCreateNewBox.setOnAction(event ->
            updateStoryAutoCardsCreateNew(autoCardsCreateNewBox.isSelected()));        
        autoCardsUpdateExistingBox.setOnAction(event ->
                updateStoryAutoCardsUpdateExisting(autoCardsUpdateExistingBox.isSelected()));
        autoCardsPinNewBox.setOnAction(event ->
                updateStoryAutoCardsPinNew(autoCardsPinNewBox.isSelected()));
        autoCardsPreviewBox.setOnAction(event ->
                updateStoryAutoCardsPreview(autoCardsPreviewBox.isSelected()));

        VBox autoCardsSection = new VBox(8,
                new Label("Auto Cards"),
                autoCardsEnabledBox,
                autoCardsCreateNewBox,                
                autoCardsUpdateExistingBox,
                autoCardsPinNewBox,
                autoCardsPreviewBox);

        VBox content = new VBox(10, new Label("System Prompt"), systemPromptArea, new Label("Plot Essentials"),
                plotEssentialsArea, new Label("Author's Note"), authorNoteArea, autoCardsSection);
        content.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        return new Tab("Story", scrollPane);
    }

    private Tab buildStoryCardsTab()
    {
        newCardButton = new Button("Create New Card");
        newCardButton.setMaxWidth(Double.MAX_VALUE);
        newCardButton.setOnAction(event -> showCardDialog(null));

        generateCardButton = new Button("Generate New Card");
        generateCardButton.setMaxWidth(Double.MAX_VALUE);
        generateCardButton.setOnAction(event -> showGenerateCardDialog());

        autoCardsRunButton = new Button("Run Auto Cards");
        autoCardsRunButton.setMaxWidth(Double.MAX_VALUE);
        autoCardsRunButton.setOnAction(event -> runAutoCardsManual());

        importCardsButton = new Button("Import AI Dungeon Cards");
        importCardsButton.setMaxWidth(Double.MAX_VALUE);
        importCardsButton.setOnAction(event -> showImportCardsDialog());

        cardList = new ListView<>(cardItems);
        cardList.setCellFactory(list -> new ListCell<>()
        {
            private final Label title = new Label();
            private final Label snippet = new Label();
            private final VBox box = new VBox(2, title, snippet);

            {
                snippet.setStyle("-fx-font-size: 11px;");
            }

            @Override
            protected void updateItem(StoryCard item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || item == null)
                {
                    setGraphic(null);
                }
                else
                {
                    title.setText(item.title());
                    snippet.setText(snippetFor(item.content()));
                    setGraphic(box);
                }
            }
        });
        cardList.setOnMouseClicked(event ->
        {
            if (event.getClickCount() == 1)
            {
                StoryCard selected = cardList.getSelectionModel().getSelectedItem();
                if (selected != null)
                {
                    showCardDialog(selected);
                }
            }
        });

        VBox content = new VBox(8, newCardButton, generateCardButton, cardList, autoCardsRunButton, importCardsButton);
        content.setPadding(new Insets(10));
        VBox.setVgrow(cardList, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        return new Tab("Story Cards", scrollPane);
    }

    private Tab buildOptionsTab()
    {
        VBox content = new VBox(12);
        content.setPadding(new Insets(10));

        ollamaUrlField = new TextField(appSettings.ollamaUrl());
        ollamaUrlField.setPromptText("Ollama URL");
        ollamaUrlField.focusedProperty().addListener((obs, oldValue, newValue) ->
        {
            if (!newValue)
            {
                updateOllamaUrl(ollamaUrlField.getText());
            }
        });

        comfyUiUrlField = new TextField(appSettings.comfyUiUrl());
        comfyUiUrlField.setPromptText("ComfyUI URL");
        comfyUiUrlField.focusedProperty().addListener((obs, oldValue, newValue) ->
        {
            if (!newValue)
            {
                updateComfyUiUrl(comfyUiUrlField.getText());
            }
        });

        comfyWorkflowSelect = new ComboBox<>();
        comfyWorkflowSelect.setMaxWidth(Double.MAX_VALUE);
        comfyWorkflowSelect.setItems(FXCollections.observableArrayList(comfyWorkflowNames));
        comfyWorkflowSelect.setValue(appSettings.comfyWorkflow());
        comfyWorkflowSelect.setOnAction(event ->
        {
            String selected = comfyWorkflowSelect.getValue();
            if (selected != null)
            {
                updateComfyWorkflow(selected);
            }
        });

        comfyWidthSpinner = buildSpinner(64, 4096, appSettings.comfyWidth());
        comfyHeightSpinner = buildSpinner(64, 4096, appSettings.comfyHeight());
        comfyBatchSizeSpinner = buildSpinner(1, 32, appSettings.comfyBatchSize());

        modelSelect = new ComboBox<>();
        modelSelect.setMaxWidth(Double.MAX_VALUE);
        refreshModelSelect();
        modelSelect.setOnAction(event ->
        {
            String selected = modelSelect.getValue();
            if (selected != null)
            {
                selectModel(selected);
            }
        });

        autoCardsCandidateSelectionMode = new ComboBox<>();
        autoCardsCandidateSelectionMode.setItems(FXCollections.observableArrayList(
                AutoCards.CANDIDATE_SELECTION_MODE_HEURISTICS,
                AutoCards.CANDIDATE_SELECTION_MODE_ASK_MODEL));
        autoCardsCandidateSelectionMode.setMaxWidth(Double.MAX_VALUE);
        autoCardsCandidateSelectionMode.setOnAction(event ->
        {
            if (autoCardsCandidateSelectionMode.getValue() != null)
            {
                updateAppAutoCardsCandidateSelectionMode(autoCardsCandidateSelectionMode.getValue());
            }
        });

        autoCardsContextMode = new ComboBox<>();
        autoCardsContextMode.setItems(FXCollections.observableArrayList(
                AutoCards.CONTEXT_MODE_WINDOWED_EXCERPT,
                AutoCards.CONTEXT_MODE_FULL_STORY));
        autoCardsContextMode.setMaxWidth(Double.MAX_VALUE);
        autoCardsContextMode.setOnAction(event ->
        {
            if (autoCardsContextMode.getValue() != null)
            {
                updateAppAutoCardsContextMode(autoCardsContextMode.getValue());
            }
        });

        autoCardsCooldownSpinner = buildSpinner(0, 100, appAutoCardsSettings.cooldownTurns());

        autoCardsMaxPerRunSpinner = buildSpinner(1, 10, appAutoCardsSettings.maxCardsPerRun());

        autoCardsWindowSpinner = buildSpinner(1, 50, appAutoCardsSettings.candidateWindow());

        autoCardsLengthLimitSpinner = buildSpinner(200, 10000, appAutoCardsSettings.cardLengthLimit());

        autoCardsSummarizeBox = new CheckBox("Summarize instead of trim");
        autoCardsSummarizeBox.setOnAction(event ->
                updateAppAutoCardsSummarize(autoCardsSummarizeBox.isSelected()));

        autoCardsBulletedListsBox = new CheckBox("Use Bulleted Lists");
        autoCardsBulletedListsBox.setOnAction(event ->
                updateAppAutoCardsUseBulletedLists(autoCardsBulletedListsBox.isSelected()));

        autoCardsCreatePrompt = buildStoryArea();
        autoCardsCreatePrompt.setMaxHeight(160);
        autoCardsUpdatePrompt = buildStoryArea();
        autoCardsUpdatePrompt.setMaxHeight(160);
        autoCardsSummarizePrompt = buildStoryArea();
        autoCardsSummarizePrompt.setMaxHeight(160);

        autoCardsCreatePrompt.focusedProperty().addListener((obs, oldValue, newValue) ->
        {
            if (!newValue)
            {
                updateModelAutoCardsPrompts();
            }
        });
        autoCardsUpdatePrompt.focusedProperty().addListener((obs, oldValue, newValue) ->
        {
            if (!newValue)
            {
                updateModelAutoCardsPrompts();
            }
        });
        autoCardsSummarizePrompt.focusedProperty().addListener((obs, oldValue, newValue) ->
        {
            if (!newValue)
            {
                updateModelAutoCardsPrompts();
            }
        });

        autoCardsMaxTokensCreate = buildSpinner(32, 1024, 256);
        autoCardsMaxTokensUpdate = buildSpinner(32, 1024, 192);
        autoCardsMaxTokensSummarize = buildSpinner(32, 1024, 192);
        autoCardsMaxTokensCreate.valueProperty().addListener((obs, oldValue, newValue) ->
                updateModelAutoCardsTokens());
        autoCardsMaxTokensUpdate.valueProperty().addListener((obs, oldValue, newValue) ->
                updateModelAutoCardsTokens());
        autoCardsMaxTokensSummarize.valueProperty().addListener((obs, oldValue, newValue) ->
                updateModelAutoCardsTokens());

        contextLimitSlider = buildIntSlider(1024, 32768, appSettings.contextLimit(), 512);
        responseLengthSlider = buildIntSlider(1, 250, appSettings.responseLength(), 1);
        temperatureSlider = buildDoubleSlider(0.1, 2.0, activeModelSettings.temperature(), 0.1);
        topKSlider = buildIntSlider(1, 999, activeModelSettings.topK(), 1);
        topPSlider = buildDoubleSlider(0.1, 1.0, activeModelSettings.topP(), 0.01);
        minPSlider = buildDoubleSlider(0.01, 0.2, activeModelSettings.minP(), 0.001);
        presencePenaltySlider = buildDoubleSlider(-2.0, 2.0, activeModelSettings.presencePenalty(), 0.01);
        frequencyPenaltySlider = buildDoubleSlider(-2.0, 2.0, activeModelSettings.frequencyPenalty(), 0.01);
        repetitionPenaltySlider = buildDoubleSlider(-2.0, 2.0, activeModelSettings.repetitionPenalty(), 0.01);
        minStoryPercentSlider = buildIntSlider(10, 100, percentFromSettings(), 1);
        storyCardLookbackSpinner = buildSpinner(0, 20, appSettings.storyCardLookback());
        anPlacementSpinner = buildSpinner(1, 10, appSettings.anPlacement());

        content.getChildren().addAll(textFieldRow("Ollama URL", ollamaUrlField),
                textFieldRow("ComfyUI URL", comfyUiUrlField),
                comboRow("ComfyUI Workflow", comfyWorkflowSelect),
                spinnerRow("Image Width", comfyWidthSpinner, this::updateComfyWidth),
                spinnerRow("Image Height", comfyHeightSpinner, this::updateComfyHeight),
                spinnerRow("Image Batch Size", comfyBatchSizeSpinner, this::updateComfyBatchSize),
                comboRow("Model", modelSelect),
                sliderRow("Context Limit", contextLimitSlider, valueLabel(appSettings.contextLimit(), "tokens"),
                        value -> updateContextLimit(value.intValue())),
                sliderRow("Response Length", responseLengthSlider, valueLabel(appSettings.responseLength(), "tokens"),
                        value -> updateResponseLength(value.intValue())),
                sliderRow("Temperature", temperatureSlider, valueLabel(activeModelSettings.temperature(), "", 2),
                        value -> updateTemperature(roundTo(value.doubleValue(), 0.1))),
                sliderRow("Top K", topKSlider, valueLabel(activeModelSettings.topK(), ""),
                        value -> updateTopK(value.intValue())),
                sliderRow("Top P", topPSlider, valueLabel(activeModelSettings.topP(), "", 2),
                        value -> updateTopP(roundTo(value.doubleValue(), 0.01))),
                sliderRow("Min P", minPSlider, valueLabel(activeModelSettings.minP(), "", 3),
                        value -> updateMinP(roundTo(value.doubleValue(), 0.001))),
                sliderRow("Presence Penalty", presencePenaltySlider, valueLabel(activeModelSettings.presencePenalty(), "", 2),
                        value -> updatePresencePenalty(roundTo(value.doubleValue(), 0.01))),
                sliderRow("Frequency Penalty", frequencyPenaltySlider,
                        valueLabel(activeModelSettings.frequencyPenalty(), "", 2),
                        value -> updateFrequencyPenalty(roundTo(value.doubleValue(), 0.01))),
                sliderRow("Repetition Penalty", repetitionPenaltySlider,
                        valueLabel(activeModelSettings.repetitionPenalty(), "", 2),
                        value -> updateRepetitionPenalty(roundTo(value.doubleValue(), 0.01))),
                sliderRow("Context to Use for Story", minStoryPercentSlider, valueLabel(percentFromSettings(), "%"),
                        value -> updateMinStoryPercent(value.intValue())),
                spinnerRow("Story Card Look Back", storyCardLookbackSpinner, this::updateStoryCardLookback),
                spinnerRow("Author's Note Insertion Point", anPlacementSpinner, this::updateAnPlacement),
                new Label("Auto Cards (Global)"),
                comboRow("Candidation Selection Mode", autoCardsCandidateSelectionMode),
                comboRow("Context Mode", autoCardsContextMode),
                spinnerRow("Cooldown (turns)", autoCardsCooldownSpinner, this::updateAppAutoCardsCooldown),
                spinnerRow("Max cards per run", autoCardsMaxPerRunSpinner, this::updateAppAutoCardsMaxPerRun),
                spinnerRow("Candidate window (blocks)", autoCardsWindowSpinner, this::updateAppAutoCardsWindow),
                spinnerRow("Card length limit (chars)", autoCardsLengthLimitSpinner, this::updateAppAutoCardsLengthLimit),
                autoCardsSummarizeBox,
                autoCardsBulletedListsBox,
                new Label("Auto Cards (Model)"),
                new Label("Create Prompt"),
                autoCardsCreatePrompt,
                new Label("Update Prompt"),
                autoCardsUpdatePrompt,
                new Label("Summarize Prompt"),
                autoCardsSummarizePrompt,
                spinnerRow("Max tokens (create)", autoCardsMaxTokensCreate, value -> updateModelAutoCardsTokens()),
                spinnerRow("Max tokens (update)", autoCardsMaxTokensUpdate, value -> updateModelAutoCardsTokens()),
                spinnerRow("Max tokens (summarize)", autoCardsMaxTokensSummarize, value -> updateModelAutoCardsTokens()));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        return new Tab("Options", scrollPane);
    }

    private void forceTabHeaderDark()
    {
        javafx.application.Platform.runLater(() ->
        {
            var header = rightTabs.lookup(".tab-header-background");
            if (header != null)
            {
                header.setStyle("-fx-background-color: #242424;");
            }
        });
    }

    private TextArea buildStoryArea()
    {
        TextArea area = new TextArea();
        area.setWrapText(true);
        area.setPrefRowCount(8);
        area.setMinHeight(Region.USE_PREF_SIZE);
        area.setMaxHeight(MAX_STORY_LINES * 18.0);
        return area;
    }

    private void attachSaveOnBlur(TextArea area)
    {
        area.focusedProperty().addListener((obs, oldValue, newValue) ->
        {
            if (!newValue)
            {
                saveStoryDetails();
            }
        });
    }

    private void toggleSidebar()
    {
        boolean collapsing = storyList.isVisible();
        storyList.setVisible(!collapsing);
        storyList.setManaged(!collapsing);
        newStoryButton.setVisible(!collapsing);
        newStoryButton.setManaged(!collapsing);
        storyHeader.setVisible(!collapsing);
        storyHeader.setManaged(!collapsing);
        importAdventureButton.setVisible(!collapsing);
        importAdventureButton.setManaged(!collapsing);

        if (collapsing)
        {
            leftSideBar.setPrefWidth(48);
            leftSideBar.setMinWidth(48);
            collapseLeftButton.setText(">>");
        }
        else
        {
            leftSideBar.setPrefWidth(LEFT_SIDEBAR_WIDTH);
            leftSideBar.setMinWidth(200);
            collapseLeftButton.setText("<<");
        }
    }

    private void toggleRightSidebar()
    {
        boolean collapsing = rightTabs.isVisible();
        rightTabs.setVisible(!collapsing);
        rightTabs.setManaged(!collapsing);

        if (collapsing)
        {
            rightSidebar.setPrefWidth(48);
            rightSidebar.setMinWidth(48);
            collapseRightButton.setText("<<");
        }
        else
        {
            rightSidebar.setPrefWidth(RIGHT_SIDEBAR_WIDTH);
            rightSidebar.setMinWidth(240);
            collapseRightButton.setText(">>");
        }
    }

    private Story loadOrCreateStory() throws SQLException
    {
        List<Story> stories = storyRepository.listAll();
        if (!stories.isEmpty())
        {
            return stories.getFirst();
        }
        String now = Timestamps.now();
        Story story = new Story(Ids.newId(), "Untitled Story", DEFAULT_SYSTEM_PROMPT, "", "", now, now);
        storyRepository.insert(story);
        return story;
    }

    private AppSettings loadOrCreateAppSettings() throws SQLException
    {
        Optional<AppSettings> current = appSettingsRepository.load();
        if (current.isPresent())
        {
            return current.get();
        }
        AppSettings defaults = appSettingsRepository.defaults();
        try
        {
            appSettingsRepository.save(defaults);
        }
        catch (SQLException e)
        {
            showError("Failed to save default settings", e);
        }
        return defaults;
    }

    private AppAutoCardsSettings loadOrCreateAppAutoCardsSettings() throws SQLException
    {
        Optional<AppAutoCardsSettings> current = appAutoCardsRepository.load();
        if (current.isPresent())
        {
            return current.get();
        }
        AppAutoCardsSettings defaults = AppAutoCardsSettings.defaults();
        appAutoCardsRepository.save(defaults);
        return defaults;
    }

    private StoryAutoCardsSettings loadOrCreateStoryAutoCardsSettings(String storyId) throws SQLException
    {
        Optional<StoryAutoCardsSettings> current = storyAutoCardsRepository.load(storyId);
        if (current.isPresent())
        {
            return current.get();
        }
        StoryAutoCardsSettings defaults = StoryAutoCardsSettings.defaults(storyId);
        storyAutoCardsRepository.save(defaults);
        return defaults;
    }

    private ModelAutoCardsSettings loadOrCreateModelAutoCardsSettings(String modelName) throws SQLException
    {
        Optional<ModelAutoCardsSettings> current = modelAutoCardsRepository.load(modelName);
        if (current.isPresent())
        {
            return current.get();
        }
        ModelAutoCardsSettings defaults = ModelAutoCardsSettings.defaults(modelName);
        modelAutoCardsRepository.save(defaults);
        return defaults;
    }

    private void syncModelsFromOllama()
    {
        try
        {
            List<String> models = ollamaClient.listModels();
            modelSettingsRepository.syncWithModels(models, ModelSettings.defaults(OllamaClient.DEFAULT_MODEL));
        }
        catch (Exception e)
        {
            // If Ollama is unavailable, keep existing model settings.
        }
    }

    private ModelSettings loadActiveModelSettings(String modelName) throws SQLException
    {
        Optional<ModelSettings> selected = modelSettingsRepository.load(modelName);
        if (selected.isPresent() && selected.get().active())
        {
            return selected.get();
        }

        List<ModelSettings> activeModels = modelSettingsRepository.listActive();
        if (!activeModels.isEmpty())
        {
            ModelSettings fallback = activeModels.get(0);
            appSettings = SettingsCoordinator.withSelectedModel(appSettings, fallback.modelName());
            persistAppSettings();
            return fallback;
        }

        ModelSettings defaults = ModelSettings.defaults(modelName);
        modelSettingsRepository.save(defaults);
        return defaults;
    }

    private GenerationSettings buildGenerationSettings()
    {
        return new GenerationSettings(appSettings.contextLimit(), appSettings.responseLength(),
                activeModelSettings.temperature(), activeModelSettings.topK(), activeModelSettings.topP(),
                activeModelSettings.minP(), activeModelSettings.presencePenalty(), activeModelSettings.frequencyPenalty(),
                activeModelSettings.repetitionPenalty(), appSettings.minStoryWindow(), appSettings.storyCardLookback(),
                appSettings.anPlacement());
    }

    private void refreshModelSelect()
    {
        if (modelSelect == null)
        {
            return;
        }
        try
        {
            List<ModelSettings> activeModels = modelSettingsRepository.listActive();
            List<String> names = new ArrayList<>();
            for (ModelSettings model : activeModels)
            {
                names.add(model.modelName());
            }
            modelSelect.setItems(FXCollections.observableArrayList(names));
            modelSelect.setValue(appSettings.selectedModel());
        }
        catch (SQLException e)
        {
            showError("Failed to load models", e);
        }
    }

    private void selectModel(String modelName)
    {
        if (modelName == null || modelName.isBlank())
        {
            return;
        }
        try
        {
            Optional<ModelSettings> selected = modelSettingsRepository.load(modelName);
            if (selected.isEmpty())
            {
                return;
            }
            activeModelSettings = selected.get();
            modelAutoCardsSettings = loadOrCreateModelAutoCardsSettings(modelName);
            appSettings = SettingsCoordinator.withSelectedModel(appSettings, modelName);
            persistAppSettings();
            updateModelControls();
            updateModelAutoCardsControls();
            refreshGenerationSettings();
            ollamaClient.setModel(modelName);
        }
        catch (SQLException e)
        {
            showError("Failed to select model", e);
        }
    }

    private void updateOllamaUrl(String url)
    {
        String normalized = url == null ? "" : url.trim();
        if (normalized.isBlank())
        {
            if (ollamaUrlField != null)
            {
                ollamaUrlField.setText(appSettings.ollamaUrl());
            }
            return;
        }
        if (normalized.equals(appSettings.ollamaUrl()))
        {
            if (ollamaUrlField != null)
            {
                ollamaUrlField.setText(normalized);
            }
            return;
        }
        appSettings = SettingsCoordinator.withOllamaUrl(appSettings, normalized);
        persistAppSettings();
        ollamaClient.setHost(appSettings.ollamaUrl());
        if (ollamaUrlField != null)
        {
            ollamaUrlField.setText(appSettings.ollamaUrl());
        }
    }

    private void updateComfyUiUrl(String url)
    {
        String normalized = url == null ? "" : url.trim();
        if (normalized.isBlank())
        {
            if (comfyUiUrlField != null)
            {
                comfyUiUrlField.setText(appSettings.comfyUiUrl());
            }
            return;
        }
        if (normalized.equals(appSettings.comfyUiUrl()))
        {
            if (comfyUiUrlField != null)
            {
                comfyUiUrlField.setText(normalized);
            }
            return;
        }
        appSettings = SettingsCoordinator.withComfyUiUrl(appSettings, normalized);
        persistAppSettings();
        comfyUiClient.setHost(appSettings.comfyUiUrl());
        if (comfyUiUrlField != null)
        {
            comfyUiUrlField.setText(appSettings.comfyUiUrl());
        }
    }

    private void updateComfyWorkflow(String workflowName)
    {
        String normalized = workflowName == null ? "" : workflowName.trim();
        if (normalized.isBlank())
        {
            if (comfyWorkflowSelect != null)
            {
                comfyWorkflowSelect.setValue(appSettings.comfyWorkflow());
            }
            return;
        }
        if (normalized.equals(appSettings.comfyWorkflow()))
        {
            return;
        }
        appSettings = SettingsCoordinator.withComfyWorkflow(appSettings, normalized);
        persistAppSettings();
    }

    private void updateComfyWidth(int value)
    {
        if (value == appSettings.comfyWidth())
        {
            return;
        }
        appSettings = SettingsCoordinator.withComfyWidth(appSettings, value);
        persistAppSettings();
    }

    private void updateComfyHeight(int value)
    {
        if (value == appSettings.comfyHeight())
        {
            return;
        }
        appSettings = SettingsCoordinator.withComfyHeight(appSettings, value);
        persistAppSettings();
    }

    private void updateComfyBatchSize(int value)
    {
        if (value == appSettings.comfyBatchSize())
        {
            return;
        }
        appSettings = SettingsCoordinator.withComfyBatchSize(appSettings, value);
        persistAppSettings();
    }

    private void refreshComfyWorkflowNames()
    {
        List<String> names = discoverComfyWorkflowNames();
        if (names.isEmpty())
        {
            names = List.of(AppSettings.DEFAULT_COMFY_WORKFLOW);
        }
        comfyWorkflowNames = names;
    }

    private void ensureValidComfyWorkflowSelection()
    {
        if (comfyWorkflowNames.isEmpty())
        {
            return;
        }
        String current = appSettings.comfyWorkflow();
        if (current != null && comfyWorkflowNames.contains(current))
        {
            return;
        }
        appSettings = SettingsCoordinator.withComfyWorkflow(appSettings, comfyWorkflowNames.getFirst());
        persistAppSettings();
    }

    private List<String> discoverComfyWorkflowNames()
    {
        List<String> names = new ArrayList<>();
        try
        {
            Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources("comfyui");
            while (urls.hasMoreElements())
            {
                URL url = urls.nextElement();
                names.addAll(listWorkflowNamesFromResourceRoot(url));
            }
        }
        catch (IOException e)
        {
            // Best effort discovery; defaults will still work.
        }
        names = new ArrayList<>(new java.util.LinkedHashSet<>(names));
        Collections.sort(names);
        return names;
    }

    private List<String> listWorkflowNamesFromResourceRoot(URL url)
    {
        if (url == null)
        {
            return List.of();
        }
        String protocol = url.getProtocol();
        if ("file".equalsIgnoreCase(protocol))
        {
            try
            {
                return listWorkflowNamesInDirectory(Path.of(url.toURI()));
            }
            catch (URISyntaxException e)
            {
                return List.of();
            }
        }
        if ("jar".equalsIgnoreCase(protocol))
        {
            String external = url.toExternalForm();
            int sep = external.indexOf("!/");
            if (sep < 0)
            {
                return List.of();
            }
            URI jarUri = URI.create(external.substring(0, sep));
            try
            {
                FileSystem existing = FileSystems.getFileSystem(jarUri);
                return listWorkflowNamesInDirectory(existing.getPath("/comfyui"));
            }
            catch (FileSystemNotFoundException ignored)
            {
                try
                {
                    try (FileSystem created = FileSystems.newFileSystem(jarUri, Map.of()))
                    {
                        return listWorkflowNamesInDirectory(created.getPath("/comfyui"));
                    }
                }
                catch (Exception e)
                {
                    return List.of();
                }
            }
            catch (Exception e)
            {
                return List.of();
            }
        }
        return List.of();
    }

    private List<String> listWorkflowNamesInDirectory(Path dir)
    {
        if (dir == null || !Files.exists(dir))
        {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(dir))
        {
            stream.filter(path -> Files.isRegularFile(path))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .forEach(names::add);
        }
        catch (IOException e)
        {
            return List.of();
        }
        return names;
    }

    private int estimatePromptTokensCalibrated(String prompt)
    {
        if (prompt == null || prompt.isBlank())
        {
            return 0;
        }
        double heuristic = Math.max(1.0, Math.ceil(prompt.length() / (double) CHARS_PER_TOKEN_ESTIMATE));
        double scale = currentTokenScale();
        return Math.max(1, (int) Math.ceil(heuristic * scale));
    }

    private synchronized double currentTokenScale()
    {
        String modelName = activeModelSettings == null ? ollamaClient.getModel() : activeModelSettings.modelName();
        if (modelName == null || modelName.isBlank())
        {
            modelName = "__default__";
        }
        return tokenScaleByModel.getOrDefault(modelName, TOKEN_SCALE_DEFAULT);
    }

    private void observePromptCalibration(int estimatedPromptTokens)
    {
        int actualPromptTokens = ollamaClient.getLastPromptEvalCount();
        if (estimatedPromptTokens <= 0 || actualPromptTokens <= 0)
        {
            return;
        }
        synchronized (this)
        {
            String modelName = activeModelSettings == null ? ollamaClient.getModel() : activeModelSettings.modelName();
            if (modelName == null || modelName.isBlank())
            {
                modelName = "__default__";
            }

            double oldScale = tokenScaleByModel.getOrDefault(modelName, TOKEN_SCALE_DEFAULT);
            double sampleRatio = actualPromptTokens / (double) estimatedPromptTokens;
            double targetScale = oldScale * sampleRatio;
            targetScale = Math.max(TOKEN_SCALE_MIN, Math.min(TOKEN_SCALE_MAX, targetScale));
            double updated = oldScale + (targetScale - oldScale) * TOKEN_SCALE_ALPHA;
            updated = Math.max(TOKEN_SCALE_MIN, Math.min(TOKEN_SCALE_MAX, updated));
            tokenScaleByModel.put(modelName, updated);
        }
    }

    private boolean runAutoCardsForGeneration(List<Block> currentBlocks, List<StoryCard> currentCards) throws Exception
    {
        try
        {
            return runAutoCardsIfNeeded(currentBlocks, currentCards, false).ran;
        }
        catch (Exception e)
        {
            logAutoCardsError("Auto Cards failed to run", e);
            return false;
        }
    }

    private void setStoryActionButtonsBusy(boolean busy)
    {
        if (storyActionRow != null)
        {
            storyActionRow.setDisable(busy);
        }
    }

    private void restoreStoryActionButtonsState()
    {
        setStoryActionButtonsBusy(false);
        if (activeStory != null && retryHistoryButton != null)
        {
            retryHistoryButton.setDisable(retryHistory.size() < 2);
        }
    }

    private void updateModelControls()
    {
        updatingModelControls = true;
        temperatureSlider.setValue(activeModelSettings.temperature());
        topKSlider.setValue(activeModelSettings.topK());
        topPSlider.setValue(activeModelSettings.topP());
        minPSlider.setValue(activeModelSettings.minP());
        presencePenaltySlider.setValue(activeModelSettings.presencePenalty());
        frequencyPenaltySlider.setValue(activeModelSettings.frequencyPenalty());
        repetitionPenaltySlider.setValue(activeModelSettings.repetitionPenalty());
        updatingModelControls = false;
    }

    private void updateAppAutoCardsControls()
    {
        if (appAutoCardsSettings == null)
        {
            return;
        }
        updatingAutoCardsControls = true;
        String candidationMode = AutoCards.normalizeCandidateSelectionMode(
                appAutoCardsSettings.candidateSelectionMode());
        String contextMode = AutoCards.normalizeContextMode(appAutoCardsSettings.contextMode());
        if (!candidationMode.equals(appAutoCardsSettings.candidateSelectionMode())
                || !contextMode.equals(appAutoCardsSettings.contextMode()))
        {
            appAutoCardsSettings = SettingsCoordinator.withCandidateSelectionMode(appAutoCardsSettings, candidationMode);
            appAutoCardsSettings = SettingsCoordinator.withContextMode(appAutoCardsSettings, contextMode);
            persistAppAutoCardsSettings();
        }
        autoCardsCandidateSelectionMode.setValue(candidationMode);
        autoCardsContextMode.setValue(contextMode);
        autoCardsCooldownSpinner.getValueFactory().setValue(appAutoCardsSettings.cooldownTurns());
        autoCardsMaxPerRunSpinner.getValueFactory().setValue(appAutoCardsSettings.maxCardsPerRun());
        autoCardsWindowSpinner.getValueFactory().setValue(appAutoCardsSettings.candidateWindow());
        autoCardsLengthLimitSpinner.getValueFactory().setValue(appAutoCardsSettings.cardLengthLimit());
        autoCardsSummarizeBox.setSelected(appAutoCardsSettings.summarizeInsteadOfTrim());
        autoCardsBulletedListsBox.setSelected(appAutoCardsSettings.useBulletedLists());
        updatingAutoCardsControls = false;
        updateAutoCardsRunButtonState();
    }

    private void updateModelAutoCardsControls()
    {
        if (modelAutoCardsSettings == null)
        {
            return;
        }
        updatingAutoCardsControls = true;
        autoCardsCreatePrompt.setText(modelAutoCardsSettings.createPrompt());
        autoCardsUpdatePrompt.setText(modelAutoCardsSettings.updatePrompt());
        autoCardsSummarizePrompt.setText(modelAutoCardsSettings.summarizePrompt());
        autoCardsMaxTokensCreate.getValueFactory().setValue(modelAutoCardsSettings.maxTokensCreate());
        autoCardsMaxTokensUpdate.getValueFactory().setValue(modelAutoCardsSettings.maxTokensUpdate());
        autoCardsMaxTokensSummarize.getValueFactory().setValue(modelAutoCardsSettings.maxTokensSummarize());
        updatingAutoCardsControls = false;
    }

    private void updateStoryAutoCardsControls()
    {
        if (storyAutoCardsSettings == null)
        {
            return;
        }
        updatingAutoCardsControls = true;
        autoCardsEnabledBox.setSelected(storyAutoCardsSettings.enabled());
        autoCardsUpdateExistingBox.setSelected(storyAutoCardsSettings.updateExisting());
        autoCardsCreateNewBox.setSelected(storyAutoCardsSettings.createNew());
        autoCardsPinNewBox.setSelected(storyAutoCardsSettings.pinNew());
        autoCardsPreviewBox.setSelected(storyAutoCardsSettings.previewFirst());
        updatingAutoCardsControls = false;
        updateAutoCardsRunButtonState();
    }

    private void updateAppAutoCardsCandidateSelectionMode(String mode)
    {
        if (updatingAutoCardsControls || appAutoCardsSettings == null || mode == null)
        {
            return;
        }
        String normalized = AutoCards.normalizeCandidateSelectionMode(mode);
        if (normalized.equals(appAutoCardsSettings.candidateSelectionMode()))
        {
            return;
        }
        appAutoCardsSettings = SettingsCoordinator.withCandidateSelectionMode(appAutoCardsSettings, normalized);
        persistAppAutoCardsSettings();
    }

    private void updateAppAutoCardsContextMode(String mode)
    {
        if (updatingAutoCardsControls || appAutoCardsSettings == null || mode == null)
        {
            return;
        }
        String normalized = AutoCards.normalizeContextMode(mode);
        if (normalized.equals(appAutoCardsSettings.contextMode()))
        {
            return;
        }
        appAutoCardsSettings = SettingsCoordinator.withContextMode(appAutoCardsSettings, normalized);
        persistAppAutoCardsSettings();
    }

    private void updateStoryAutoCardsEnabled(boolean enabled)
    {
        if (updatingAutoCardsControls || storyAutoCardsSettings == null)
        {
            return;
        }
        if (enabled == storyAutoCardsSettings.enabled())
        {
            return;
        }
        storyAutoCardsSettings = SettingsCoordinator.withEnabled(storyAutoCardsSettings, enabled);
        persistStoryAutoCardsSettings();
        updateAutoCardsRunButtonState();
    }

    private void updateAppAutoCardsCooldown(int value)
    {
        if (updatingAutoCardsControls || appAutoCardsSettings == null)
        {
            return;
        }
        AppAutoCardsSettings updated = SettingsCoordinator.withCooldownTurns(appAutoCardsSettings, value);
        if (updated.cooldownTurns() == appAutoCardsSettings.cooldownTurns())
        {
            return;
        }
        appAutoCardsSettings = updated;
        persistAppAutoCardsSettings();
    }

    private void updateAppAutoCardsMaxPerRun(int value)
    {
        if (updatingAutoCardsControls || appAutoCardsSettings == null)
        {
            return;
        }
        AppAutoCardsSettings updated = SettingsCoordinator.withMaxCardsPerRun(appAutoCardsSettings, value);
        if (updated.maxCardsPerRun() == appAutoCardsSettings.maxCardsPerRun())
        {
            return;
        }
        appAutoCardsSettings = updated;
        persistAppAutoCardsSettings();
    }

    private void updateAppAutoCardsWindow(int value)
    {
        if (updatingAutoCardsControls || appAutoCardsSettings == null)
        {
            return;
        }
        AppAutoCardsSettings updated = SettingsCoordinator.withCandidateWindow(appAutoCardsSettings, value);
        if (updated.candidateWindow() == appAutoCardsSettings.candidateWindow())
        {
            return;
        }
        appAutoCardsSettings = updated;
        persistAppAutoCardsSettings();
    }

    private void updateStoryAutoCardsUpdateExisting(boolean value)
    {
        if (updatingAutoCardsControls || storyAutoCardsSettings == null)
        {
            return;
        }
        if (value == storyAutoCardsSettings.updateExisting())
        {
            return;
        }
        storyAutoCardsSettings = SettingsCoordinator.withUpdateExisting(storyAutoCardsSettings, value);
        persistStoryAutoCardsSettings();
    }

    private void updateStoryAutoCardsCreateNew(boolean value)
    {
        if (updatingAutoCardsControls || storyAutoCardsSettings == null)
        {
            return;
        }
        if (value == storyAutoCardsSettings.createNew())
        {
            return;
        }
        storyAutoCardsSettings = SettingsCoordinator.withCreateNew(storyAutoCardsSettings, value);
        persistStoryAutoCardsSettings();
    }

    private void updateStoryAutoCardsPinNew(boolean value)
    {
        if (updatingAutoCardsControls || storyAutoCardsSettings == null)
        {
            return;
        }
        if (value == storyAutoCardsSettings.pinNew())
        {
            return;
        }
        storyAutoCardsSettings = SettingsCoordinator.withPinNew(storyAutoCardsSettings, value);
        persistStoryAutoCardsSettings();
    }

    private void updateStoryAutoCardsPreview(boolean value)
    {
        if (updatingAutoCardsControls || storyAutoCardsSettings == null)
        {
            return;
        }
        if (value == storyAutoCardsSettings.previewFirst())
        {
            return;
        }
        storyAutoCardsSettings = SettingsCoordinator.withPreviewFirst(storyAutoCardsSettings, value);
        persistStoryAutoCardsSettings();
    }

    private void updateAppAutoCardsLengthLimit(int value)
    {
        if (updatingAutoCardsControls || appAutoCardsSettings == null)
        {
            return;
        }
        AppAutoCardsSettings updated = SettingsCoordinator.withCardLengthLimit(appAutoCardsSettings, value);
        if (updated.cardLengthLimit() == appAutoCardsSettings.cardLengthLimit())
        {
            return;
        }
        appAutoCardsSettings = updated;
        persistAppAutoCardsSettings();
    }

    private void updateAppAutoCardsSummarize(boolean value)
    {
        if (updatingAutoCardsControls || appAutoCardsSettings == null)
        {
            return;
        }
        if (value == appAutoCardsSettings.summarizeInsteadOfTrim())
        {
            return;
        }
        appAutoCardsSettings = SettingsCoordinator.withSummarizeInsteadOfTrim(appAutoCardsSettings, value);
        persistAppAutoCardsSettings();
    }

    private void updateAppAutoCardsUseBulletedLists(boolean value)
    {
        if (updatingAutoCardsControls || appAutoCardsSettings == null)
        {
            return;
        }
        if (value == appAutoCardsSettings.useBulletedLists())
        {
            return;
        }
        appAutoCardsSettings = SettingsCoordinator.withUseBulletedLists(appAutoCardsSettings, value);
        persistAppAutoCardsSettings();
    }

    private void updateModelAutoCardsPrompts()
    {
        if (updatingAutoCardsControls || modelAutoCardsSettings == null)
        {
            return;
        }
        String createPrompt = autoCardsCreatePrompt.getText();
        String updatePrompt = autoCardsUpdatePrompt.getText();
        String summarizePrompt = autoCardsSummarizePrompt.getText();
        if (createPrompt.equals(modelAutoCardsSettings.createPrompt())
                && updatePrompt.equals(modelAutoCardsSettings.updatePrompt())
                && summarizePrompt.equals(modelAutoCardsSettings.summarizePrompt()))
        {
            return;
        }
        modelAutoCardsSettings = SettingsCoordinator.withPrompts(modelAutoCardsSettings, createPrompt, updatePrompt,
                summarizePrompt);
        persistModelAutoCardsSettings();
    }

    private void updateModelAutoCardsTokens()
    {
        if (updatingAutoCardsControls || modelAutoCardsSettings == null)
        {
            return;
        }
        Integer createTokens = autoCardsMaxTokensCreate.getValue();
        Integer updateTokens = autoCardsMaxTokensUpdate.getValue();
        Integer summarizeTokens = autoCardsMaxTokensSummarize.getValue();
        if (createTokens == null || updateTokens == null || summarizeTokens == null)
        {
            return;
        }
        if (createTokens == modelAutoCardsSettings.maxTokensCreate()
                && updateTokens == modelAutoCardsSettings.maxTokensUpdate()
                && summarizeTokens == modelAutoCardsSettings.maxTokensSummarize())
        {
            return;
        }
        modelAutoCardsSettings = SettingsCoordinator.withTokenCaps(modelAutoCardsSettings,
                createTokens, updateTokens, summarizeTokens);
        persistModelAutoCardsSettings();
    }

    private void updateAutoCardsRunButtonState()
    {
        if (autoCardsRunButton == null || appAutoCardsSettings == null)
        {
            return;
        }
        boolean enabled = activeStory != null && storyAutoCardsSettings != null;
        autoCardsRunButton.setDisable(!enabled);
    }

    private void runAutoCardsManual()
    {
        if (activeStory == null)
        {
            showInfo("Select a story first.");
            return;
        }
        if (storyAutoCardsSettings == null)
        {
            showInfo("Story settings are not loaded yet.");
            return;
        }

        statusLabel.setText("Auto Cards...");
        autoCardsRunButton.setDisable(true);

        Task<AutoCardsResult> task = new Task<>()
        {
            @Override
            protected AutoCardsResult call() throws Exception
            {
                List<Block> currentBlocks = blockRepository.listForStory(activeStory.id());
                List<StoryCard> currentCards = cardRepository.listForStory(activeStory.id());
                return runAutoCardsIfNeeded(currentBlocks, currentCards, true);
            }
        };

        task.setOnSucceeded(event ->
        {
            AutoCardsResult result = task.getValue();
            refreshCardList(activeStory.id());
            if (result != null && result.ran)
            {
                statusLabel.setText("Auto Cards updated (" + result.created + " new, " + result.updated + " updated)");
            }
            else
            {
                statusLabel.setText("Auto Cards: no changes");
            }
            updateAutoCardsRunButtonState();
        });

        task.setOnFailed(event ->
        {
            Throwable error = task.getException();
            statusLabel.setText("Auto Cards error: " + (error == null ? "Unknown" : error.getMessage()));
            updateAutoCardsRunButtonState();
        });

        executor.submit(task);
    }

    private AutoCardsResult runAutoCardsIfNeeded(List<Block> currentBlocks, List<StoryCard> currentCards, boolean manual)
            throws IOException, InterruptedException, SQLException
    {
        if (appAutoCardsSettings == null || storyAutoCardsSettings == null || modelAutoCardsSettings == null)
        {
            return new AutoCardsResult(0, 0, false);
        }
        if (!manual && !storyAutoCardsSettings.enabled())
        {
            return new AutoCardsResult(0, 0, false);
        }
        if (currentBlocks == null || currentBlocks.isEmpty())
        {
            return new AutoCardsResult(0, 0, false);
        }
        if (!storyAutoCardsSettings.updateExisting() && !storyAutoCardsSettings.createNew())
        {
            return new AutoCardsResult(0, 0, false);
        }
        if (!manual && !passesAutoCardsCooldown(currentBlocks))
        {
            return new AutoCardsResult(0, 0, false);
        }

        String excerpt = buildAutoCardsExcerpt(currentBlocks, appAutoCardsSettings.candidateWindow());
        if (excerpt.isBlank())
        {
            return new AutoCardsResult(0, 0, true);
        }
        String fullStoryPromptPrefix = "";
        if (AutoCards.CONTEXT_MODE_FULL_STORY.equals(
                AutoCards.normalizeContextMode(appAutoCardsSettings.contextMode())))
        {
            fullStoryPromptPrefix = autoCardsService.buildFullStoryPrompt(
                    activeStory,
                    currentBlocks,
                    currentCards,
                    appSettings,
                    activeModelSettings,
                    modelAutoCardsSettings);
        }

        List<AutoCards.Candidate> candidates = extractAutoCardCandidates(excerpt, currentCards,
                appAutoCardsSettings.maxCardsPerRun());
        if (candidates.isEmpty())
        {
            updateAutoCardsRunState(currentBlocks);
            return new AutoCardsResult(0, 0, true);
        }

        Map<String, StoryCard> byTitle = new HashMap<>();
        for (StoryCard card : currentCards)
        {
            if (card.title() != null)
            {
                byTitle.put(card.title().trim().toLowerCase(), card);
            }
        }

        int created = 0;
        int updated = 0;
        int limit = appAutoCardsSettings.cardLengthLimit();
        boolean summarize = appAutoCardsSettings.summarizeInsteadOfTrim();

        boolean preview = storyAutoCardsSettings.previewFirst();
        for (AutoCards.Candidate candidate : candidates)
        {
            if (candidate.title().isBlank())
            {
                continue;
            }
            String key = candidate.title().trim().toLowerCase();
            StoryCard existing = byTitle.get(key);
            if (existing != null)
            {
                if (!storyAutoCardsSettings.updateExisting())
                {
                    continue;
                }
                String updatedContent = autoCardsService.generateCardUpdate(
                        existing,
                        excerpt,
                        fullStoryPromptPrefix,
                        appAutoCardsSettings.useBulletedLists(),
                        appSettings,
                        activeModelSettings,
                        modelAutoCardsSettings);
                if (updatedContent.isBlank())
                {
                    continue;
                }
                AutoCardsService.LengthEnforcementResult lengthResult = autoCardsService.enforceCardLengthDetailed(
                        updatedContent,
                        summarize,
                        limit,
                        existing.title(),
                        existing.triggers(),
                        excerpt,
                        fullStoryPromptPrefix,
                        appAutoCardsSettings.useBulletedLists(),
                        appSettings,
                        activeModelSettings,
                        modelAutoCardsSettings);
                updatedContent = lengthResult.content();
                if (preview)
                {
                    String proposed = updatedContent;
                    boolean summarizedForPreview = lengthResult.summarized();
                    String approved = runOnUiThreadAndWait(
                            () -> showAutoCardUpdateDialog(existing, proposed, summarizedForPreview));
                    if (approved == null)
                    {
                        continue;
                    }
                    updatedContent = approved;
                }
                StoryCard updatedCard = new StoryCard(existing.id(), existing.storyId(), existing.title(),
                        existing.triggers(), updatedContent, existing.pinned());
                cardRepository.update(updatedCard);
                updated++;
            }
            else
            {
                if (!storyAutoCardsSettings.createNew())
                {
                    continue;
                }
                String content = autoCardsService.generateCardCreate(
                        candidate,
                        excerpt,
                        fullStoryPromptPrefix,
                        appAutoCardsSettings.useBulletedLists(),
                        appSettings,
                        activeModelSettings,
                        modelAutoCardsSettings);
                if (content.isBlank())
                {
                    continue;
                }
                content = autoCardsService.enforceCardLength(
                        content,
                        summarize,
                        limit,
                        candidate.title(),
                        candidate.triggers(),
                        excerpt,
                        fullStoryPromptPrefix,
                        appAutoCardsSettings.useBulletedLists(),
                        appSettings,
                        activeModelSettings,
                        modelAutoCardsSettings);
                StoryCard createdCard = new StoryCard(Ids.newId(), activeStory.id(), candidate.title(),
                        candidate.triggers(), content, storyAutoCardsSettings.pinNew());
                if (preview)
                {
                    StoryCard draft = createdCard;
                    StoryCard approved = runOnUiThreadAndWait(() -> showAutoCardCreateDialog(draft));
                    if (approved == null)
                    {
                        continue;
                    }
                    createdCard = approved;
                }
                cardRepository.insert(createdCard);
                created++;
            }
        }

        updateAutoCardsRunState(currentBlocks);
        return new AutoCardsResult(created, updated, true);
    }

    private boolean passesAutoCardsCooldown(List<Block> currentBlocks)
    {
        int assistantCount = countAssistantBlocks(currentBlocks);
        AutoCardsRunState state = autoCardsRunState.get(activeStory.id());
        if (state == null)
        {
            return true;
        }
        int diff = assistantCount - state.assistantCount;
        if (diff < appAutoCardsSettings.cooldownTurns())
        {
            return false;
        }
        return true;
    }

    private void updateAutoCardsRunState(List<Block> currentBlocks)
    {
        int assistantCount = countAssistantBlocks(currentBlocks);
        autoCardsRunState.put(activeStory.id(), new AutoCardsRunState(assistantCount));
    }

    private int countAssistantBlocks(List<Block> currentBlocks)
    {
        int count = 0;
        for (Block block : currentBlocks)
        {
            if (block.role() == Role.ASSISTANT)
            {
                count++;
            }
        }
        return count;
    }

    private String buildAutoCardsExcerpt(List<Block> currentBlocks, int window)
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

    private List<AutoCards.Candidate> extractAutoCardCandidates(String excerpt, List<StoryCard> currentCards, int maxCount)
            throws IOException, InterruptedException
    {
        String mode = AutoCards.normalizeCandidateSelectionMode(appAutoCardsSettings.candidateSelectionMode());
        if (AutoCards.CANDIDATE_SELECTION_MODE_ASK_MODEL.equals(mode))
        {
            return extractAutoCardCandidatesByModel(excerpt, currentCards, maxCount);
        }
        return AutoCards.extractCandidatesByHeuristics(excerpt, currentCards, maxCount);
    }

    private List<AutoCards.Candidate> extractAutoCardCandidatesByModel(String excerpt, List<StoryCard> currentCards,
            int maxCount)
            throws IOException, InterruptedException
    {
        return autoCardsService.extractCandidatesByModel(
                excerpt,
                currentCards,
                maxCount,
                appSettings,
                activeModelSettings,
                modelAutoCardsSettings);
    }

    private void refreshStoryList(String selectedId)
    {
        try
        {
            storyItems.setAll(storyRepository.listAll());
        }
        catch (SQLException e)
        {
            showError("Failed to load stories", e);
        }

        if (selectedId != null)
        {
            for (Story story : storyItems)
            {
                if (story.id().equals(selectedId))
                {
                    storyList.getSelectionModel().select(story);
                    break;
                }
            }
        }
    }

    private void refreshCardList(String storyId)
    {
        if (storyId == null)
        {
            cardItems.clear();
            return;
        }
        try
        {
            cardItems.setAll(cardRepository.listForStory(storyId));
        }
        catch (SQLException e)
        {
            showError("Failed to load story cards", e);
        }
    }

    private void showNewStoryDialog()
    {
        TextInputDialog dialog = new TextInputDialog("New Story");
        dialog.setTitle("New Story");
        dialog.setHeaderText("Enter a story name");
        dialog.initOwner(primaryStage);
        dialog.showAndWait().ifPresent(name ->
        {
            String trimmed = name.trim();
            if (trimmed.isEmpty())
            {
                showInfo("Story name cannot be empty.");
                return;
            }
            try
            {
                String now = Timestamps.now();
                Story story = new Story(Ids.newId(), trimmed, DEFAULT_SYSTEM_PROMPT, "", "", now, now);
                storyRepository.insert(story);
                refreshStoryList(story.id());
                loadStory(story, true);
            }
            catch (SQLException e)
            {
                showError("Failed to create story", e);
            }
        });
    }

    private void showImportAdventureDialog()
    {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Import Adventure");
        dialog.setHeaderText("Import AI Dungeon adventure backup");
        dialog.initOwner(primaryStage);

        TextField fileField = new TextField();
        fileField.setEditable(false);
        fileField.setPromptText("Select a ZIP file");

        Button browseButton = new Button("Choose File");
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP Files", "*.zip"));
        browseButton.setOnAction(event ->
        {
            java.io.File selected = chooser.showOpenDialog(primaryStage);
            if (selected != null)
            {
                fileField.setText(selected.getAbsolutePath());
            }
        });

        HBox fileRow = new HBox(8, fileField, browseButton);
        HBox.setHgrow(fileField, Priority.ALWAYS);

        Button importButton = new Button("Import");
        importButton.setDisable(true);
        Button cancelButton = new Button("Cancel");

        fileField.textProperty().addListener((obs, oldValue, newValue) ->
        {
            importButton.setDisable(newValue == null || newValue.isBlank());
        });

        importButton.setOnAction(event ->
        {
            try
            {
                Story imported = aiDungeonImports.importAdventure(Path.of(fileField.getText()));
                refreshStoryList(imported.id());
                loadStory(imported, true);
                dialog.close();
                showInfo("Imported adventure \"" + imported.title() + "\".");
            }
            catch (Exception e)
            {
                showError("Failed to import adventure", e);
            }
        });

        cancelButton.setOnAction(event -> dialog.close());

        HBox buttons = new HBox(8, importButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(12, fileRow, buttons);
        content.setPadding(new Insets(12));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        Node cancelNode = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelNode != null)
        {
            cancelNode.setVisible(false);
            cancelNode.setManaged(false);
        }
        dialog.showAndWait();
    }

    private void showStoryDialog(Story story)
    {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Story");
        dialog.setHeaderText("Story settings");
        dialog.initOwner(primaryStage);

        TextField titleField = new TextField(story.title());
        titleField.setPrefWidth(320);

        VBox content = new VBox(8, new Label("Title"), titleField);
        content.setPadding(new Insets(10));

        ButtonType playType = new ButtonType("Play", ButtonBar.ButtonData.OK_DONE);
        ButtonType updateType = new ButtonType("Update", ButtonBar.ButtonData.APPLY);
        ButtonType deleteType = new ButtonType("Delete", ButtonBar.ButtonData.LEFT);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(playType, updateType, deleteType, cancelType);
        dialog.getDialogPane().setContent(content);

        Button playButton = (Button) dialog.getDialogPane().lookupButton(playType);
        playButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            String name = titleField.getText().trim();
            if (name.isEmpty())
            {
                showInfo("Story name cannot be empty.");
                return;
            }
            Story updated = updateStoryTitleIfNeeded(story, name);
            dialog.close();
            playStory(updated);
        });

        Button updateButton = (Button) dialog.getDialogPane().lookupButton(updateType);
        updateButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            String name = titleField.getText().trim();
            if (name.isEmpty())
            {
                showInfo("Story name cannot be empty.");
                return;
            }
            Story updated = updateStoryTitleIfNeeded(story, name);
            refreshStoryList(updated.id());
            dialog.close();
        });

        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteType);
        deleteButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            if (confirmDelete(story.title()))
            {
                deleteStory(story);
                dialog.close();
            }
        });

        dialog.showAndWait();
    }

    private void showCardDialog(StoryCard card)
    {
        if (activeStory == null)
        {
            showInfo("Select a story first.");
            return;
        }

        boolean isNew = card == null;
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "New Story Card" : "Edit Story Card");
        dialog.setHeaderText(isNew ? "Create a story card" : "Edit story card");
        dialog.initOwner(primaryStage);

        TextField titleField = new TextField(isNew ? "" : card.title());
        TextArea contentArea = new TextArea(isNew ? "" : card.content());
        contentArea.setWrapText(true);
        contentArea.setPrefRowCount(6);
        TextField triggersField = new TextField(isNew ? "" : card.triggers());
        CheckBox pinnedBox = new CheckBox("Pinned");
        pinnedBox.setSelected(!isNew && card.pinned());

        VBox content = new VBox(8, new Label("Title"), titleField, new Label("Content"), contentArea,
                new Label("Triggers (comma separated)"), triggersField, pinnedBox);
        content.setPadding(new Insets(10));

        ButtonType saveType = new ButtonType(isNew ? "Create" : "Update", ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteType = new ButtonType("Delete", ButtonBar.ButtonData.LEFT);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        if (isNew)
        {
            dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);
        }
        else
        {
            dialog.getDialogPane().getButtonTypes().addAll(saveType, deleteType, cancelType);
        }
        dialog.getDialogPane().setContent(content);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            String title = titleField.getText().trim();
            if (title.isEmpty())
            {
                showInfo("Card title cannot be empty.");
                return;
            }
            String contentText = contentArea.getText().trim();
            String triggers = triggersField.getText().trim();

            if (isNew)
            {
                StoryCard newCard = new StoryCard(Ids.newId(), activeStory.id(), title, triggers, contentText,
                        pinnedBox.isSelected());
                try
                {
                    cardRepository.insert(newCard);
                    refreshCardList(activeStory.id());
                    dialog.close();
                }
                catch (SQLException e)
                {
                    showError("Failed to create story card", e);
                }
            }
            else
            {
                StoryCard updated = new StoryCard(card.id(), card.storyId(), title, triggers, contentText,
                        pinnedBox.isSelected());
                try
                {
                    cardRepository.update(updated);
                    refreshCardList(activeStory.id());
                    dialog.close();
                }
                catch (SQLException e)
                {
                    showError("Failed to update story card", e);
                }
            }
        });

        if (!isNew)
        {
            Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteType);
            deleteButton.addEventFilter(ActionEvent.ACTION, event ->
            {
                event.consume();
                if (confirmDeleteCard(card.title()))
                {
                    deleteCard(card);
                    dialog.close();
                }
            });
        }

        dialog.showAndWait();
    }

    private void showGenerateCardDialog()
    {
        if (activeStory == null)
        {
            showInfo("Select a story first.");
            return;
        }
        if (appAutoCardsSettings == null || modelAutoCardsSettings == null)
        {
            showInfo("Auto Cards settings are not loaded yet.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Generate Story Card");
        dialog.setHeaderText("Generate a new card from a prompt");
        dialog.initOwner(primaryStage);

        TextField titleField = new TextField();
        TextArea contentArea = new TextArea();
        contentArea.setWrapText(true);
        contentArea.setPrefRowCount(6);
        TextField triggersField = new TextField();
        CheckBox pinnedBox = new CheckBox("Pinned");
        TextArea promptArea = new TextArea();
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(4);
        promptArea.setPromptText("Example: Generate a new character that is a male warrior.");

        VBox content = new VBox(8,
                new Label("Prompt"), promptArea,
                new Label("Title"), titleField,
                new Label("Content"), contentArea,
                new Label("Triggers (comma separated)"), triggersField,
                pinnedBox);
        content.setPadding(new Insets(10));

        Button generateButton = new Button("Generate");
        Button createButton = new Button("Create");
        Button createAndCloseButton = new Button("Create and Close");
        Button cancelButton = new Button("Cancel");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox rightActions = new HBox(8, createButton, createAndCloseButton, cancelButton);
        rightActions.setAlignment(Pos.CENTER_RIGHT);
        HBox actions = new HBox(8, generateButton, spacer, rightActions);
        actions.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().add(actions);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        Node cancelNode = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelNode != null)
        {
            cancelNode.setVisible(false);
            cancelNode.setManaged(false);
        }

        Runnable saveAndKeepOpen = () ->
        {
            String title = titleField.getText().trim();
            if (title.isEmpty())
            {
                showInfo("Card title cannot be empty.");
                return;
            }
            String contentText = contentArea.getText().trim();
            if (contentText.isEmpty())
            {
                showInfo("Card content cannot be empty.");
                return;
            }
            String triggers = triggersField.getText().trim();

            StoryCard newCard = new StoryCard(Ids.newId(), activeStory.id(), title, triggers, contentText,
                    pinnedBox.isSelected());
            try
            {
                cardRepository.insert(newCard);
                refreshCardList(activeStory.id());
                titleField.clear();
                triggersField.clear();
                contentArea.clear();
            }
            catch (SQLException e)
            {
                showError("Failed to create story card", e);
            }
        };

        createButton.setOnAction(event -> saveAndKeepOpen.run());
        createAndCloseButton.setOnAction(event ->
        {
            int before = cardItems.size();
            saveAndKeepOpen.run();
            if (cardItems.size() > before)
            {
                dialog.close();
            }
        });
        cancelButton.setOnAction(event -> dialog.close());

        generateButton.setOnAction(event ->
        {
            String request = promptArea.getText().trim();
            if (request.isEmpty())
            {
                showInfo("Prompt cannot be empty.");
                return;
            }

            final String storyId = activeStory.id();
            final Story story = activeStory;
            generateButton.setDisable(true);
            createButton.setDisable(true);
            createAndCloseButton.setDisable(true);
            statusLabel.setText("Generating story card...");

            Task<AutoCards.GeneratedCard> task = new Task<>()
            {
                @Override
                protected AutoCards.GeneratedCard call() throws Exception
                {
                    List<Block> currentBlocks = blockRepository.listForStory(storyId);
                    List<StoryCard> currentCards = cardRepository.listForStory(storyId);
                    String contextMode = AutoCards.normalizeContextMode(appAutoCardsSettings.contextMode());
                    String excerpt = "";
                    if (!AutoCards.CONTEXT_MODE_FULL_STORY.equals(contextMode))
                    {
                        excerpt = buildAutoCardsExcerpt(currentBlocks, appAutoCardsSettings.candidateWindow());
                    }
                    String fullStoryPromptPrefix = "";
                    if (AutoCards.CONTEXT_MODE_FULL_STORY.equals(contextMode))
                    {
                        fullStoryPromptPrefix = autoCardsService.buildFullStoryPrompt(
                                story,
                                currentBlocks,
                                currentCards,
                                appSettings,
                                activeModelSettings,
                                modelAutoCardsSettings);
                    }

                    AutoCards.GeneratedCard generated = autoCardsService.generateCardFromUserPrompt(
                            request,
                            excerpt,
                            fullStoryPromptPrefix,
                            appAutoCardsSettings.useBulletedLists(),
                            appSettings,
                            activeModelSettings,
                            modelAutoCardsSettings);
                    if (generated == null)
                    {
                        return null;
                    }
                    String enforced = autoCardsService.enforceCardLength(
                            generated.content(),
                            appAutoCardsSettings.summarizeInsteadOfTrim(),
                            appAutoCardsSettings.cardLengthLimit(),
                            generated.title(),
                            generated.triggers(),
                            excerpt,
                            fullStoryPromptPrefix,
                            appAutoCardsSettings.useBulletedLists(),
                            appSettings,
                            activeModelSettings,
                            modelAutoCardsSettings);
                    return new AutoCards.GeneratedCard(generated.title(), generated.triggers(), enforced);
                }
            };

            task.setOnSucceeded(e ->
            {
                generateButton.setDisable(false);
                createButton.setDisable(false);
                createAndCloseButton.setDisable(false);
                AutoCards.GeneratedCard generated = task.getValue();
                if (generated == null)
                {
                    statusLabel.setText("Generate card: no result");
                    return;
                }
                titleField.setText(generated.title());
                triggersField.setText(generated.triggers());
                contentArea.setText(generated.content());
                statusLabel.setText("Generated story card draft");
            });

            task.setOnFailed(e ->
            {
                generateButton.setDisable(false);
                createButton.setDisable(false);
                createAndCloseButton.setDisable(false);
                Throwable error = task.getException();
                statusLabel.setText("Generate card failed");
                if (error instanceof Exception exception)
                {
                    showError("Failed to generate story card", exception);
                }
                else if (error != null)
                {
                    showError("Failed to generate story card", new RuntimeException(error));
                }
            });

            executor.submit(task);
        });

        dialog.showAndWait();
    }

    private void showSeeDialog()
    {
        showSeeDialog(null, null);
    }

    private void showSeeDialog(String initialPrompt, Block replaceImageBlock)
    {
        if (activeStory == null)
        {
            showInfo("Select a story first.");
            return;
        }
        if (appAutoCardsSettings == null || modelAutoCardsSettings == null)
        {
            showInfo("Auto Cards settings are not loaded yet.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("See");
        dialog.setHeaderText(replaceImageBlock == null
                ? "Generate an image prompt from the story"
                : "Retry image generation");
        dialog.initOwner(primaryStage);
        dialog.setResizable(true);

        TextArea requestArea = new TextArea();
        requestArea.setWrapText(true);
        requestArea.setPrefRowCount(3);
        requestArea.setPromptText("Optional. Leave blank to infer from the latest scene.");

        TextArea promptArea = new TextArea(initialPrompt == null ? "" : initialPrompt);
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(6);
        promptArea.setPromptText("Generated image prompt will appear here.");

        ImageView selectedPreview = new ImageView();
        selectedPreview.setPreserveRatio(true);
        selectedPreview.setSmooth(true);
        selectedPreview.setFitWidth(640);
        selectedPreview.setFitHeight(320);
        selectedPreview.setVisible(false);
        selectedPreview.setManaged(false);

        TilePane thumbnails = new TilePane();
        thumbnails.setHgap(8);
        thumbnails.setVgap(8);
        thumbnails.setPrefColumns(4);
        thumbnails.setPrefTileWidth(140);
        thumbnails.setPrefTileHeight(140);
        thumbnails.setTileAlignment(Pos.CENTER);

        Label imagesPlaceholder = new Label("No images yet. Generate a prompt, then create images.");
        imagesPlaceholder.setWrapText(true);
        imagesPlaceholder.setStyle("-fx-text-fill: #b8b1a5;");

        VBox imageResultsBox = new VBox(8, new Label("Images"), imagesPlaceholder, selectedPreview, thumbnails);
        imageResultsBox.setPadding(new Insets(8));
        imageResultsBox.setStyle("-fx-border-color: rgba(255,255,255,0.12); -fx-border-width: 1; -fx-border-radius: 4;");

        Button regeneratePromptButton = new Button("Regenerate Prompt");
        Button createImagesButton = new Button("Create Images");
        Button insertImageButton = new Button(replaceImageBlock == null ? "Insert Image" : "Replace Image");
        Button cancelButton = new Button("Cancel");
        insertImageButton.setDisable(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox rightButtons = new HBox(8, createImagesButton, insertImageButton, cancelButton);
        HBox actions = new HBox(8, regeneratePromptButton, spacer, rightButtons);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10,
                new Label("Request"),
                requestArea,
                new Label("Image Prompt"),
                promptArea,
                imageResultsBox,
                actions);
        content.setPadding(new Insets(10));

        ScrollPane contentScroll = new ScrollPane(content);
        contentScroll.setFitToWidth(true);
        contentScroll.setFitToHeight(false);
        contentScroll.setPannable(true);
        contentScroll.setPrefViewportWidth(980);
        contentScroll.setPrefViewportHeight(660);
        dialog.getDialogPane().setContent(contentScroll);
        dialog.getDialogPane().setPrefWidth(1040);
        dialog.getDialogPane().setPrefHeight(720);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        Node hiddenCancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (hiddenCancel != null)
        {
            hiddenCancel.setVisible(false);
            hiddenCancel.setManaged(false);
        }

        final List<ImageGenerationCoordinator.PendingImage>[] pendingImagesRef = new List[] { new ArrayList<>() };
        final int[] selectedIndexRef = new int[] { -1 };

        Runnable refreshImageSelectionUi = () ->
        {
            thumbnails.getChildren().clear();
            List<ImageGenerationCoordinator.PendingImage> pendingImages = pendingImagesRef[0];
            if (pendingImages == null || pendingImages.isEmpty())
            {
                imagesPlaceholder.setVisible(true);
                imagesPlaceholder.setManaged(true);
                selectedPreview.setVisible(false);
                selectedPreview.setManaged(false);
                insertImageButton.setDisable(true);
                return;
            }

            imagesPlaceholder.setVisible(false);
            imagesPlaceholder.setManaged(false);
            ToggleGroup group = new ToggleGroup();
            for (int i = 0; i < pendingImages.size(); i++)
            {
                ImageGenerationCoordinator.PendingImage pending = pendingImages.get(i);
                Image img = new Image(new ByteArrayInputStream(pending.bytes()));
                ImageView thumbView = new ImageView(img);
                thumbView.setPreserveRatio(true);
                thumbView.setSmooth(true);
                thumbView.setFitWidth(132);
                thumbView.setFitHeight(132);

                ToggleButton thumbButton = new ToggleButton();
                thumbButton.setGraphic(thumbView);
                thumbButton.setToggleGroup(group);
                thumbButton.setUserData(i);
                thumbButton.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
                thumbButton.setStyle("-fx-padding: 4;");
                if (i == selectedIndexRef[0])
                {
                    thumbButton.setSelected(true);
                    selectedPreview.setImage(img);
                    selectedPreview.setVisible(true);
                    selectedPreview.setManaged(true);
                }
                thumbnails.getChildren().add(thumbButton);
            }

            group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) ->
            {
                if (newToggle == null)
                {
                    selectedIndexRef[0] = -1;
                    insertImageButton.setDisable(true);
                    return;
                }
                int index = (int) newToggle.getUserData();
                selectedIndexRef[0] = index;
                ImageGenerationCoordinator.PendingImage pending = pendingImagesRef[0].get(index);
                selectedPreview.setImage(new Image(new ByteArrayInputStream(pending.bytes())));
                selectedPreview.setVisible(true);
                selectedPreview.setManaged(true);
                insertImageButton.setDisable(false);
            });

            if (selectedIndexRef[0] < 0 && !pendingImages.isEmpty())
            {
                selectedIndexRef[0] = 0;
                ImageGenerationCoordinator.PendingImage first = pendingImages.getFirst();
                selectedPreview.setImage(new Image(new ByteArrayInputStream(first.bytes())));
                selectedPreview.setVisible(true);
                selectedPreview.setManaged(true);
                insertImageButton.setDisable(false);
            }
            else
            {
                insertImageButton.setDisable(selectedIndexRef[0] < 0);
            }
        };

        Runnable restoreSeeButtons = () ->
        {
            regeneratePromptButton.setDisable(false);
            createImagesButton.setDisable(false);
            insertImageButton.setDisable(selectedIndexRef[0] < 0);
            cancelButton.setDisable(false);
            restoreStoryActionButtonsState();
        };

        cancelButton.setOnAction(event -> dialog.close());

        regeneratePromptButton.setOnAction(event ->
        {
            String request = requestArea.getText().trim();
            if (request.isEmpty())
            {
                request = DEFAULT_SEE_REQUEST;
            }

            final Story story = activeStory;
            final String requestFinal = request;
            regeneratePromptButton.setDisable(true);
            createImagesButton.setDisable(true);
            insertImageButton.setDisable(true);
            cancelButton.setDisable(true);
            setStoryActionButtonsBusy(true);
            statusLabel.setText("Generating image prompt...");

            Task<String> task = new Task<>()
            {
                @Override
                protected String call() throws Exception
                {
                    return imageGenerationCoordinator.generateImagePrompt(
                            story,
                            requestFinal,
                            appSettings,
                            activeModelSettings,
                            appAutoCardsSettings,
                            modelAutoCardsSettings);
                }
            };

            task.setOnSucceeded(e ->
            {
                restoreSeeButtons.run();
                String generated = task.getValue();
                if (generated == null || generated.isBlank())
                {
                    statusLabel.setText("Image prompt generation returned empty.");
                    return;
                }
                promptArea.setText(generated);
                statusLabel.setText("Generated image prompt");
            });

            task.setOnFailed(e ->
            {
                restoreSeeButtons.run();
                Throwable error = task.getException();
                statusLabel.setText("Image prompt generation failed");
                if (error instanceof Exception exception)
                {
                    showError("Failed to generate image prompt", exception);
                }
                else if (error != null)
                {
                    showError("Failed to generate image prompt", new RuntimeException(error));
                }
            });

            executor.submit(task);
        });

        createImagesButton.setOnAction(event ->
        {
            String promptText = promptArea.getText();
            if (promptText == null || promptText.isBlank())
            {
                showInfo("Image prompt cannot be empty.");
                return;
            }
            regeneratePromptButton.setDisable(true);
            createImagesButton.setDisable(true);
            insertImageButton.setDisable(true);
            cancelButton.setDisable(true);
            setStoryActionButtonsBusy(true);
            statusLabel.setText("Generating images...");

            Task<ComfyUiClient.GenerationResult> task = new Task<>()
            {
                @Override
                protected ComfyUiClient.GenerationResult call() throws Exception
                {
                    return imageGenerationCoordinator.generateImages(appSettings, promptText);
                }
            };

            task.setOnSucceeded(e ->
            {
                restoreSeeButtons.run();
                ComfyUiClient.GenerationResult result = task.getValue();
                if (result == null || result.images() == null || result.images().isEmpty())
                {
                    statusLabel.setText("ComfyUI returned no images.");
                    return;
                }
                List<ImageGenerationCoordinator.PendingImage> pending = new ArrayList<>();
                for (ComfyUiClient.GeneratedImage image : result.images())
                {
                    pending.add(new ImageGenerationCoordinator.PendingImage(image.bytes(), image.mimeType(), result.workflowJson()));
                }
                pendingImagesRef[0] = pending;
                selectedIndexRef[0] = pending.isEmpty() ? -1 : 0;
                refreshImageSelectionUi.run();
                createImagesButton.setText("Regenerate Images");
                statusLabel.setText("Generated " + pending.size() + " image(s)");
            });

            task.setOnFailed(e ->
            {
                restoreSeeButtons.run();
                Throwable error = task.getException();
                statusLabel.setText("Image generation failed");
                System.out.println("ComfyUI image generation failed:");
                if (error != null)
                {
                    error.printStackTrace(System.out);
                }
                if (error instanceof Exception exception)
                {
                    showError("Failed to generate images", exception);
                }
                else if (error != null)
                {
                    showError("Failed to generate images", new RuntimeException(error));
                }
            });

            executor.submit(task);
        });

        insertImageButton.setOnAction(event ->
        {
            int selectedIndex = selectedIndexRef[0];
            if (selectedIndex < 0 || selectedIndex >= pendingImagesRef[0].size())
            {
                showInfo("Select an image first.");
                return;
            }
            ImageGenerationCoordinator.PendingImage pending = pendingImagesRef[0].get(selectedIndex);
            String promptText = promptArea.getText() == null ? "" : promptArea.getText().trim();
            if (promptText.isBlank())
            {
                showInfo("Image prompt cannot be empty.");
                return;
            }
            try
            {
                ImageGenerationCoordinator.ImageMutationResult result = imageGenerationCoordinator.insertOrReplaceImage(
                        activeStory, pending, promptText, replaceImageBlock);
                activeStory = result.updatedStory();
                blocks = blockRepository.listForStory(activeStory.id());
                renderStoryBlocks(true);
                if (!result.replaced())
                {
                    clearRetryHistory();
                }
                else
                {
                    StoryImage storyImage = result.storyImage();
                    retryHistory.add(new ImageRetryHistoryEntry(storyImage.prompt(), storyImage.imageBytes(), storyImage.mimeType(),
                            storyImage.workflowJson()));
                    retryIndex = retryHistory.size() - 1;
                    updateRetryCountLabel();
                }
                refreshStoryList(activeStory.id());
                statusLabel.setText(replaceImageBlock == null ? "Inserted image" : "Replaced image");
                dialog.close();
            }
            catch (Exception ex)
            {
                if (ex instanceof Exception exception)
                {
                    showError("Failed to insert image", exception);
                }
                else
                {
                    showError("Failed to insert image", new RuntimeException(ex));
                }
            }
        });

        if (promptArea.getText().isBlank())
        {
            requestArea.setText("");
        }
        refreshImageSelectionUi.run();

        dialog.showAndWait();
        setStoryDependentControlsEnabled(activeStory != null);
    }

    private void deleteCard(StoryCard card)
    {
        try
        {
            cardRepository.delete(card.id());
            refreshCardList(activeStory.id());
        }
        catch (SQLException e)
        {
            showError("Failed to delete story card", e);
        }
    }

    private boolean confirmDeleteCard(String title)
    {
        Alert first = new Alert(Alert.AlertType.CONFIRMATION);
        first.initOwner(primaryStage);
        first.setTitle("Delete Story Card");
        first.setHeaderText("Delete card \"" + title + "\"?");
        first.setContentText("This will remove the card.");
        if (first.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
        {
            return false;
        }

        Alert second = new Alert(Alert.AlertType.CONFIRMATION);
        second.initOwner(primaryStage);
        second.setTitle("Delete Story Card");
        second.setHeaderText("Are you absolutely sure?");
        second.setContentText("This action cannot be undone.");
        return second.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private Story updateStoryTitleIfNeeded(Story story, String name)
    {
        if (name.equals(story.title()))
        {
            return story;
        }
        Story base = story;
        if (activeStory != null && activeStory.id().equals(story.id()))
        {
            base = activeStory;
        }
        String now = Timestamps.now();
        Story updated = new Story(story.id(), name, base.systemPrompt(), base.plotEssentials(), base.authorNote(),
                base.createdAt(), now);
        try
        {
            storyRepository.updateTitle(updated.id(), updated.title(), updated.updatedAt());
            if (activeStory != null && activeStory.id().equals(updated.id()))
            {
                activeStory = updated;
            }
            refreshStoryList(updated.id());
            return updated;
        }
        catch (SQLException e)
        {
            showError("Failed to update story", e);
            return story;
        }
    }

    private void saveStoryDetails()
    {
        if (activeStory == null)
        {
            return;
        }
        String systemPrompt = systemPromptArea.getText();
        String plotEssentials = plotEssentialsArea.getText();
        String authorNote = authorNoteArea.getText();

        if (systemPrompt.equals(activeStory.systemPrompt()) && plotEssentials.equals(activeStory.plotEssentials())
                && authorNote.equals(activeStory.authorNote()))
        {
            return;
        }

        String now = Timestamps.now();
        Story updated = new Story(activeStory.id(), activeStory.title(), systemPrompt, plotEssentials, authorNote,
                activeStory.createdAt(), now);
        try
        {
            storyRepository.update(updated);
            activeStory = updated;
            refreshStoryList(activeStory.id());
        }
        catch (SQLException e)
        {
            showError("Failed to update story details", e);
        }
    }

    private boolean confirmDelete(String title)
    {
        Alert first = new Alert(Alert.AlertType.CONFIRMATION);
        first.initOwner(primaryStage);
        first.setTitle("Delete Story");
        first.setHeaderText("Delete story \"" + title + "\"?");
        first.setContentText("This will remove the story and all its blocks.");
        if (first.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
        {
            return false;
        }

        Alert second = new Alert(Alert.AlertType.CONFIRMATION);
        second.initOwner(primaryStage);
        second.setTitle("Delete Story");
        second.setHeaderText("Are you absolutely sure?");
        second.setContentText("This action cannot be undone.");
        return second.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void deleteStory(Story story)
    {
        try
        {
            storyRepository.delete(story.id());
            refreshStoryList(null);
            if (activeStory != null && activeStory.id().equals(story.id()))
            {
                activeStory = null;
                blocks = new ArrayList<>();
                renderStoryBlocks(false);
                statusLabel.setText("Select a story");
                populateStoryDetails(null);
                refreshCardList(null);
                setStoryDependentControlsEnabled(false);
            }
        }
        catch (SQLException e)
        {
            showError("Failed to delete story", e);
        }
    }

    private void deleteHeadBlock()
    {
        if (activeStory == null)
        {
            showInfo("Select a story first.");
            return;
        }
        if (blocks.isEmpty())
        {
            return;
        }
        try
        {
            Block head = blocks.get(blocks.size() - 1);
            blockRepository.deleteHead(activeStory.id());
            deleteLinkedImageIfPresent(head);
            blocks = blockRepository.listForStory(activeStory.id());
            renderStoryBlocks(true);
            clearRetryHistory();
        }
        catch (SQLException e)
        {
            showError("Failed to delete block", e);
        }
    }

    private void playStory(Story story)
    {
        String now = Timestamps.now();
        Story updated = new Story(story.id(), story.title(), story.systemPrompt(), story.plotEssentials(), story.authorNote(),
                story.createdAt(), now);
        try
        {
            storyRepository.update(updated);
        }
        catch (SQLException e)
        {
            showError("Failed to update story timestamp", e);
        }
        refreshStoryList(updated.id());
        loadStory(updated, true);
    }

    private void loadStory(Story story, boolean updateSelection)
    {
        try
        {
            activeStory = story;
            blocks = blockRepository.listForStory(story.id());
            storyAutoCardsSettings = loadOrCreateStoryAutoCardsSettings(story.id());
            renderStoryBlocks(true);
            statusLabel.setText("Ready");
            populateStoryDetails(story);
            updateStoryAutoCardsControls();
            updateAutoCardsRunButtonState();
            refreshCardList(story.id());
            setStoryDependentControlsEnabled(true);
            if (updateSelection)
            {
                storyList.getSelectionModel().select(story);
            }
        }
        catch (SQLException e)
        {
            showError("Failed to load story", e);
        }
    }

    private void runRetry()
    {
        if (activeStory == null)
        {
            showInfo("Select a story first.");
            return;
        }
        if (blocks.isEmpty())
        {
            return;
        }
        Block head = blocks.get(blocks.size() - 1);
        if (head.role() == Role.IMAGE)
        {
            seedImageRetryHistoryIfNeeded(head);
            StoryImage image = loadStoryImage(head.text());
            showSeeDialog(image == null ? null : image.prompt(), head);
            return;
        }
        if (head.role() != Role.ASSISTANT)
        {
            showInfo("The last block is not retryable.");
            return;
        }

        setStoryActionButtonsBusy(true);
        statusLabel.setText("Generating...");

        Task<Block> task = new Task<>()
        {
            @Override
            protected Block call() throws Exception
            {
                if (retryHistory.isEmpty())
                {
                    retryHistory.add(new TextRetryHistoryEntry(head.text()));
                    retryIndex = 0;
                }

                settings = buildGenerationSettings();
                GenerationCoordinator.RetryResult result = generationCoordinator.retryAssistantHead(activeStory, blocks, head, settings);
                observePromptCalibration(result.estimatedPromptTokens());
                if (result.updatedBlock() == null)
                {
                    return null;
                }
                Block updated = result.updatedBlock();
                retryHistory.add(new TextRetryHistoryEntry(updated.text()));
                retryIndex = retryHistory.size() - 1;
                return updated;
            }
        };

        task.setOnSucceeded(event ->
        {
            Block updated = task.getValue();
            if (updated == null)
            {
                statusLabel.setText("Last generation was empty.");
                restoreStoryActionButtonsState();
                return;
            }
            blocks.set(blocks.size() - 1, updated);
            renderStoryBlocks(true);
            statusLabel.setText("Ready");
            updateRetryCountLabel();
            restoreStoryActionButtonsState();
        });

        task.setOnFailed(event ->
        {
            Throwable error = task.getException();
            restoreStoryActionButtonsState();
            statusLabel.setText("Error: " + (error == null ? "Unknown" : error.getMessage()));
        });

        executor.submit(task);
    }

    private void showRetryDialog()
    {
        if (retryHistory.size() < 2)
        {
            return;
        }
        if (retryIndex < 0 || retryIndex >= retryHistory.size())
        {
            retryIndex = retryHistory.size() - 1;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Retry History");
        dialog.setHeaderText("Select a retry");
        dialog.initOwner(primaryStage);

        Block head = blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
        boolean imageMode = head != null && head.role() == Role.IMAGE;

        Node previewNode;
        final TextArea[] textPreviewRef = new TextArea[1];
        final ImageView[] imagePreviewRef = new ImageView[1];
        final TextArea[] promptPreviewRef = new TextArea[1];
        if (imageMode)
        {
            ImageView imagePreview = new ImageView();
            imagePreview.setPreserveRatio(true);
            imagePreview.setSmooth(true);
            imagePreview.setFitWidth(760);
            imagePreview.setFitHeight(420);
            TextArea promptPreview = new TextArea();
            promptPreview.setWrapText(true);
            promptPreview.setEditable(false);
            promptPreview.setPrefRowCount(4);
            imagePreviewRef[0] = imagePreview;
            promptPreviewRef[0] = promptPreview;
            previewNode = new VBox(8, imagePreview, new Label("Prompt"), promptPreview);
        }
        else
        {
            TextArea textPreview = new TextArea();
            textPreview.setWrapText(true);
            textPreview.setEditable(false);
            textPreview.setPrefRowCount(8);
            textPreviewRef[0] = textPreview;
            previewNode = textPreview;
        }

        VBox content = new VBox(8, previewNode);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        ButtonType prevType = new ButtonType("<", ButtonBar.ButtonData.LEFT);
        ButtonType nextType = new ButtonType(">", ButtonBar.ButtonData.LEFT);
        ButtonType selectType = new ButtonType("Select", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(prevType, nextType, selectType, closeType);

        Button prevButton = (Button) dialog.getDialogPane().lookupButton(prevType);
        Button nextButton = (Button) dialog.getDialogPane().lookupButton(nextType);
        Button selectButton = (Button) dialog.getDialogPane().lookupButton(selectType);

        Runnable refresh = () ->
        {
            RetryHistoryEntry entry = retryHistory.get(retryIndex);
            if (imageMode)
            {
                if (entry instanceof ImageRetryHistoryEntry imageEntry)
                {
                    imagePreviewRef[0].setImage(new Image(new ByteArrayInputStream(imageEntry.bytes)));
                    promptPreviewRef[0].setText(imageEntry.prompt);
                }
            }
            else
            {
                if (entry instanceof TextRetryHistoryEntry textEntry)
                {
                    textPreviewRef[0].setText(textEntry.text);
                }
            }
            prevButton.setDisable(retryIndex <= 0);
            nextButton.setDisable(retryIndex >= retryHistory.size() - 1);
        };
        refresh.run();

        prevButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            if (retryIndex > 0)
            {
                retryIndex--;
                refresh.run();
            }
        });

        nextButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            if (retryIndex < retryHistory.size() - 1)
            {
                retryIndex++;
                refresh.run();
            }
        });

        selectButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            if (blocks.isEmpty())
            {
                dialog.close();
                return;
            }
            Block currentHead = blocks.get(blocks.size() - 1);
            RetryHistoryEntry chosen = retryHistory.get(retryIndex);
            if (currentHead.role() == Role.ASSISTANT && chosen instanceof TextRetryHistoryEntry textEntry)
            {
                if (!textEntry.text.equals(currentHead.text()))
                {
                    Block updated = new Block(currentHead.id(), currentHead.storyId(), Role.ASSISTANT, textEntry.text,
                            Timestamps.now(), currentHead.position());
                    try
                    {
                        blockRepository.replaceHead(updated);
                        blocks.set(blocks.size() - 1, updated);
                        renderStoryBlocks(true);
                    }
                    catch (SQLException e)
                    {
                        showError("Failed to apply retry selection", e);
                    }
                }
            }
            else if (currentHead.role() == Role.IMAGE && chosen instanceof ImageRetryHistoryEntry imageEntry)
            {
                try
                {
                    replaceImageBlockFromRetryHistory(currentHead, imageEntry);
                }
                catch (SQLException e)
                {
                    showError("Failed to apply retry selection", e);
                }
            }
            dialog.close();
        });

        dialog.showAndWait();
    }

    private void seedImageRetryHistoryIfNeeded(Block imageHead)
    {
        if (imageHead == null || imageHead.role() != Role.IMAGE || !retryHistory.isEmpty())
        {
            return;
        }
        StoryImage image = loadStoryImage(imageHead.text());
        if (image == null || image.imageBytes() == null || image.imageBytes().length == 0)
        {
            return;
        }
        retryHistory.add(new ImageRetryHistoryEntry(image.prompt(), image.imageBytes(), image.mimeType(), image.workflowJson()));
        retryIndex = 0;
        updateRetryCountLabel();
    }

    private void replaceImageBlockFromRetryHistory(Block headBlock, ImageRetryHistoryEntry imageEntry) throws SQLException
    {
        if (activeStory == null || headBlock == null || headBlock.role() != Role.IMAGE)
        {
            return;
        }
        ImageGenerationCoordinator.ImageMutationResult result = imageGenerationCoordinator.replaceImageFromRetryHistory(
                activeStory, headBlock, imageEntry.prompt, imageEntry.bytes, imageEntry.mimeType, imageEntry.workflowJson);
        activeStory = result.updatedStory();
        blocks = blockRepository.listForStory(activeStory.id());
        renderStoryBlocks(true);
        refreshStoryList(activeStory.id());
        statusLabel.setText("Applied image retry selection");
    }

    private void showImportCardsDialog()
    {
        if (activeStory == null)
        {
            showInfo("Select a story first.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Import Story Cards");
        dialog.setHeaderText("Import AI Dungeon story cards");
        dialog.initOwner(primaryStage);

        TextField fileField = new TextField();
        fileField.setEditable(false);
        fileField.setPromptText("Select a JSON file");

        Button browseButton = new Button("Choose File");
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        browseButton.setOnAction(event ->
        {
            java.io.File selected = chooser.showOpenDialog(primaryStage);
            if (selected != null)
            {
                fileField.setText(selected.getAbsolutePath());
            }
        });

        HBox fileRow = new HBox(8, fileField, browseButton);
        HBox.setHgrow(fileField, Priority.ALWAYS);

        CheckBox replaceBox = new CheckBox("Replace existing cards");
        replaceBox.setSelected(false);

        Button importButton = new Button("Import");
        importButton.setDisable(true);
        Button cancelButton = new Button("Cancel");

        fileField.textProperty().addListener((obs, oldValue, newValue) ->
        {
            importButton.setDisable(newValue == null || newValue.isBlank());
        });

        importButton.setOnAction(event ->
        {
            try
            {
                int imported = aiDungeonImports.importStoryCards(Path.of(fileField.getText()), activeStory.id(),
                        replaceBox.isSelected());
                refreshCardList(activeStory.id());
                dialog.close();
                showInfo("Imported " + imported + " cards.");
            }
            catch (Exception e)
            {
                showError("Failed to import cards", e);
            }
        });

        cancelButton.setOnAction(event -> dialog.close());

        HBox buttons = new HBox(8, importButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(12, fileRow, replaceBox, buttons);
        content.setPadding(new Insets(12));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        Node cancelNode = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelNode != null)
        {
            cancelNode.setVisible(false);
            cancelNode.setManaged(false);
        }
        dialog.showAndWait();
    }

    private StoryCard showAutoCardCreateDialog(StoryCard draft)
    {
        if (draft == null || activeStory == null)
        {
            return null;
        }

        Dialog<StoryCard> dialog = new Dialog<>();
        dialog.setTitle("Auto Card Preview");
        dialog.setHeaderText("Create Story Card");
        dialog.initOwner(primaryStage);

        TextField titleField = new TextField(draft.title());
        TextField triggersField = new TextField(draft.triggers());
        TextArea contentArea = new TextArea(draft.content());
        contentArea.setWrapText(true);
        contentArea.setPrefRowCount(8);
        CheckBox pinnedBox = new CheckBox("Pinned");
        pinnedBox.setSelected(draft.pinned());

        VBox content = new VBox(8,
                new Label("Title"), titleField,
                new Label("Triggers"), triggersField,
                new Label("Content"), contentArea,
                pinnedBox);
        content.setPadding(new Insets(10));

        ButtonType createType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, cancelType);
        dialog.getDialogPane().setContent(content);

        Node createButton = dialog.getDialogPane().lookupButton(createType);
        createButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            if (titleField.getText().trim().isEmpty())
            {
                showInfo("Card title cannot be empty.");
                event.consume();
                return;
            }
            if (contentArea.getText().trim().isEmpty())
            {
                showInfo("Card content cannot be empty.");
                event.consume();
            }
        });

        dialog.setResultConverter(buttonType ->
        {
            if (buttonType != createType)
            {
                return null;
            }
            String title = titleField.getText().trim();
            String triggers = triggersField.getText().trim();
            String contentText = contentArea.getText().trim();
            return new StoryCard(draft.id(), activeStory.id(), title, triggers, contentText, pinnedBox.isSelected());
        });

        return dialog.showAndWait().orElse(null);
    }

    private String showAutoCardUpdateDialog(StoryCard existing, String proposedContent, boolean summarized)
    {
        if (existing == null)
        {
            return null;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Auto Card Preview");
        dialog.setHeaderText(summarized ? "Summarize Story Card" : "Update Story Card");
        dialog.initOwner(primaryStage);

        Label titleLabel = new Label(existing.title());
        titleLabel.setStyle("-fx-font-weight: bold;");

        TextArea oldArea = new TextArea(existing.content());
        oldArea.setEditable(false);
        oldArea.setWrapText(true);
        oldArea.setPrefRowCount(10);

        TextArea newArea = new TextArea(proposedContent);
        newArea.setWrapText(true);
        newArea.setPrefRowCount(10);

        VBox oldBox = new VBox(6, new Label("Existing"), oldArea);
        VBox newBox = new VBox(6, new Label("Proposed"), newArea);
        oldBox.setPrefWidth(300);
        newBox.setPrefWidth(300);

        HBox panes = new HBox(10, oldBox, newBox);
        HBox.setHgrow(oldBox, Priority.ALWAYS);
        HBox.setHgrow(newBox, Priority.ALWAYS);

        VBox content = new VBox(8, titleLabel, panes);
        content.setPadding(new Insets(10));

        ButtonType updateType = new ButtonType("Update", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(updateType, cancelType);
        dialog.getDialogPane().setContent(content);

        Node updateButton = dialog.getDialogPane().lookupButton(updateType);
        updateButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            if (newArea.getText().trim().isEmpty())
            {
                showInfo("Updated content cannot be empty.");
                event.consume();
            }
        });

        dialog.setResultConverter(buttonType ->
        {
            if (buttonType != updateType)
            {
                return null;
            }
            return newArea.getText().trim();
        });

        return dialog.showAndWait().orElse(null);
    }

    private <T> T runOnUiThreadAndWait(java.util.concurrent.Callable<T> action)
    {
        if (Platform.isFxApplicationThread())
        {
            try
            {
                return action.call();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() ->
        {
            try
            {
                future.complete(action.call());
            }
            catch (Exception e)
            {
                future.completeExceptionally(e);
            }
        });

        try
        {
            return future.get();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return null;
        }
        catch (ExecutionException e)
        {
            throw new RuntimeException(e.getCause());
        }
    }

    private void populateStoryDetails(Story story)
    {
        if (story == null)
        {
            systemPromptArea.setText("");
            plotEssentialsArea.setText("");
            authorNoteArea.setText("");
            return;
        }
        systemPromptArea.setText(story.systemPrompt());
        plotEssentialsArea.setText(story.plotEssentials());
        authorNoteArea.setText(story.authorNote());
    }

    private void setStoryDependentControlsEnabled(boolean enabled)
    {
        continueButton.setDisable(!enabled);
        takeTurnButton.setDisable(!enabled);
        retryButton.setDisable(!enabled);
        deleteButton.setDisable(!enabled);
        retryHistoryButton.setDisable(!enabled || retryHistory.size() < 2);
        seeButton.setDisable(!enabled);
        systemPromptArea.setDisable(!enabled);
        plotEssentialsArea.setDisable(!enabled);
        authorNoteArea.setDisable(!enabled);
        newCardButton.setDisable(!enabled);
        generateCardButton.setDisable(!enabled);
        autoCardsRunButton.setDisable(!enabled);
        importCardsButton.setDisable(!enabled);
        cardList.setDisable(!enabled);
        autoCardsEnabledBox.setDisable(!enabled);
        autoCardsUpdateExistingBox.setDisable(!enabled);
        autoCardsCreateNewBox.setDisable(!enabled);
        autoCardsPinNewBox.setDisable(!enabled);
        autoCardsPreviewBox.setDisable(!enabled);
    }

    private void submitTurn()
    {
        if (activeStory == null)
        {
            showInfo("Select a story first.");
            return;
        }
        String text = turnInputArea.getText().trim();
        if (text.isEmpty())
        {
            showInfo("Turn text cannot be empty.");
            return;
        }
        showTurnInput(false);
        turnInputArea.clear();
        clearRetryHistory();
        runTurn(text);
    }

    private void runContinue()
    {
        if (activeStory == null)
        {
            showInfo("Select a story first.");
            return;
        }

        clearRetryHistory();
        setStoryActionButtonsBusy(true);
        statusLabel.setText("Generating...");

        Task<Block> task = new Task<>()
        {
            @Override
            protected Block call() throws Exception
            {
                settings = buildGenerationSettings();
                GenerationCoordinator.ContinueResult result = generationCoordinator.continueStory(activeStory, settings,
                        App.this::runAutoCardsForGeneration);
                observePromptCalibration(result.estimatedPromptTokens());
                activeStory = result.updatedStory();
                return result.block();
            }
        };

        task.setOnSucceeded(event ->
        {
            Block block = task.getValue();
            if (block == null)
            {
                restoreStoryActionButtonsState();
                statusLabel.setText("Last generation was empty.");
                return;
            }
            blocks.add(block);
            renderStoryBlocks(true);
            restoreStoryActionButtonsState();
            statusLabel.setText("Ready");
            refreshStoryList(activeStory.id());
            refreshCardList(activeStory.id());
        });

        task.setOnFailed(event ->
        {
            Throwable error = task.getException();
            restoreStoryActionButtonsState();
            statusLabel.setText("Error: " + (error == null ? "Unknown" : error.getMessage()));
        });

        executor.submit(task);
    }

    private void runTurn(String userText)
    {
        setStoryActionButtonsBusy(true);
        statusLabel.setText("Generating...");

        Task<Boolean> task = new Task<>()
        {
            @Override
            protected Boolean call() throws Exception
            {
                settings = buildGenerationSettings();
                GenerationCoordinator.TurnResult result = generationCoordinator.takeTurn(activeStory, userText, settings,
                        App.this::runAutoCardsForGeneration);
                observePromptCalibration(result.estimatedPromptTokens());
                activeStory = result.updatedStory();
                return result.generated();
            }
        };

        task.setOnSucceeded(event ->
        {
            try
            {
                boolean generated = Boolean.TRUE.equals(task.getValue());
                blocks = blockRepository.listForStory(activeStory.id());
                renderStoryBlocks(true);
                statusLabel.setText(generated ? "Ready" : "Last generation was empty.");
                refreshStoryList(activeStory.id());
                refreshCardList(activeStory.id());
            }
            catch (SQLException e)
            {
                showError("Failed to reload story", e);
            }
            finally
            {
                restoreStoryActionButtonsState();
            }
        });

        task.setOnFailed(event ->
        {
            Throwable error = task.getException();
            restoreStoryActionButtonsState();
            statusLabel.setText("Error: " + (error == null ? "Unknown" : error.getMessage()));
        });

        executor.submit(task);
    }

    private Slider buildIntSlider(int min, int max, int value, int step)
    {
        Slider slider = new Slider(min, max, value);
        slider.setShowTickMarks(false);
        slider.setShowTickLabels(false);
        slider.setBlockIncrement(step);
        slider.setSnapToTicks(true);
        slider.setMajorTickUnit(step);
        slider.setMinorTickCount(0);
        slider.setOrientation(Orientation.HORIZONTAL);
        return slider;
    }

    private Slider buildDoubleSlider(double min, double max, double value, double step)
    {
        Slider slider = new Slider(min, max, value);
        slider.setShowTickMarks(false);
        slider.setShowTickLabels(false);
        slider.setBlockIncrement(step);
        slider.setSnapToTicks(true);
        slider.setMajorTickUnit(step);
        slider.setMinorTickCount(0);
        slider.setOrientation(Orientation.HORIZONTAL);
        return slider;
    }

    private VBox sliderRow(String labelText, Slider slider, Label valueLabel, java.util.function.Consumer<Number> handler)
    {
        Label label = new Label(labelText);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, label, spacer, valueLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(6, header, slider);
        slider.valueProperty().addListener((obs, oldValue, newValue) ->
        {
            valueLabel.setText(formatValue(newValue, valueLabel));
            handler.accept(newValue);
        });
        return box;
    }

    private VBox textFieldRow(String labelText, TextField field)
    {
        Label label = new Label(labelText);
        VBox box = new VBox(6, label, field);
        return box;
    }

    private VBox comboRow(String labelText, ComboBox<String> comboBox)
    {
        Label label = new Label(labelText);
        VBox box = new VBox(6, label, comboBox);
        return box;
    }

    private VBox spinnerRow(String labelText, Spinner<Integer> spinner, java.util.function.Consumer<Integer> handler)
    {
        Label label = new Label(labelText);
        VBox box = new VBox(6, label, spinner);
        spinner.valueProperty().addListener((obs, oldValue, newValue) -> handler.accept(newValue));
        return box;
    }

    private Spinner<Integer> buildSpinner(int min, int max, int value)
    {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, value));
        spinner.setEditable(true);
        spinner.setMaxWidth(Double.MAX_VALUE);
        return spinner;
    }

    private Label valueLabel(double value, String suffix)
    {
        Label label = new Label(formatValue(value, suffix));
        return label;
    }

    private Label valueLabel(double value, String suffix, int decimals)
    {
        Label label = new Label(formatValue(value, suffix, decimals));
        label.setUserData(decimals);
        return label;
    }

    private String formatValue(Number value, Label label)
    {
        String text = label.getText();
        String suffix = "";
        int space = text.lastIndexOf(' ');
        if (space > -1)
        {
            suffix = text.substring(space + 1);
        }
        int decimals = 2;
        Object data = label.getUserData();
        if (data instanceof Integer)
        {
            decimals = (int) data;
        }
        return formatValue(value.doubleValue(), suffix.equals("") ? "" : suffix, decimals);
    }

    private String formatValue(double value, String suffix)
    {
        return formatValue(value, suffix, 2);
    }

    private String formatValue(double value, String suffix, int decimals)
    {
        String formatted;
        if (Math.abs(value - Math.round(value)) < 0.0001)
        {
            formatted = String.valueOf((int) Math.round(value));
        }
        else
        {
            String pattern = "%." + Math.max(0, decimals) + "f";
            formatted = String.format(java.util.Locale.US, pattern, value);
        }
        if (!suffix.isEmpty())
        {
            return formatted + " " + suffix;
        }
        return formatted;
    }

    private int percentFromSettings()
    {
        if (appSettings.contextLimit() == 0)
        {
            return 0;
        }
        return (int) Math.round((appSettings.minStoryWindow() * 100.0) / appSettings.contextLimit());
    }

    private void updateContextLimit(int value)
    {
        int percent = minStoryPercentSlider == null ? 90 : (int) Math.round(minStoryPercentSlider.getValue());
        appSettings = SettingsCoordinator.withContextLimit(appSettings, value, percent);
        persistAppSettings();
        refreshGenerationSettings();
    }

    private void updateResponseLength(int value)
    {
        appSettings = SettingsCoordinator.withResponseLength(appSettings, value);
        persistAppSettings();
        refreshGenerationSettings();
    }

    private void updateTemperature(double value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withTemperature(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateTopK(int value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withTopK(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateTopP(double value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withTopP(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateMinP(double value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withMinP(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updatePresencePenalty(double value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withPresencePenalty(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateFrequencyPenalty(double value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withFrequencyPenalty(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateRepetitionPenalty(double value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withRepetitionPenalty(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateMinStoryPercent(int percent)
    {
        appSettings = SettingsCoordinator.withMinStoryPercent(appSettings, percent);
        persistAppSettings();
        refreshGenerationSettings();
    }

    private void updateStoryCardLookback(int value)
    {
        appSettings = SettingsCoordinator.withStoryCardLookback(appSettings, value);
        persistAppSettings();
        refreshGenerationSettings();
    }

    private void updateAnPlacement(int value)
    {
        appSettings = SettingsCoordinator.withAnPlacement(appSettings, value);
        persistAppSettings();
        refreshGenerationSettings();
    }

    private void persistAppSettings()
    {
        if (appSettingsRepository == null)
        {
            return;
        }
        try
        {
            appSettingsRepository.save(appSettings);
        }
        catch (SQLException e)
        {
            showError("Failed to save settings", e);
        }
    }

    private void persistModelSettings()
    {
        if (modelSettingsRepository == null || activeModelSettings == null)
        {
            return;
        }
        try
        {
            modelSettingsRepository.save(activeModelSettings);
        }
        catch (SQLException e)
        {
            showError("Failed to save model settings", e);
        }
    }

    private void persistAppAutoCardsSettings()
    {
        if (appAutoCardsRepository == null || appAutoCardsSettings == null)
        {
            return;
        }
        try
        {
            appAutoCardsRepository.save(appAutoCardsSettings);
        }
        catch (SQLException e)
        {
            showError("Failed to save auto cards settings", e);
        }
    }

    private void persistStoryAutoCardsSettings()
    {
        if (storyAutoCardsRepository == null || storyAutoCardsSettings == null)
        {
            return;
        }
        try
        {
            storyAutoCardsRepository.save(storyAutoCardsSettings);
        }
        catch (SQLException e)
        {
            showError("Failed to save auto cards settings", e);
        }
    }

    private void persistModelAutoCardsSettings()
    {
        if (modelAutoCardsRepository == null || modelAutoCardsSettings == null)
        {
            return;
        }
        try
        {
            modelAutoCardsRepository.save(modelAutoCardsSettings);
        }
        catch (SQLException e)
        {
            showError("Failed to save auto cards settings", e);
        }
    }

    private void refreshGenerationSettings()
    {
        settings = buildGenerationSettings();
    }

    private void clearRetryHistory()
    {
        retryHistory.clear();
        retryIndex = -1;
        updateRetryCountLabel();
    }

    private void updateRetryCountLabel()
    {
        int count = Math.max(0, retryHistory.size() - 1);
        boolean hasRetries = count > 0;
        retryHistoryButton.setText(hasRetries ? String.valueOf(count) : "");
        retryHistoryButton.setVisible(hasRetries);
        retryHistoryButton.setManaged(hasRetries);
    }

    private static double roundTo(double value, double step)
    {
        return Math.round(value / step) * step;
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

    private void renderStoryBlocks(boolean forceScroll)
    {
        storyRows.clear();
        if (blocks.isEmpty())
        {
            return;
        }

        String latestAssistantId = findLatestAssistantId();
        List<Block> assistantGroup = new ArrayList<>();

        for (Block block : blocks)
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

    private void addAssistantGroup(List<Block> group, String latestAssistantId)
    {
        if (group.isEmpty())
        {
            return;
        }
        List<List<Block>> chunks = splitAssistantGroup(group);
        for (List<Block> chunk : chunks)
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

        for (int i = 0; i < group.size(); i++)
        {
            Block block = group.get(i);
            boolean highlight = block.id().equals(latestAssistantId);
            Text textNode = new Text(block.text());
            textNode.setFill(javafx.scene.paint.Color.web("#e6e1d8"));
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

        for (int i = 0; i < group.size(); i++)
        {
            Block block = group.get(i);
            int blockChars = block.text() == null ? 0 : block.text().length();
            current.add(block);
            charCount += blockChars;

            boolean softLimitReached = current.size() >= ASSISTANT_FLOW_CHUNK_BLOCK_LIMIT
                    || charCount >= ASSISTANT_FLOW_CHUNK_CHAR_LIMIT;
            boolean hardLimitReached = current.size() >= ASSISTANT_FLOW_CHUNK_HARD_BLOCK_LIMIT
                    || charCount >= ASSISTANT_FLOW_CHUNK_HARD_CHAR_LIMIT;
            boolean hasNext = i + 1 < group.size();
            if (!hasNext)
            {
                continue;
            }

            Block next = group.get(i + 1);
            boolean naturalBoundary = hasHardBreakBetween(block, next);
            boolean shouldSplit = hardLimitReached || (softLimitReached && naturalBoundary);
            if (!shouldSplit)
            {
                continue;
            }
            chunks.add(current);
            current = new ArrayList<>();
            charCount = 0;
        }

        if (!current.isEmpty())
        {
            chunks.add(current);
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
        // editor.setPrefRowCount(4);
        editor.setMinHeight(Region.USE_PREF_SIZE);
        // editor.setPrefHeight(Region.USE_COMPUTED_SIZE);
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
        javafx.application.Platform.runLater(() ->
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

        javafx.application.Platform.runLater(() ->
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
        persistBlockTextAsync(blockId, normalized, () ->
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
            showError("Failed to update block", e);
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
                String newText = editor.getText();
                commitInlineEdit(block.id(), newText, label, editor, row, false);
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

        StoryImage storyImage = loadStoryImage(block.text());
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
        preview.setOnMouseClicked(event -> showImageBlockDialog(block, storyImage, image));
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

    private StoryImage loadStoryImage(String imageId)
    {
        try
        {
            return imageGenerationCoordinator.loadStoryImage(imageId);
        }
        catch (SQLException e)
        {
            showError("Failed to load image", e);
        }
        return null;
    }

    private void showImageBlockDialog(Block block, StoryImage storyImage, Image image)
    {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Story Image");
        dialog.setHeaderText("Image block");
        dialog.initOwner(primaryStage);
        dialog.setResizable(true);

        ImageView large = new ImageView(image);
        large.setPreserveRatio(true);
        large.setSmooth(true);
        large.setFitWidth(860);
        large.setFitHeight(520);

        TextArea promptArea = new TextArea(storyImage.prompt());
        promptArea.setWrapText(true);
        promptArea.setEditable(false);
        promptArea.setPrefRowCount(5);

        VBox content = new VBox(10, large, new Label("Prompt"), promptArea);
        content.setPadding(new Insets(10));
        content.setMaxWidth(980);
        ScrollPane contentScroll = new ScrollPane(content);
        contentScroll.setFitToWidth(true);
        contentScroll.setFitToHeight(false);
        contentScroll.setPannable(true);
        contentScroll.setPrefViewportWidth(980);
        contentScroll.setPrefViewportHeight(660);
        dialog.getDialogPane().setContent(contentScroll);
        dialog.getDialogPane().setPrefWidth(1040);
        dialog.getDialogPane().setPrefHeight(720);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.LEFT);
        ButtonType deleteType = new ButtonType("Delete", ButtonBar.ButtonData.LEFT);
        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, deleteType, closeType);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            saveStoryImageToFile(storyImage);
        });

        Button deleteButtonLocal = (Button) dialog.getDialogPane().lookupButton(deleteType);
        deleteButtonLocal.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            if (deleteBlockAndLinkedImage(block, false))
            {
                dialog.close();
            }
        });

        dialog.showAndWait();
    }

    private void saveStoryImageToFile(StoryImage storyImage)
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Files", "*.png"));
        chooser.setInitialFileName("llamaquill-image-" + storyImage.id() + ".png");
        java.io.File selected = chooser.showSaveDialog(primaryStage);
        if (selected == null)
        {
            return;
        }
        try
        {
            Files.write(selected.toPath(), storyImage.imageBytes());
            statusLabel.setText("Saved image to " + selected.getName());
        }
        catch (IOException e)
        {
            showError("Failed to save image", e);
        }
    }

    private boolean deleteBlockAndLinkedImage(Block block, boolean forceScroll)
    {
        if (block == null)
        {
            return false;
        }
        try
        {
            blockRepository.deleteById(block.id());
            deleteLinkedImageIfPresent(block);
            blocks = blockRepository.listForStory(activeStory.id());
            renderStoryBlocks(forceScroll);
            clearRetryHistory();
            statusLabel.setText("Ready");
            return true;
        }
        catch (SQLException e)
        {
            showError("Failed to delete block", e);
            return false;
        }
    }

    private void deleteLinkedImageIfPresent(Block block) throws SQLException
    {
        if (block == null || block.role() != Role.IMAGE)
        {
            return;
        }
        imageGenerationCoordinator.deleteImageById(block.text());
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

        persistBlockTextAsync(blockId, normalized,
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
                    showError("Failed to update block", e);
                });
    }

    private Block updateBlockText(String blockId, String text)
    {
        for (int i = 0; i < blocks.size(); i++)
        {
            Block block = blocks.get(i);
            if (!block.id().equals(blockId))
            {
                continue;
            }
            if (text.equals(block.text()))
            {
                return block;
            }
            Block updated = new Block(block.id(), block.storyId(), block.role(), text, block.createdAt(), block.position());
            blocks.set(i, updated);
            return updated;
        }
        return null;
    }

    private Block findBlockById(String blockId)
    {
        for (Block block : blocks)
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
        updatedText.setFill(javafx.scene.paint.Color.web("#e6e1d8"));
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

    private void persistBlockTextAsync(String blockId, String text, Runnable onSuccess, Consumer<Exception> onFailure)
    {
        if (executor == null)
        {
            try
            {
                blockRepository.updateText(blockId, text);
                onSuccess.run();
            }
            catch (SQLException e)
            {
                onFailure.accept(e);
            }
            return;
        }

        CompletableFuture.runAsync(() ->
        {
            try
            {
                blockRepository.updateText(blockId, text);
            }
            catch (SQLException e)
            {
                throw new RuntimeException(e);
            }
        }, executor).thenRun(() -> Platform.runLater(onSuccess)).exceptionally(throwable ->
        {
            Platform.runLater(() -> onFailure.accept(asException(throwable)));
            return null;
        });
    }

    private Exception asException(Throwable throwable)
    {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null)
        {
            current = current.getCause();
        }
        if (current instanceof Exception e)
        {
            return e;
        }
        return new RuntimeException(current);
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

    private void scrollToBottom()
    {
        javafx.application.Platform.runLater(() ->
        {
            if (storyRows.isEmpty())
            {
                return;
            }
            storyListView.refresh();
            storyListView.applyCss();
            storyListView.layout();
            storyListView.scrollTo(storyRows.size() - 1);
            javafx.application.Platform.runLater(() ->
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
        if (storyListView == null)
        {
            return;
        }
        storyListView.getSelectionModel().clearSelection();
    }

    private void refreshStoryLayoutPreserveViewport()
    {
        if (storyListView == null)
        {
            return;
        }
        ScrollBar before = findStoryVerticalScrollBar();
        double previousValue = before == null ? Double.NaN : before.getValue();
        boolean stickToBottom = before != null && previousValue >= (before.getMax() - 0.5);
        javafx.application.Platform.runLater(() ->
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
        if (storyViewportRefreshDebounce == null)
        {
            refreshStoryLayoutPreserveViewport();
            return;
        }
        storyViewportRefreshDebounce.playFromStart();
    }

    private void refreshStoryRow(Region row)
    {
        if (storyListView == null || row == null)
        {
            return;
        }
        int index = storyRows.indexOf(row);
        if (index < 0)
        {
            return;
        }
        storyRows.set(index, row);
    }

    private ScrollBar findStoryVerticalScrollBar()
    {
        if (storyListView == null)
        {
            return null;
        }
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
        if (storyListView == null)
        {
            return;
        }
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
        if (storyContentWidthBinding != null)
        {
            return storyContentWidthBinding;
        }
        return storyListView.widthProperty().subtract(32);
    }

    private DoubleBinding rowContentWidthBinding()
    {
        if (storyRowContentWidthBinding != null)
        {
            return storyRowContentWidthBinding;
        }
        return contentWidthBinding().subtract(24);
    }

    private void logAutoCardsError(String message, Exception e)
    {
        System.err.println(message + ": " + e.getMessage());
    }

    private void showError(String message, Exception e)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(primaryStage);
        alert.setTitle("LlamaQuill");
        alert.setHeaderText(message);
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }

    private void showInfo(String message)
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(primaryStage);
        alert.setTitle("LlamaQuill");
        alert.setHeaderText(message);
        alert.showAndWait();
    }

    public static void main(String[] args)
    {
        launch(args);
    }
}
