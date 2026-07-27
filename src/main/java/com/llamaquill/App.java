package com.llamaquill;

import com.llamaquill.autocards.AutoCards;
import com.llamaquill.autocards.AutoCardsCoordinator;
import com.llamaquill.autocards.AutoCardsDialogs;
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
import com.llamaquill.generation.PromptDialog;
import com.llamaquill.generation.StoryPromptCoordinator;
import com.llamaquill.image.ImageGenerationCoordinator;
import com.llamaquill.image.SeeDialog;
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
import com.llamaquill.imports.ImportDialogs;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.serviceClients.ComfyUiClient;
import com.llamaquill.serviceClients.OllamaChatResult;
import com.llamaquill.serviceClients.OllamaClient;
import com.llamaquill.serviceClients.OllamaEndpoint;
import com.llamaquill.serviceClients.OllamaException;
import com.llamaquill.serviceClients.OllamaModelDetails;
import com.llamaquill.retry.RetryHistoryDialog;
import com.llamaquill.session.StoryOperationRegistry;
import com.llamaquill.session.StoryRetryHistory;
import com.llamaquill.session.StorySession;
import com.llamaquill.settings.SettingsCoordinator;
import com.llamaquill.stories.StoryDialogs;
import com.llamaquill.storycards.StoryCardDialogs;
import com.llamaquill.storyview.StoryPaneController;
import com.llamaquill.util.Ids;
import com.llamaquill.util.Timestamps;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class App extends Application
{
    private static final String DEFAULT_SYSTEM_PROMPT = "You're a masterful storyteller and gamemaster. "
            + "Write in second person present tense (You are), crafting vivid, engaging narratives with authority and confidence.";

    private static final int MAX_STORY_LINES = 20;
    private static final int LEFT_SIDEBAR_WIDTH = 480;
    private static final int RIGHT_SIDEBAR_WIDTH = 480;
    private static final List<String> BUNDLED_COMFY_WORKFLOW_NAMES =
            List.of("ChromaHD", "Chroma2Kaleidoscope");

    private Database database;
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
    private StoryPromptCoordinator storyPromptCoordinator;
    private ImageGenerationCoordinator imageGenerationCoordinator;
    private AutoCardsService autoCardsService;
    private AutoCardsCoordinator autoCardsCoordinator;
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
    private StorySession activeSession;
    private long storySessionRevision;
    private final StoryOperationRegistry storyOperations = new StoryOperationRegistry();
    private final Set<Task<?>> backgroundTasks = ConcurrentHashMap.newKeySet();

    private final ObservableList<Story> storyItems = FXCollections.observableArrayList();
    private final ObservableList<StoryCard> cardItems = FXCollections.observableArrayList();

    private Stage primaryStage;
    private StoryPaneController storyPaneController;
    private Label statusLabel;
    private Button continueButton;
    private Button takeTurnButton;
    private Button retryButton;
    private Button deleteButton;
    private Button retryHistoryButton;
    private Button seeButton;
    private Button promptButton;

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

    private final StoryRetryHistory<RetryHistoryEntry> retryHistory = new StoryRetryHistory<>();

    private Slider contextLimitSlider;
    private CheckBox responseLengthEnabledBox;
    private Slider responseLengthSlider;
    private CheckBox temperatureEnabledBox;
    private Slider temperatureSlider;
    private CheckBox topKEnabledBox;
    private Slider topKSlider;
    private CheckBox topPEnabledBox;
    private Slider topPSlider;
    private CheckBox minPEnabledBox;
    private Slider minPSlider;
    private CheckBox typicalPEnabledBox;
    private Slider typicalPSlider;
    private CheckBox presencePenaltyEnabledBox;
    private Slider presencePenaltySlider;
    private CheckBox frequencyPenaltyEnabledBox;
    private Slider frequencyPenaltySlider;
    private CheckBox repeatLastNEnabledBox;
    private Slider repeatLastNSlider;
    private CheckBox repetitionPenaltyEnabledBox;
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
    private Button refreshModelsButton;
    private Label modelDetailsLabel;
    private Label contextLimitValueLabel;
    private boolean updatingModelControls;
    private boolean modelRefreshInProgress;

    private List<String> comfyWorkflowNames = new ArrayList<>();
    private static final double TOKEN_SCALE_ALPHA = 0.2;
    private final Map<String, OllamaModelDetails> modelDetailsByName = new HashMap<>();

    private static final String DEFAULT_SEE_REQUEST = "Generate an image prompt for the most recent scene in the story.";
    private static final String DEFAULT_ONE_SHOT_SYSTEM_PROMPT = "You respond to the user's prompt using the existing story context.";

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

    private record AutoCardsRunContext(StorySession session, Story story, AppSettings appSettings,
            StoryAutoCardsSettings storySettings, AppAutoCardsSettings appAutoCardsSettings,
            ModelSettings modelSettings, ModelAutoCardsSettings modelAutoCardsSettings)
    {
    }

    private record ModelDiscoveryResult(String endpoint, List<String> models, String selectedModel,
            OllamaModelDetails selectedDetails, Exception metadataError)
    {
    }

    @Override
    public void start(Stage stage)
    {
        this.primaryStage = stage;
        try
        {
            database = Database.open();
            storyRepository = new StoryRepository(database);
            blockRepository = new BlockRepository(database);
            cardRepository = new StoryCardRepository(database);
            ImageRepository imageRepository = new ImageRepository(database);
            appSettingsRepository = new AppSettingsRepository(database);
            modelSettingsRepository = new ModelSettingsRepository(database);
            appAutoCardsRepository = new AppAutoCardsRepository(database);
            storyAutoCardsRepository = new StoryAutoCardsRepository(database);
            modelAutoCardsRepository = new ModelAutoCardsRepository(database);
            promptCompiler = new PromptCompiler();
            aiDungeonImports = new AIDungeonImports(database, storyRepository, blockRepository, cardRepository,
                    DEFAULT_SYSTEM_PROMPT);
            ollamaClient = new OllamaClient();
            comfyUiClient = new ComfyUiClient();
            generationCoordinator = new GenerationCoordinator(database, blockRepository, storyRepository, cardRepository,
                    promptCompiler, ollamaClient);
            autoCardsService = new AutoCardsService(ollamaClient, promptCompiler);
            autoCardsCoordinator = new AutoCardsCoordinator(blockRepository, cardRepository, autoCardsService);
            storyPromptCoordinator = new StoryPromptCoordinator(blockRepository, cardRepository, autoCardsService);
            imageGenerationCoordinator = new ImageGenerationCoordinator(database, imageRepository, blockRepository,
                    storyRepository, cardRepository, autoCardsService, comfyUiClient);
            appSettings = loadOrCreateAppSettings();
            appAutoCardsSettings = loadOrCreateAppAutoCardsSettings();
            refreshComfyWorkflowNames();
            ensureValidComfyWorkflowSelection();
            ollamaClient.setHost(appSettings.ollamaUrl());
            comfyUiClient.setHost(appSettings.comfyUiUrl());
            activeModelSettings = loadActiveModelSettings(appSettings.selectedModel());
            modelAutoCardsSettings = loadOrCreateModelAutoCardsSettings(activeModelSettings.modelName());
            ollamaClient.setModel(activeModelSettings.modelName());
            settings = buildGenerationSettings();
            executor = Executors.newSingleThreadExecutor();

            activeStory = loadOrCreateStory();
            blocks = blockRepository.listForStory(activeStory.id());
            activateStorySession();
            storyAutoCardsSettings = loadOrCreateStoryAutoCardsSettings(activeStory.id());
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Failed to initialize database", e);
        }

        continueButton = new Button("Continue");
        continueButton.setOnAction(event -> runContinue());

        takeTurnButton = new Button("Take A Turn");

        retryButton = new Button("Retry");
        retryButton.setOnAction(event -> runRetry());

        deleteButton = new Button("Erase");
        deleteButton.setOnAction(event -> deleteHeadBlock());

        retryHistoryButton = new Button("");
        retryHistoryButton.setOnAction(event -> showRetryDialog());
        updateRetryCountLabel();

        seeButton = new Button("See");
        seeButton.setOnAction(event -> showSeeDialog());

        promptButton = new Button("Prompt");
        promptButton.setOnAction(event -> showPromptDialog());

        storyPaneController = new StoryPaneController(
                primaryStage,
                this::submitTurn,
                this::loadStoryImage,
                this::saveStoryImageToFile,
                this::deleteBlockAndLinkedImage,
                this::persistBlockTextAsync,
                this::showError);
        takeTurnButton.setOnAction(event -> showTurnInput(true));

        statusLabel = new Label("Ready");

        var statusBar = new HBox(statusLabel);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(8, 12, 8, 12));

        var root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setLeft(buildStorySidebar());
        root.setCenter(storyPaneController.buildCenterPane(
                takeTurnButton, continueButton, seeButton, retryButton, retryHistoryButton, deleteButton, promptButton));
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
        stage.setTitle(AppVersion.displayName());
        stage.setScene(scene);
        stage.show();
        refreshModelsFromOllama(false);
    }

    @Override
    public void stop()
    {
        storyOperations.cancelAll();
        for (Task<?> task : backgroundTasks)
        {
            task.cancel(true);
        }
        backgroundTasks.clear();
        if (executor != null)
        {
            executor.shutdownNow();
            try
            {
                executor.awaitTermination(3, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
        if (ollamaClient != null)
        {
            ollamaClient.close();
        }
        if (database != null)
        {
            try
            {
                database.close();
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

    private void showTurnInput(boolean show)
    {
        if (storyPaneController != null)
        {
            storyPaneController.showTurnInput(show);
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
            if (!updatingModelControls && selected != null)
            {
                selectModel(selected);
            }
        });
        refreshModelsButton = new Button("Refresh");
        refreshModelsButton.setOnAction(event -> refreshModelsFromOllama(true));
        modelDetailsLabel = new Label("Model metadata has not been loaded.");
        modelDetailsLabel.setWrapText(true);

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

        autoCardsMaxTokensCreate = buildSpinner(32, 2048, 256);
        autoCardsMaxTokensUpdate = buildSpinner(32, 2048, 192);
        autoCardsMaxTokensSummarize = buildSpinner(32, 2048, 192);
        autoCardsMaxTokensCreate.valueProperty().addListener((obs, oldValue, newValue) ->
                updateModelAutoCardsTokens());
        autoCardsMaxTokensUpdate.valueProperty().addListener((obs, oldValue, newValue) ->
                updateModelAutoCardsTokens());
        autoCardsMaxTokensSummarize.valueProperty().addListener((obs, oldValue, newValue) ->
                updateModelAutoCardsTokens());

        contextLimitSlider = buildIntSlider(ModelSettings.MIN_CONTEXT_LIMIT,
                Math.max(131072, activeModelSettings.contextLimit()), activeModelSettings.contextLimit(), 512);
        contextLimitValueLabel = valueLabel(activeModelSettings.contextLimit(), "tokens");
        responseLengthSlider = buildIntSlider(1, 32768, appSettings.responseLength(), 1);
        temperatureSlider = buildDoubleSlider(0.0, 5.0, activeModelSettings.temperature(), 0.1);
        topKSlider = buildIntSlider(0, 10000, activeModelSettings.topK(), 1);
        topPSlider = buildDoubleSlider(0.0, 1.0, activeModelSettings.topP(), 0.01);
        minPSlider = buildDoubleSlider(0.0, 1.0, activeModelSettings.minP(), 0.001);
        typicalPSlider = buildDoubleSlider(0.0, 1.0, activeModelSettings.typicalP(), 0.01);
        presencePenaltySlider = buildDoubleSlider(-2.0, 2.0, activeModelSettings.presencePenalty(), 0.01);
        frequencyPenaltySlider = buildDoubleSlider(-2.0, 2.0, activeModelSettings.frequencyPenalty(), 0.01);
        repeatLastNSlider = buildIntSlider(-1, activeModelSettings.contextLimit(),
                activeModelSettings.repeatLastN(), 1);
        repetitionPenaltySlider = buildDoubleSlider(0.0, 5.0, activeModelSettings.repetitionPenalty(), 0.01);
        responseLengthEnabledBox = optionCheckBox("Response Length", appSettings.responseLengthEnabled());
        responseLengthEnabledBox.setTooltip(new Tooltip(
                "When disabled, Ollama chooses the response length and LlamaQuill reserves 200 context tokens for output."));
        temperatureEnabledBox = optionCheckBox("Temperature", activeModelSettings.temperatureEnabled());
        topKEnabledBox = optionCheckBox("Top K", activeModelSettings.topKEnabled());
        topPEnabledBox = optionCheckBox("Top P", activeModelSettings.topPEnabled());
        minPEnabledBox = optionCheckBox("Min P", activeModelSettings.minPEnabled());
        typicalPEnabledBox = optionCheckBox("Typical P", activeModelSettings.typicalPEnabled());
        typicalPEnabledBox.setTooltip(new Tooltip(
                "Locally typical sampling. 1.0 has no effect; disabling leaves the value to Ollama and the model."));
        presencePenaltyEnabledBox = optionCheckBox("Presence Penalty", activeModelSettings.presencePenaltyEnabled());
        frequencyPenaltyEnabledBox = optionCheckBox("Frequency Penalty", activeModelSettings.frequencyPenaltyEnabled());
        repeatLastNEnabledBox = optionCheckBox("Repeat Last N", activeModelSettings.repeatLastNEnabled());
        repeatLastNEnabledBox.setTooltip(new Tooltip(
                "Tokens checked by repetition penalty. 0 disables the lookback; -1 uses the full context."));
        repetitionPenaltyEnabledBox = optionCheckBox("Repetition Penalty",
                activeModelSettings.repetitionPenaltyEnabled());
        minStoryPercentSlider = buildIntSlider(10, 100, percentFromSettings(), 1);
        storyCardLookbackSpinner = buildSpinner(0, 100, appSettings.storyCardLookback());
        anPlacementSpinner = buildSpinner(1, 100, appSettings.anPlacement());

        Label databaseLocationLabel = new Label(database.paths().databaseFile().toString());
        databaseLocationLabel.setWrapText(true);
        Button backupDatabaseButton = new Button("Back Up Database");
        backupDatabaseButton.setMaxWidth(Double.MAX_VALUE);
        backupDatabaseButton.setOnAction(event ->
        {
            try
            {
                Path backup = database.createBackup();
                showInfo("Database backup created:\n" + backup);
            }
            catch (SQLException e)
            {
                showError("Failed to back up database", e);
            }
        });
        Button checkDatabaseButton = new Button("Check Database");
        checkDatabaseButton.setMaxWidth(Double.MAX_VALUE);
        checkDatabaseButton.setOnAction(event ->
        {
            try
            {
                Database.Diagnostics diagnostics = database.diagnostics();
                showInfo("Database: " + diagnostics.databaseFile()
                        + "\nSchema: " + diagnostics.schemaVersion()
                        + "\nIntegrity: " + diagnostics.integrityResult()
                        + "\nForeign-key violations: " + diagnostics.foreignKeyViolations()
                        + "\nOrphan images: " + diagnostics.orphanImages()
                        + "\nBroken image blocks: " + diagnostics.brokenImageBlocks()
                        + "\nJournal mode: " + diagnostics.journalMode());
            }
            catch (SQLException e)
            {
                showError("Failed to check database", e);
            }
        });

        content.getChildren().addAll(textFieldRow("Ollama URL", ollamaUrlField),
                modelSelectorRow(),
                modelDetailsLabel,
                sliderRow("Context Limit", contextLimitSlider, contextLimitValueLabel,
                        value -> updateContextLimit(value.intValue())),
                optionalSliderRow(responseLengthEnabledBox, responseLengthSlider,
                        valueLabel(appSettings.responseLength(), "tokens"),
                        value -> updateResponseLength(value.intValue()), this::updateResponseLengthEnabled),
                optionalSliderRow(temperatureEnabledBox, temperatureSlider,
                        valueLabel(activeModelSettings.temperature(), "", 2),
                        value -> updateTemperature(roundTo(value.doubleValue(), 0.1)), this::updateTemperatureEnabled),
                optionalSliderRow(topKEnabledBox, topKSlider, valueLabel(activeModelSettings.topK(), ""),
                        value -> updateTopK(value.intValue()), this::updateTopKEnabled),
                optionalSliderRow(topPEnabledBox, topPSlider, valueLabel(activeModelSettings.topP(), "", 2),
                        value -> updateTopP(roundTo(value.doubleValue(), 0.01)), this::updateTopPEnabled),
                optionalSliderRow(minPEnabledBox, minPSlider, valueLabel(activeModelSettings.minP(), "", 3),
                        value -> updateMinP(roundTo(value.doubleValue(), 0.001)), this::updateMinPEnabled),
                optionalSliderRow(typicalPEnabledBox, typicalPSlider,
                        valueLabel(activeModelSettings.typicalP(), "", 2),
                        value -> updateTypicalP(roundTo(value.doubleValue(), 0.01)), this::updateTypicalPEnabled),
                optionalSliderRow(presencePenaltyEnabledBox, presencePenaltySlider,
                        valueLabel(activeModelSettings.presencePenalty(), "", 2),
                        value -> updatePresencePenalty(roundTo(value.doubleValue(), 0.01)),
                        this::updatePresencePenaltyEnabled),
                optionalSliderRow(frequencyPenaltyEnabledBox, frequencyPenaltySlider,
                        valueLabel(activeModelSettings.frequencyPenalty(), "", 2),
                        value -> updateFrequencyPenalty(roundTo(value.doubleValue(), 0.01)),
                        this::updateFrequencyPenaltyEnabled),
                optionalSliderRow(repeatLastNEnabledBox, repeatLastNSlider,
                        valueLabel(activeModelSettings.repeatLastN(), ""),
                        value -> updateRepeatLastN(value.intValue()), this::updateRepeatLastNEnabled),
                optionalSliderRow(repetitionPenaltyEnabledBox, repetitionPenaltySlider,
                        valueLabel(activeModelSettings.repetitionPenalty(), "", 2),
                        value -> updateRepetitionPenalty(roundTo(value.doubleValue(), 0.01)),
                        this::updateRepetitionPenaltyEnabled),
                sliderRow("Context to Use for Story", minStoryPercentSlider, valueLabel(percentFromSettings(), "%"),
                        value -> updateMinStoryPercent(value.intValue())),
                spinnerRow("Story Card Look Back", storyCardLookbackSpinner, this::updateStoryCardLookback),
                spinnerRow("Author's Note Insertion Point", anPlacementSpinner, this::updateAnPlacement),
                underlinedLabel("Auto Cards (Global)"),
                comboRow("Candidation Selection Mode", autoCardsCandidateSelectionMode),
                comboRow("Context Mode", autoCardsContextMode),
                spinnerRow("Cooldown (turns)", autoCardsCooldownSpinner, this::updateAppAutoCardsCooldown),
                spinnerRow("Max cards per run", autoCardsMaxPerRunSpinner, this::updateAppAutoCardsMaxPerRun),
                spinnerRow("Candidate window (blocks)", autoCardsWindowSpinner, this::updateAppAutoCardsWindow),
                spinnerRow("Card length limit (chars)", autoCardsLengthLimitSpinner, this::updateAppAutoCardsLengthLimit),
                autoCardsSummarizeBox,
                autoCardsBulletedListsBox,
                underlinedLabel("Auto Cards (Model)"),
                new Label("Create Prompt"),
                autoCardsCreatePrompt,
                new Label("Update Prompt"),
                autoCardsUpdatePrompt,
                new Label("Summarize Prompt"),
                autoCardsSummarizePrompt,
                spinnerRow("Max tokens (create)", autoCardsMaxTokensCreate, value -> updateModelAutoCardsTokens()),
                spinnerRow("Max tokens (update)", autoCardsMaxTokensUpdate, value -> updateModelAutoCardsTokens()),
                spinnerRow("Max tokens (summarize)", autoCardsMaxTokensSummarize, value -> updateModelAutoCardsTokens()),
                underlinedLabel("Image Generation"),
                textFieldRow("ComfyUI URL", comfyUiUrlField),
                comboRow("ComfyUI Workflow", comfyWorkflowSelect),
                spinnerRow("Image Width", comfyWidthSpinner, this::updateComfyWidth),
                spinnerRow("Image Height", comfyHeightSpinner, this::updateComfyHeight),
                spinnerRow("Image Batch Size", comfyBatchSizeSpinner, this::updateComfyBatchSize),
                underlinedLabel("Local Data"),
                new Label("Database"),
                databaseLocationLabel,
                backupDatabaseButton,
                checkDatabaseButton
            );

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
            AppSettings validated = current.get();
            appSettingsRepository.save(validated);
            return validated;
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

    private void refreshModelsFromOllama(boolean userInitiated)
    {
        if (modelRefreshInProgress || executor == null)
        {
            return;
        }

        modelRefreshInProgress = true;
        if (refreshModelsButton != null)
        {
            refreshModelsButton.setDisable(true);
        }
        if (statusLabel != null)
        {
            statusLabel.setText("Refreshing Ollama models...");
        }

        String endpoint = ollamaClient.getHost();
        String preferredModel = appSettings.selectedModel();
        submitTask(() ->
                {
                    List<String> models = ollamaClient.listModels(endpoint);
                    String selected = models.contains(preferredModel)
                            ? preferredModel
                            : models.isEmpty() ? "" : models.getFirst();
                    OllamaModelDetails details = null;
                    Exception metadataError = null;
                    if (!selected.isBlank())
                    {
                        try
                        {
                            details = ollamaClient.showModel(endpoint, selected);
                        }
                        catch (IOException e)
                        {
                            metadataError = e;
                        }
                    }
                    return new ModelDiscoveryResult(endpoint, models, selected, details, metadataError);
                },
                result ->
                {
                    finishModelRefresh();
                    if (!result.endpoint().equals(ollamaClient.getHost()))
                    {
                        refreshModelsFromOllama(false);
                        return;
                    }
                    try
                    {
                        if (result.models().isEmpty())
                        {
                            modelSettingsRepository.syncWithModels(List.of(),
                                    ModelSettings.defaults(appSettings.selectedModel()));
                            refreshModelSelect();
                            statusLabel.setText("Ollama is available, but no local models were found.");
                            modelDetailsLabel.setText("No local Ollama models were found.");
                            if (userInitiated)
                            {
                                showInfo("Ollama is available, but no local models were found.");
                            }
                            return;
                        }

                        modelSettingsRepository.syncWithModels(result.models(),
                                ModelSettings.defaults(result.selectedModel()));
                        String selectedModel = result.models().contains(appSettings.selectedModel())
                                ? appSettings.selectedModel()
                                : result.selectedModel();
                        OllamaModelDetails selectedDetails = selectedModel.equals(result.selectedModel())
                                ? result.selectedDetails()
                                : null;
                        modelDetailsByName.clear();
                        if (selectedDetails != null)
                        {
                            modelDetailsByName.put(selectedModel, selectedDetails);
                        }
                        selectModelInternal(selectedModel, selectedDetails);
                        refreshModelSelect();
                        if (selectedDetails == null)
                        {
                            if (selectedModel.equals(result.selectedModel()) && result.metadataError() != null)
                            {
                                modelDetailsLabel.setText("Model metadata unavailable: "
                                        + taskErrorMessage(result.metadataError()));
                                statusLabel.setText("Models refreshed, but context validation failed.");
                                if (userInitiated)
                                {
                                    showError("Failed to inspect Ollama model", result.metadataError());
                                }
                            }
                            else
                            {
                                loadModelDetails(selectedModel);
                            }
                        }
                        else
                        {
                            statusLabel.setText("Ollama models refreshed.");
                        }
                    }
                    catch (SQLException e)
                    {
                        showError("Failed to save discovered Ollama models", e);
                    }
                },
                error ->
                {
                    finishModelRefresh();
                    if (!endpoint.equals(ollamaClient.getHost()))
                    {
                        refreshModelsFromOllama(false);
                        return;
                    }
                    String message = taskErrorMessage(error);
                    statusLabel.setText("Ollama model refresh failed: " + message);
                    if (modelDetailsLabel != null)
                    {
                        modelDetailsLabel.setText("Model discovery failed. Existing saved settings remain available.");
                    }
                    if (userInitiated)
                    {
                        showError("Failed to refresh Ollama models", error);
                    }
                });
    }

    private void finishModelRefresh()
    {
        modelRefreshInProgress = false;
        if (refreshModelsButton != null)
        {
            refreshModelsButton.setDisable(false);
        }
    }

    private ModelSettings loadActiveModelSettings(String modelName) throws SQLException
    {
        Optional<ModelSettings> selected = modelSettingsRepository.load(modelName);
        if (selected.isPresent() && selected.get().active())
        {
            ModelSettings validated = selected.get();
            modelSettingsRepository.save(validated);
            return validated;
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
        int contextLimit = activeModelSettings.contextLimit();
        int minStoryWindow = (int) Math.round(contextLimit * (appSettings.minStoryPercent() / 100.0));
        return new GenerationSettings(activeModelSettings.modelName(), appSettings.ollamaUrl(), contextLimit,
                activeModelSettings.promptTokenScale(),
                appSettings.responseLengthEnabled(), appSettings.responseLength(),
                activeModelSettings.temperatureEnabled(), activeModelSettings.temperature(),
                activeModelSettings.topKEnabled(), activeModelSettings.topK(),
                activeModelSettings.topPEnabled(), activeModelSettings.topP(),
                activeModelSettings.minPEnabled(), activeModelSettings.minP(),
                activeModelSettings.typicalPEnabled(), activeModelSettings.typicalP(),
                activeModelSettings.presencePenaltyEnabled(), activeModelSettings.presencePenalty(),
                activeModelSettings.frequencyPenaltyEnabled(), activeModelSettings.frequencyPenalty(),
                activeModelSettings.repeatLastNEnabled(), activeModelSettings.repeatLastN(),
                activeModelSettings.repetitionPenaltyEnabled(), activeModelSettings.repetitionPenalty(),
                minStoryWindow, appSettings.storyCardLookback(), appSettings.anPlacement());
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
            updatingModelControls = true;
            modelSelect.setItems(FXCollections.observableArrayList(names));
            modelSelect.setValue(names.contains(appSettings.selectedModel()) ? appSettings.selectedModel() : null);
            updatingModelControls = false;
        }
        catch (SQLException e)
        {
            updatingModelControls = false;
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
            selectModelInternal(modelName, modelDetailsByName.get(modelName));
            if (!modelDetailsByName.containsKey(modelName))
            {
                loadModelDetails(modelName);
            }
        }
        catch (SQLException e)
        {
            showError("Failed to select model", e);
        }
    }

    private void selectModelInternal(String modelName, OllamaModelDetails details) throws SQLException
    {
        Optional<ModelSettings> selected = modelSettingsRepository.load(modelName);
        if (selected.isEmpty())
        {
            return;
        }
        activeModelSettings = selected.get();
        modelSettingsRepository.save(activeModelSettings);
        modelAutoCardsSettings = loadOrCreateModelAutoCardsSettings(modelName);
        appSettings = SettingsCoordinator.withSelectedModel(appSettings, modelName);
        persistAppSettings();
        ollamaClient.setModel(modelName);
        applyModelDetails(details);
        updateModelControls();
        updateModelAutoCardsControls();
        refreshGenerationSettings();
    }

    private void loadModelDetails(String modelName)
    {
        String endpoint = ollamaClient.getHost();
        if (modelDetailsLabel != null)
        {
            modelDetailsLabel.setText("Loading model metadata...");
        }
        submitTask(() -> ollamaClient.showModel(endpoint, modelName),
                details ->
                {
                    if (!endpoint.equals(ollamaClient.getHost()))
                    {
                        return;
                    }
                    modelDetailsByName.put(modelName, details);
                    if (activeModelSettings != null && modelName.equals(activeModelSettings.modelName()))
                    {
                        applyModelDetails(details);
                        updateModelControls();
                        refreshGenerationSettings();
                    }
                },
                error ->
                {
                    if (activeModelSettings != null && modelName.equals(activeModelSettings.modelName()))
                    {
                        modelDetailsLabel.setText("Model metadata unavailable: " + taskErrorMessage(error));
                        statusLabel.setText("Could not validate model context.");
                        showError("Failed to inspect Ollama model", error);
                    }
                });
    }

    private void applyModelDetails(OllamaModelDetails details)
    {
        if (modelDetailsLabel == null || activeModelSettings == null)
        {
            return;
        }
        if (details == null)
        {
            modelDetailsLabel.setText("Model metadata has not been loaded.");
            return;
        }

        String summary = details.displaySummary();
        if (details.hasCapability("thinking"))
        {
            summary += " · thinking is requested off for narrative compatibility";
        }
        int declaredMaximum = details.maxContextLength();
        if (declaredMaximum > 0 && activeModelSettings.contextLimit() > declaredMaximum)
        {
            int previous = activeModelSettings.contextLimit();
            activeModelSettings = SettingsCoordinator.withContextLimit(activeModelSettings, declaredMaximum);
            persistModelSettings();
            summary = "Configured context reduced from " + previous + " to the model maximum of "
                    + activeModelSettings.contextLimit() + " tokens. " + summary;
            statusLabel.setText("Context limit adjusted to model maximum.");
        }
        modelDetailsLabel.setText(summary);
    }

    private void updateOllamaUrl(String url)
    {
        final String normalized;
        try
        {
            normalized = OllamaEndpoint.normalize(url);
        }
        catch (IllegalArgumentException e)
        {
            if (ollamaUrlField != null)
            {
                ollamaUrlField.setText(appSettings.ollamaUrl());
            }
            showError("Invalid Ollama URL", e);
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
        modelDetailsByName.clear();
        refreshModelsFromOllama(false);
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
        List<String> names = new ArrayList<>(bundledComfyWorkflowNames());
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

    static List<String> bundledComfyWorkflowNames()
    {
        return BUNDLED_COMFY_WORKFLOW_NAMES.stream()
                .filter(name -> App.class.getResource("/comfyui/" + name + ".json") != null)
                .toList();
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

    private void observePromptCalibration(int estimatedPromptTokens, OllamaChatResult response)
    {
        if (response == null || response.model().isBlank()
                || estimatedPromptTokens <= 0 || response.promptEvalCount() <= 0)
        {
            return;
        }
        try
        {
            Optional<ModelSettings> stored = modelSettingsRepository.load(response.model());
            if (stored.isEmpty())
            {
                return;
            }

            ModelSettings modelSettings = stored.get();
            double oldScale = modelSettings.promptTokenScale();
            double sampleRatio = response.promptEvalCount() / (double) estimatedPromptTokens;
            double targetScale = oldScale * sampleRatio;
            targetScale = Math.max(ModelSettings.MIN_PROMPT_TOKEN_SCALE,
                    Math.min(ModelSettings.MAX_PROMPT_TOKEN_SCALE, targetScale));
            double updated = oldScale + (targetScale - oldScale) * TOKEN_SCALE_ALPHA;
            updated = Math.max(ModelSettings.MIN_PROMPT_TOKEN_SCALE,
                    Math.min(ModelSettings.MAX_PROMPT_TOKEN_SCALE, updated));
            ModelSettings calibrated = SettingsCoordinator.withPromptTokenScale(modelSettings, updated);
            modelSettingsRepository.save(calibrated);
            if (activeModelSettings != null && response.model().equals(activeModelSettings.modelName()))
            {
                activeModelSettings = calibrated;
                refreshGenerationSettings();
            }
        }
        catch (SQLException e)
        {
            statusLabel.setText("Response completed, but prompt calibration could not be saved.");
        }
    }

    private void recordOllamaResponse(int estimatedPromptTokens, OllamaChatResult response)
    {
        observePromptCalibration(estimatedPromptTokens, response);
        if (statusLabel != null && response != null)
        {
            String summary = response.diagnosticSummary();
            statusLabel.setTooltip(summary.isBlank() ? null : new Tooltip(summary));
        }
    }

    private boolean runAutoCardsForGeneration(AutoCardsRunContext context, List<Block> currentBlocks,
            List<StoryCard> currentCards) throws Exception
    {
        try
        {
            return runAutoCardsIfNeeded(context, currentBlocks, currentCards, false).ran();
        }
        catch (Exception e)
        {
            logAutoCardsError("Auto Cards failed to run", e);
            return false;
        }
    }

    private <T> Task<T> submitTask(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure)
    {
        Task<T> task = new Task<>()
        {
            @Override
            protected T call() throws Exception
            {
                return work.call();
            }
        };

        task.setOnSucceeded(event ->
        {
            backgroundTasks.remove(task);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event ->
        {
            backgroundTasks.remove(task);
            onFailure.accept(task.getException());
        });
        task.setOnCancelled(event -> backgroundTasks.remove(task));
        backgroundTasks.add(task);
        executor.submit(task);
        return task;
    }

    private <T> Task<T> submitStoryTask(StorySession session, String kind, Callable<T> work,
            Consumer<T> onSuccess, Consumer<Throwable> onFailure)
    {
        StoryOperationRegistry.Operation operation = storyOperations.begin(session, kind);
        Task<T> task = submitTask(work,
                value ->
                {
                    storyOperations.complete(operation);
                    onSuccess.accept(value);
                },
                error ->
                {
                    storyOperations.complete(operation);
                    onFailure.accept(error);
                });
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED,
                event -> storyOperations.complete(operation));
        return task;
    }

    private static String taskErrorMessage(Throwable error)
    {
        return error == null ? "Unknown" : error.getMessage();
    }

    private void setStoryActionButtonsBusy(boolean busy)
    {
        if (storyPaneController != null)
        {
            storyPaneController.setActionButtonsDisabled(busy);
        }
    }

    private void restoreStoryActionButtonsState()
    {
        boolean busy = activeSession != null && storyOperations.hasActive(activeSession.storyId());
        setStoryActionButtonsBusy(busy);
        if (!busy && activeStory != null && retryHistoryButton != null)
        {
            retryHistoryButton.setDisable(retryHistory.size(activeSession) < 2);
        }
    }

    private void updateModelControls()
    {
        if (activeModelSettings == null || contextLimitSlider == null)
        {
            return;
        }
        updatingModelControls = true;
        OllamaModelDetails details = modelDetailsByName.get(activeModelSettings.modelName());
        int sliderMaximum = details != null && details.maxContextLength() > 0
                ? Math.min(ModelSettings.MAX_CONTEXT_LIMIT, details.maxContextLength())
                : Math.max(131072, activeModelSettings.contextLimit());
        sliderMaximum = Math.max(ModelSettings.MIN_CONTEXT_LIMIT, sliderMaximum);
        contextLimitSlider.setMax(sliderMaximum);
        contextLimitSlider.setValue(Math.min(activeModelSettings.contextLimit(), sliderMaximum));
        temperatureSlider.setValue(activeModelSettings.temperature());
        topKSlider.setValue(activeModelSettings.topK());
        topPSlider.setValue(activeModelSettings.topP());
        minPSlider.setValue(activeModelSettings.minP());
        typicalPSlider.setValue(activeModelSettings.typicalP());
        presencePenaltySlider.setValue(activeModelSettings.presencePenalty());
        frequencyPenaltySlider.setValue(activeModelSettings.frequencyPenalty());
        repeatLastNSlider.setMax(activeModelSettings.contextLimit());
        repeatLastNSlider.setValue(activeModelSettings.repeatLastN());
        repetitionPenaltySlider.setValue(activeModelSettings.repetitionPenalty());
        temperatureEnabledBox.setSelected(activeModelSettings.temperatureEnabled());
        topKEnabledBox.setSelected(activeModelSettings.topKEnabled());
        topPEnabledBox.setSelected(activeModelSettings.topPEnabled());
        minPEnabledBox.setSelected(activeModelSettings.minPEnabled());
        typicalPEnabledBox.setSelected(activeModelSettings.typicalPEnabled());
        presencePenaltyEnabledBox.setSelected(activeModelSettings.presencePenaltyEnabled());
        frequencyPenaltyEnabledBox.setSelected(activeModelSettings.frequencyPenaltyEnabled());
        repeatLastNEnabledBox.setSelected(activeModelSettings.repeatLastNEnabled());
        repetitionPenaltyEnabledBox.setSelected(activeModelSettings.repetitionPenaltyEnabled());
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

        AutoCardsRunContext context = captureAutoCardsRunContext();
        if (context == null)
        {
            showInfo("Auto Cards settings are not loaded yet.");
            return;
        }

        statusLabel.setText("Auto Cards...");
        autoCardsRunButton.setDisable(true);
        submitStoryTask(context.session(), "Auto Cards", () ->
                {
                    List<Block> currentBlocks = blockRepository.listForStory(context.story().id());
                    List<StoryCard> currentCards = cardRepository.listForStory(context.story().id());
                    return runAutoCardsIfNeeded(context, currentBlocks, currentCards, true);
                },
                result ->
                {
                    if (activeStory != null && activeStory.id().equals(context.story().id()))
                    {
                        refreshCardList(context.story().id());
                    }
                    if (result != null && result.ran())
                    {
                        statusLabel.setText("Auto Cards updated (" + result.created() + " new, " + result.updated() + " updated)");
                    }
                    else
                    {
                        statusLabel.setText("Auto Cards: no changes");
                    }
                    updateAutoCardsRunButtonState();
                },
                error ->
                {
                    statusLabel.setText("Auto Cards error: " + taskErrorMessage(error));
                    updateAutoCardsRunButtonState();
                });
    }

    private AutoCardsCoordinator.RunResult runAutoCardsIfNeeded(AutoCardsRunContext context, List<Block> currentBlocks,
            List<StoryCard> currentCards, boolean manual)
            throws IOException, InterruptedException, SQLException
    {
        if (context == null)
        {
            return new AutoCardsCoordinator.RunResult(0, 0, false);
        }
        AutoCardsCoordinator.PreviewCallbacks previewCallbacks = new AutoCardsCoordinator.PreviewCallbacks(
                draft -> runOnUiThreadAndWait(
                        () -> AutoCardsDialogs.showCreateDialog(primaryStage, context.story().id(), draft)),
                (existing, proposed, summarized) -> runOnUiThreadAndWait(
                        () -> AutoCardsDialogs.showUpdateDialog(primaryStage, existing, proposed, summarized)));
        return autoCardsCoordinator.runIfNeeded(
                context.story(),
                currentBlocks,
                currentCards,
                manual,
                context.appSettings(),
                context.storySettings(),
                context.appAutoCardsSettings(),
                context.modelSettings(),
                context.modelAutoCardsSettings(),
                previewCallbacks);
    }

    private AutoCardsRunContext captureAutoCardsRunContext()
    {
        if (activeSession == null || activeStory == null || appSettings == null || storyAutoCardsSettings == null
                || appAutoCardsSettings == null || activeModelSettings == null || modelAutoCardsSettings == null)
        {
            return null;
        }
        return new AutoCardsRunContext(activeSession, activeStory, appSettings, storyAutoCardsSettings,
                appAutoCardsSettings, activeModelSettings, modelAutoCardsSettings);
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
        StoryDialogs.showNewStoryDialog(primaryStage, this::showInfo, trimmed ->
        {
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
        ImportDialogs.showAdventureImportDialog(
                primaryStage,
                this::showInfo,
                this::showError,
                aiDungeonImports::importAdventure,
                imported ->
                {
                    refreshStoryList(imported.id());
                    loadStory(imported, true);
                });
    }

    private void showStoryDialog(Story story)
    {
        StoryDialogs.showStoryDialog(
                primaryStage,
                story,
                this::showInfo,
                name -> playStory(updateStoryTitleIfNeeded(story, name)),
                name -> refreshStoryList(updateStoryTitleIfNeeded(story, name).id()),
                () -> confirmDelete(story.title()),
                () -> deleteStory(story));
    }

    private void showCardDialog(StoryCard card)
    {
        if (activeStory == null)
        {
            showInfo("Select a story first.");
            return;
        }
        StoryCardDialogs.showCardDialog(
                primaryStage,
                activeStory.id(),
                card,
                this::showInfo,
                this::showError,
                savedCard ->
                {
                    if (card == null)
                    {
                        cardRepository.insert(savedCard);
                    }
                    else
                    {
                        cardRepository.update(savedCard);
                    }
                    refreshCardList(activeStory.id());
                },
                () ->
                {
                    if (card != null && confirmDeleteCard(card.title()))
                    {
                        deleteCard(card);
                        return true;
                    }
                    return false;
                });
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
        AutoCardsRunContext context = captureAutoCardsRunContext();
        if (context == null)
        {
            showInfo("Auto Cards settings are not loaded yet.");
            return;
        }
        Story story = context.story();
        StoryCardDialogs.showGenerateDialog(
                primaryStage,
                story.id(),
                this::showInfo,
                this::showError,
                text -> statusLabel.setText(text),
                (request, onSuccess, onFailure) -> submitStoryTask(context.session(), "Generate Story Card",
                        () -> autoCardsCoordinator.generateCardDraftFromPrompt(
                                story,
                                request,
                                context.appSettings(),
                                context.appAutoCardsSettings(),
                                context.modelSettings(),
                                context.modelAutoCardsSettings()),
                        onSuccess,
                        onFailure),
                savedCard ->
                {
                    cardRepository.insert(savedCard);
                    if (activeStory != null && activeStory.id().equals(story.id()))
                    {
                        refreshCardList(story.id());
                    }
                });
    }

    private void showPromptDialog()
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

        AutoCardsRunContext context = captureAutoCardsRunContext();
        if (context == null)
        {
            showInfo("Auto Cards settings are not loaded yet.");
            return;
        }
        Story story = context.story();
        PromptDialog.show(
                primaryStage,
                DEFAULT_ONE_SHOT_SYSTEM_PROMPT,
                this::showInfo,
                this::showError,
                text -> statusLabel.setText(text),
                () -> setStoryActionButtonsBusy(true),
                this::restoreStoryActionButtonsState,
                (systemPrompt, userPrompt, onSuccess, onFailure) -> submitStoryTask(
                        context.session(), "Story Prompt",
                        () -> storyPromptCoordinator.generateResponse(
                                story,
                                systemPrompt,
                                userPrompt,
                                context.appSettings(),
                                context.appAutoCardsSettings(),
                                context.modelSettings(),
                                context.modelAutoCardsSettings()),
                        onSuccess,
                        onFailure));
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

        AutoCardsRunContext context = captureAutoCardsRunContext();
        if (context == null)
        {
            showInfo("Auto Cards settings are not loaded yet.");
            return;
        }
        String header = replaceImageBlock == null ? "Generate an image prompt from the story" : "Retry image generation";
        String insertLabel = replaceImageBlock == null ? "Insert Image" : "Replace Image";
        Story story = context.story();
        StorySession session = context.session();
        String expectedHeadId = replaceImageBlock == null ? session.headBlockId() : replaceImageBlock.id();
        SeeDialog.show(
                primaryStage,
                header,
                initialPrompt,
                DEFAULT_SEE_REQUEST,
                insertLabel,
                this::showInfo,
                this::showError,
                textValue -> statusLabel.setText(textValue),
                () -> setStoryActionButtonsBusy(true),
                this::restoreStoryActionButtonsState,
                (request, onSuccess, onFailure) -> submitStoryTask(session, "Image Prompt",
                        () -> imageGenerationCoordinator.generateImagePrompt(
                                story,
                                request,
                                context.appSettings(),
                                context.modelSettings(),
                                context.appAutoCardsSettings(),
                                context.modelAutoCardsSettings()),
                        onSuccess,
                        onFailure),
                (promptText, onSuccess, onFailure) -> submitStoryTask(session, "Image Generation",
                        () ->
                        {
                            ComfyUiClient.GenerationResult result = imageGenerationCoordinator.generateImages(
                                    context.appSettings(), promptText);
                            List<ImageGenerationCoordinator.PendingImage> pending = new ArrayList<>();
                            if (result != null && result.images() != null)
                            {
                                for (ComfyUiClient.GeneratedImage image : result.images())
                                {
                                    pending.add(new ImageGenerationCoordinator.PendingImage(
                                            image.bytes(), image.mimeType(), result.workflowJson()));
                                }
                            }
                            return pending;
                        },
                        onSuccess,
                        error ->
                        {
                            System.out.println("ComfyUI image generation failed:");
                            if (error != null)
                            {
                                error.printStackTrace(System.out);
                            }
                            onFailure.accept(error);
                        }),
                (pending, promptText) ->
                {
                    ImageGenerationCoordinator.ImageMutationResult result = imageGenerationCoordinator.insertOrReplaceImage(
                            story, expectedHeadId, pending, promptText, replaceImageBlock);
                    if (result.stale())
                    {
                        statusLabel.setText("Image result was stale; the story was not changed.");
                        return;
                    }
                    if (canApplyToActiveSession(session))
                    {
                        reloadActiveStoryIfCompatible(session, result.updatedStory(), true);
                        if (result.replaced())
                        {
                            StoryImage storyImage = result.storyImage();
                            retryHistory.add(activeSession, new ImageRetryHistoryEntry(storyImage.prompt(),
                                    storyImage.imageBytes(), storyImage.mimeType(), storyImage.workflowJson()));
                            updateRetryCountLabel();
                        }
                    }
                    else
                    {
                        refreshStoryList(activeStory == null ? null : activeStory.id());
                    }
                    statusLabel.setText(replaceImageBlock == null ? "Inserted image" : "Replaced image");
                });
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
                activeSession = null;
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
            Block head = blocks.getLast();
            database.inTransaction(connection ->
            {
                blockRepository.deleteHead(activeStory.id());
                deleteLinkedImageIfPresent(head);
            });
            blocks = blockRepository.listForStory(activeStory.id());
            activeSession = activeSession.advance(blocks);
            retryHistory.activate(activeSession);
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
            activateStorySession();
            storyAutoCardsSettings = loadOrCreateStoryAutoCardsSettings(story.id());
            renderStoryBlocks(true);
            statusLabel.setText("Ready");
            populateStoryDetails(story);
            updateStoryAutoCardsControls();
            updateAutoCardsRunButtonState();
            refreshCardList(story.id());
            setStoryDependentControlsEnabled(true);
            restoreStoryActionButtonsState();
            updateRetryCountLabel();
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

    private void activateStorySession()
    {
        if (activeStory == null)
        {
            activeSession = null;
            return;
        }
        activeSession = StorySession.open(activeStory.id(), ++storySessionRevision, blocks);
        retryHistory.activate(activeSession);
    }

    private boolean canApplyToActiveSession(StorySession source)
    {
        return activeSession != null && activeSession.canApplyResultFrom(source);
    }

    private void reloadActiveStoryIfCompatible(StorySession source, Story updatedStory, boolean forceScroll)
            throws SQLException
    {
        if (!canApplyToActiveSession(source))
        {
            refreshStoryList(activeStory == null ? null : activeStory.id());
            return;
        }

        activeStory = updatedStory == null
                ? storyRepository.findById(source.storyId()).orElse(activeStory)
                : updatedStory;
        blocks = blockRepository.listForStory(source.storyId());
        activeSession = activeSession.advance(blocks);
        retryHistory.activate(activeSession);
        renderStoryBlocks(forceScroll);
        refreshStoryList(source.storyId());
        refreshCardList(source.storyId());
        updateRetryCountLabel();
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
        Block head = blocks.getLast();
        if (head.role() == Role.IMAGE)
        {
            seedImageRetryHistoryIfNeeded(head);
            StoryImage image = loadStoryImage(head.text());
            showSeeDialog(image == null ? null : image.prompt(), head);
            return;
        }
        if (head.role() == Role.USER)
        {
            runContinue();
            return;
        }
        if (head.role() != Role.ASSISTANT)
        {
            showInfo("The last block is not retryable.");
            return;
        }

        AutoCardsRunContext context = captureAutoCardsRunContext();
        if (context == null)
        {
            showInfo("Generation settings are not loaded yet.");
            return;
        }
        StorySession session = context.session();
        List<Block> blockSnapshot = List.copyOf(blocks);
        GenerationSettings operationSettings = buildGenerationSettings();
        if (retryHistory.isEmpty(session))
        {
            retryHistory.add(session, new TextRetryHistoryEntry(head.text()));
        }
        setStoryActionButtonsBusy(true);
        statusLabel.setText("Generating...");
        long streamingToken = storyPaneController.startStreaming(StoryPaneController.StreamingMode.RETRY);
        GenerationCoordinator.GenerationObserver observer = streamingObserver(streamingToken);
        submitStoryTask(session, "Retry", () ->
                {
                    return generationCoordinator.retryAssistantHead(context.story(), blockSnapshot, head,
                            operationSettings, observer);
                },
                result ->
                {
                    recordOllamaResponse(result.estimatedPromptTokens(), result.ollamaResponse());
                    try
                    {
                        if (result.status() == GenerationCoordinator.ResultStatus.STALE)
                        {
                            storyPaneController.cancelStreaming(streamingToken);
                            if (canApplyToActiveSession(session))
                            {
                                statusLabel.setText("Retry was stale; the story was not changed.");
                            }
                            return;
                        }
                        if (result.status() == GenerationCoordinator.ResultStatus.EMPTY)
                        {
                            storyPaneController.cancelStreaming(streamingToken);
                            if (canApplyToActiveSession(session))
                            {
                                statusLabel.setText("Last generation was empty.");
                            }
                            return;
                        }
                        if (canApplyToActiveSession(session))
                        {
                            storyPaneController.endStreaming(streamingToken);
                            reloadActiveStoryIfCompatible(session, context.story(), true);
                            retryHistory.add(activeSession, new TextRetryHistoryEntry(result.updatedBlock().text()));
                            statusLabel.setText("Ready");
                            updateRetryCountLabel();
                        }
                    }
                    catch (SQLException e)
                    {
                        showError("Failed to reload story", e);
                    }
                    finally
                    {
                        restoreStoryActionButtonsState();
                    }
                },
                error ->
                {
                    storyPaneController.cancelStreaming(streamingToken);
                    restoreStoryActionButtonsState();
                    statusLabel.setText("Error: " + taskErrorMessage(error));
                    showError("Ollama generation failed", error);
                });
    }

    private void showRetryDialog()
    {
        if (activeSession == null || retryHistory.size(activeSession) < 2)
        {
            return;
        }
        int retryIndex = retryHistory.selectedIndex(activeSession);
        List<RetryHistoryEntry> historyEntries = retryHistory.entries(activeSession);
        if (retryIndex < 0 || retryIndex >= historyEntries.size())
        {
            retryIndex = historyEntries.size() - 1;
            retryHistory.select(activeSession, retryIndex);
        }

        Block head = blocks.isEmpty() ? null : blocks.getLast();
        boolean imageMode = head != null && head.role() == Role.IMAGE;

        List<RetryHistoryDialog.Entry> entries = new ArrayList<>(historyEntries.size());
        for (RetryHistoryEntry entry : historyEntries)
        {
            if (entry instanceof TextRetryHistoryEntry textEntry)
            {
                entries.add(new RetryHistoryDialog.TextEntry(textEntry.text));
            }
            else if (entry instanceof ImageRetryHistoryEntry imageEntry)
            {
                entries.add(new RetryHistoryDialog.ImageEntry(imageEntry.prompt, imageEntry.bytes));
            }
        }

        Integer selectedIndex = RetryHistoryDialog.show(primaryStage, entries, retryIndex, imageMode);
        if (selectedIndex == null)
        {
            return;
        }
        retryHistory.select(activeSession, selectedIndex);
        if (blocks.isEmpty())
        {
            return;
        }

        Block currentHead = blocks.getLast();
        RetryHistoryEntry chosen = retryHistory.selected(activeSession);
        if (currentHead.role() == Role.ASSISTANT && chosen instanceof TextRetryHistoryEntry textEntry)
        {
            if (!textEntry.text.equals(currentHead.text()))
            {
                Block updated = new Block(currentHead.id(), currentHead.storyId(), Role.ASSISTANT, textEntry.text,
                        Timestamps.now(), currentHead.position());
                try
                {
                    if (!blockRepository.replaceHeadIfCurrent(updated))
                    {
                        statusLabel.setText("Retry selection was stale; the story was not changed.");
                        return;
                    }
                    blocks.set(blocks.size() - 1, updated);
                    activeSession = activeSession.advance(blocks);
                    retryHistory.activate(activeSession);
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
    }

    private void seedImageRetryHistoryIfNeeded(Block imageHead)
    {
        if (activeSession == null || imageHead == null || imageHead.role() != Role.IMAGE
                || !retryHistory.isEmpty(activeSession))
        {
            return;
        }
        StoryImage image = loadStoryImage(imageHead.text());
        if (image == null || image.imageBytes() == null || image.imageBytes().length == 0)
        {
            return;
        }
        retryHistory.add(activeSession,
                new ImageRetryHistoryEntry(image.prompt(), image.imageBytes(), image.mimeType(), image.workflowJson()));
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
        if (result.stale())
        {
            statusLabel.setText("Retry selection was stale; the story was not changed.");
            return;
        }
        StorySession source = activeSession;
        reloadActiveStoryIfCompatible(source, result.updatedStory(), true);
        statusLabel.setText("Applied image retry selection");
    }

    private void showImportCardsDialog()
    {
        if (activeStory == null)
        {
            showInfo("Select a story first.");
            return;
        }

        String storyId = activeStory.id();
        ImportDialogs.showStoryCardsImportDialog(
                primaryStage,
                this::showInfo,
                this::showError,
                (path, replaceExisting) -> aiDungeonImports.importStoryCards(path, storyId, replaceExisting),
                imported -> refreshCardList(storyId));
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
        retryHistoryButton.setDisable(!enabled || activeSession == null || retryHistory.size(activeSession) < 2);
        seeButton.setDisable(!enabled);
        promptButton.setDisable(!enabled);
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
        String text = storyPaneController == null ? "" : storyPaneController.turnInputText().trim();
        if (text.isEmpty())
        {
            showInfo("Turn text cannot be empty.");
            return;
        }
        showTurnInput(false);
        if (storyPaneController != null)
        {
            storyPaneController.clearTurnInput();
        }
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

        AutoCardsRunContext context = captureAutoCardsRunContext();
        if (context == null)
        {
            showInfo("Generation settings are not loaded yet.");
            return;
        }
        GenerationSettings operationSettings = buildGenerationSettings();
        clearRetryHistory();
        setStoryActionButtonsBusy(true);
        statusLabel.setText("Generating...");
        long streamingToken = storyPaneController.startStreaming(StoryPaneController.StreamingMode.APPEND);
        GenerationCoordinator.GenerationObserver observer = streamingObserver(streamingToken);
        submitStoryTask(context.session(), "Continue", () ->
                {
                    return generationCoordinator.continueStory(context.story(), operationSettings,
                            (currentBlocks, currentCards) ->
                                    runAutoCardsForGeneration(context, currentBlocks, currentCards),
                            observer);
                },
                result ->
                {
                    recordOllamaResponse(result.estimatedPromptTokens(), result.ollamaResponse());
                    try
                    {
                        if (result.status() == GenerationCoordinator.ResultStatus.STALE)
                        {
                            storyPaneController.cancelStreaming(streamingToken);
                            if (canApplyToActiveSession(context.session()))
                            {
                                statusLabel.setText("Generation was stale; the story was not changed.");
                            }
                            return;
                        }
                        if (result.status() == GenerationCoordinator.ResultStatus.EMPTY)
                        {
                            storyPaneController.cancelStreaming(streamingToken);
                            if (canApplyToActiveSession(context.session()))
                            {
                                statusLabel.setText("Last generation was empty.");
                            }
                            return;
                        }
                        storyPaneController.endStreaming(streamingToken);
                        reloadActiveStoryIfCompatible(context.session(), result.updatedStory(), true);
                        if (activeStory != null && activeStory.id().equals(context.story().id()))
                        {
                            statusLabel.setText("Ready");
                        }
                    }
                    catch (SQLException e)
                    {
                        showError("Failed to reload story", e);
                    }
                    finally
                    {
                        restoreStoryActionButtonsState();
                    }
                },
                error ->
                {
                    storyPaneController.cancelStreaming(streamingToken);
                    restoreStoryActionButtonsState();
                    statusLabel.setText("Error: " + taskErrorMessage(error));
                    showError("Ollama generation failed", error);
                });
    }

    private void runTurn(String userText)
    {
        AutoCardsRunContext context = captureAutoCardsRunContext();
        if (context == null)
        {
            showInfo("Generation settings are not loaded yet.");
            return;
        }
        GenerationSettings operationSettings = buildGenerationSettings();
        setStoryActionButtonsBusy(true);
        statusLabel.setText("Generating...");
        long streamingToken = storyPaneController.startStreaming(StoryPaneController.StreamingMode.TURN);
        GenerationCoordinator.GenerationObserver observer = streamingObserver(streamingToken);
        submitStoryTask(context.session(), "Take A Turn", () ->
                {
                    return generationCoordinator.takeTurn(context.story(), userText, operationSettings,
                            (currentBlocks, currentCards) ->
                                    runAutoCardsForGeneration(context, currentBlocks, currentCards),
                            observer);
                },
                result ->
                {
                    try
                    {
                        recordOllamaResponse(result.estimatedPromptTokens(), result.ollamaResponse());
                        if (result.status() == GenerationCoordinator.ResultStatus.STALE)
                        {
                            storyPaneController.cancelStreaming(streamingToken);
                            if (canApplyToActiveSession(context.session()))
                            {
                                statusLabel.setText("Generation was stale; no response was applied.");
                            }
                            reloadActiveStoryIfCompatible(context.session(), result.updatedStory(), true);
                            return;
                        }
                        storyPaneController.endStreaming(streamingToken);
                        reloadActiveStoryIfCompatible(context.session(), result.updatedStory(), true);
                        if (activeStory != null && activeStory.id().equals(context.story().id()))
                        {
                            statusLabel.setText(result.generated() ? "Ready" : "Last generation was empty.");
                        }
                    }
                    catch (SQLException e)
                    {
                        showError("Failed to reload story", e);
                    }
                    finally
                    {
                        restoreStoryActionButtonsState();
                    }
                },
                error ->
                {
                    storyPaneController.cancelStreaming(streamingToken);
                    try
                    {
                        reloadActiveStoryIfCompatible(context.session(), null, true);
                    }
                    catch (SQLException reloadFailure)
                    {
                        error.addSuppressed(reloadFailure);
                    }
                    restoreStoryActionButtonsState();
                    statusLabel.setText("Error: " + taskErrorMessage(error));
                    showError("Ollama generation failed", error);
                });
    }

    private GenerationCoordinator.GenerationObserver streamingObserver(long streamingToken)
    {
        return new GenerationCoordinator.GenerationObserver()
        {
            @Override
            public void onSeedCommitted(Block seedBlock)
            {
                storyPaneController.queueStreamingSeed(streamingToken, seedBlock);
            }

            @Override
            public void onAttemptStarted(String generatedPrefix)
            {
                storyPaneController.queueStreamingAttempt(streamingToken, generatedPrefix);
            }

            @Override
            public void onGeneratedText(String chunk)
            {
                storyPaneController.queueStreamingText(streamingToken, chunk);
            }
        };
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

    private CheckBox optionCheckBox(String labelText, boolean selected)
    {
        CheckBox checkBox = new CheckBox(labelText);
        checkBox.setSelected(selected);
        return checkBox;
    }

    private VBox optionalSliderRow(CheckBox enabledBox, Slider slider, Label valueLabel,
            java.util.function.Consumer<Number> valueHandler,
            java.util.function.Consumer<Boolean> enabledHandler)
    {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, enabledBox, spacer, valueLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(6, header, slider);

        slider.disableProperty().bind(enabledBox.selectedProperty().not());
        valueLabel.disableProperty().bind(enabledBox.selectedProperty().not());
        slider.valueProperty().addListener((obs, oldValue, newValue) ->
        {
            valueLabel.setText(formatValue(newValue, valueLabel));
            valueHandler.accept(newValue);
        });
        enabledBox.setOnAction(event -> enabledHandler.accept(enabledBox.isSelected()));
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

    private VBox modelSelectorRow()
    {
        Label label = new Label("Model");
        HBox row = new HBox(8, modelSelect, refreshModelsButton);
        HBox.setHgrow(modelSelect, Priority.ALWAYS);
        return new VBox(6, label, row);
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

    private static Label underlinedLabel(String text)
    {
        Label label = new Label(text);
        label.setUnderline(true);
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
        return appSettings.minStoryPercent();
    }

    private void updateContextLimit(int value)
    {
        if (updatingModelControls)
        {
            return;
        }
        OllamaModelDetails details = modelDetailsByName.get(activeModelSettings.modelName());
        int maximum = details != null && details.maxContextLength() > 0
                ? Math.min(ModelSettings.MAX_CONTEXT_LIMIT, details.maxContextLength())
                : ModelSettings.MAX_CONTEXT_LIMIT;
        activeModelSettings = SettingsCoordinator.withContextLimit(activeModelSettings, Math.min(value, maximum));
        updateModelControls();
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateResponseLength(int value)
    {
        appSettings = SettingsCoordinator.withResponseLength(appSettings, value);
        persistAppSettings();
        refreshGenerationSettings();
    }

    private void updateResponseLengthEnabled(boolean enabled)
    {
        appSettings = SettingsCoordinator.withResponseLengthEnabled(appSettings, enabled);
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

    private void updateTemperatureEnabled(boolean enabled)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withTemperatureEnabled(activeModelSettings, enabled);
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

    private void updateTopKEnabled(boolean enabled)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withTopKEnabled(activeModelSettings, enabled);
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

    private void updateTopPEnabled(boolean enabled)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withTopPEnabled(activeModelSettings, enabled);
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

    private void updateMinPEnabled(boolean enabled)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withMinPEnabled(activeModelSettings, enabled);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateTypicalP(double value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withTypicalP(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateTypicalPEnabled(boolean enabled)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withTypicalPEnabled(activeModelSettings, enabled);
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

    private void updatePresencePenaltyEnabled(boolean enabled)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withPresencePenaltyEnabled(activeModelSettings, enabled);
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

    private void updateFrequencyPenaltyEnabled(boolean enabled)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withFrequencyPenaltyEnabled(activeModelSettings, enabled);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateRepeatLastN(int value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withRepeatLastN(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateRepeatLastNEnabled(boolean enabled)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withRepeatLastNEnabled(activeModelSettings, enabled);
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

    private void updateRepetitionPenaltyEnabled(boolean enabled)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = SettingsCoordinator.withRepetitionPenaltyEnabled(activeModelSettings, enabled);
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
        if (activeSession != null)
        {
            retryHistory.clear(activeSession);
        }
        updateRetryCountLabel();
    }

    private void updateRetryCountLabel()
    {
        int count = activeSession == null ? 0 : Math.max(0, retryHistory.size(activeSession) - 1);
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
        if (storyPaneController == null)
        {
            return;
        }
        storyPaneController.renderBlocks(blocks, forceScroll);
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
            database.inTransaction(connection ->
            {
                blockRepository.deleteById(block.id());
                deleteLinkedImageIfPresent(block);
            });
            blocks = blockRepository.listForStory(activeStory.id());
            activeSession = activeSession.advance(blocks);
            retryHistory.activate(activeSession);
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
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null)
        {
            current = current.getCause();
        }
        if (current instanceof Exception e)
        {
            return e;
        }
        return new RuntimeException(current);
    }

    private void logAutoCardsError(String message, Exception e)
    {
        System.err.println(message + ": " + e.getMessage());
    }

    private void showError(String message, Exception e)
    {
        Exception error = asException(e);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(primaryStage);
        alert.setTitle("LlamaQuill");
        alert.setHeaderText(message);
        String userMessage = error.getMessage();
        alert.setContentText(userMessage == null || userMessage.isBlank()
                ? error.getClass().getSimpleName()
                : userMessage);

        TextArea diagnostics = new TextArea(diagnosticText(error));
        diagnostics.setEditable(false);
        diagnostics.setWrapText(false);
        diagnostics.setMaxWidth(Double.MAX_VALUE);
        diagnostics.setMaxHeight(Double.MAX_VALUE);
        alert.getDialogPane().setExpandableContent(diagnostics);
        alert.showAndWait();
    }

    private static String diagnosticText(Exception error)
    {
        if (error instanceof OllamaException ollamaError)
        {
            return ollamaError.diagnosticText();
        }
        StringWriter text = new StringWriter();
        error.printStackTrace(new PrintWriter(text));
        return text.toString();
    }

    private void showError(String message, Throwable error)
    {
        if (error instanceof Exception exception)
        {
            showError(message, exception);
            return;
        }
        if (error != null)
        {
            showError(message, new RuntimeException(error));
        }
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
