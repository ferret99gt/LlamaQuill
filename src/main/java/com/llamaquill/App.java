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
import com.llamaquill.serviceClients.OllamaClient;
import com.llamaquill.retry.RetryHistoryDialog;
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
import javafx.geometry.Insets;
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
import javafx.stage.FileChooser;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
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
import java.util.concurrent.Callable;
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

    private final List<RetryHistoryEntry> retryHistory = new ArrayList<>();
    private int retryIndex = -1;

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

    private List<String> comfyWorkflowNames = new ArrayList<>();
    private static final double TOKEN_SCALE_DEFAULT = 1.0;
    private static final double TOKEN_SCALE_MIN = 0.7;
    private static final double TOKEN_SCALE_MAX = 1.6;
    private static final double TOKEN_SCALE_ALPHA = 0.2;
    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    private final Map<String, Double> tokenScaleByModel = new HashMap<>();

    private static final String DEFAULT_SEE_REQUEST = "Generate an image prompt for the most recent scene in the story.";

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
            autoCardsCoordinator = new AutoCardsCoordinator(blockRepository, cardRepository, autoCardsService);
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
                takeTurnButton, continueButton, seeButton, retryButton, retryHistoryButton, deleteButton));
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
            return runAutoCardsIfNeeded(currentBlocks, currentCards, false).ran();
        }
        catch (Exception e)
        {
            logAutoCardsError("Auto Cards failed to run", e);
            return false;
        }
    }

    private <T> void submitTask(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure)
    {
        Task<T> task = new Task<>()
        {
            @Override
            protected T call() throws Exception
            {
                return work.call();
            }
        };

        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onFailure.accept(task.getException()));
        executor.submit(task);
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
        submitTask(() ->
                {
                    List<Block> currentBlocks = blockRepository.listForStory(activeStory.id());
                    List<StoryCard> currentCards = cardRepository.listForStory(activeStory.id());
                    return runAutoCardsIfNeeded(currentBlocks, currentCards, true);
                },
                result ->
                {
                    refreshCardList(activeStory.id());
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

    private AutoCardsCoordinator.RunResult runAutoCardsIfNeeded(List<Block> currentBlocks, List<StoryCard> currentCards, boolean manual)
            throws IOException, InterruptedException, SQLException
    {
        if (appAutoCardsSettings == null || storyAutoCardsSettings == null || modelAutoCardsSettings == null)
        {
            return new AutoCardsCoordinator.RunResult(0, 0, false);
        }
        AutoCardsCoordinator.PreviewCallbacks previewCallbacks = new AutoCardsCoordinator.PreviewCallbacks(
                draft -> runOnUiThreadAndWait(() -> AutoCardsDialogs.showCreateDialog(primaryStage, activeStory.id(), draft)),
                (existing, proposed, summarized) -> runOnUiThreadAndWait(
                        () -> AutoCardsDialogs.showUpdateDialog(primaryStage, existing, proposed, summarized)));
        return autoCardsCoordinator.runIfNeeded(
                activeStory,
                currentBlocks,
                currentCards,
                manual,
                appSettings,
                storyAutoCardsSettings,
                appAutoCardsSettings,
                activeModelSettings,
                modelAutoCardsSettings,
                previewCallbacks);
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
        Story story = activeStory;
        StoryCardDialogs.showGenerateDialog(
                primaryStage,
                activeStory.id(),
                this::showInfo,
                this::showError,
                text -> statusLabel.setText(text),
                (request, onSuccess, onFailure) -> submitTask(
                        () -> autoCardsCoordinator.generateCardDraftFromPrompt(
                                story,
                                request,
                                appSettings,
                                appAutoCardsSettings,
                                activeModelSettings,
                                modelAutoCardsSettings),
                        onSuccess,
                        onFailure),
                savedCard ->
                {
                    cardRepository.insert(savedCard);
                    refreshCardList(activeStory.id());
                });
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

        String header = replaceImageBlock == null ? "Generate an image prompt from the story" : "Retry image generation";
        String insertLabel = replaceImageBlock == null ? "Insert Image" : "Replace Image";
        Story story = activeStory;
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
                (request, onSuccess, onFailure) -> submitTask(
                        () -> imageGenerationCoordinator.generateImagePrompt(
                                story,
                                request,
                                appSettings,
                                activeModelSettings,
                                appAutoCardsSettings,
                                modelAutoCardsSettings),
                        onSuccess,
                        onFailure),
                (promptText, onSuccess, onFailure) -> submitTask(
                        () ->
                        {
                            ComfyUiClient.GenerationResult result = imageGenerationCoordinator.generateImages(appSettings, promptText);
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
        submitTask(() ->
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
                },
                updated ->
                {
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
                },
                error ->
                {
                    restoreStoryActionButtonsState();
                    statusLabel.setText("Error: " + taskErrorMessage(error));
                });
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

        Block head = blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
        boolean imageMode = head != null && head.role() == Role.IMAGE;

        List<RetryHistoryDialog.Entry> entries = new ArrayList<>(retryHistory.size());
        for (RetryHistoryEntry entry : retryHistory)
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
        retryIndex = selectedIndex;
        if (blocks.isEmpty())
        {
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

        clearRetryHistory();
        setStoryActionButtonsBusy(true);
        statusLabel.setText("Generating...");
        submitTask(() ->
                {
                    settings = buildGenerationSettings();
                    GenerationCoordinator.ContinueResult result = generationCoordinator.continueStory(activeStory, settings,
                            App.this::runAutoCardsForGeneration);
                    observePromptCalibration(result.estimatedPromptTokens());
                    activeStory = result.updatedStory();
                    return result.block();
                },
                block ->
                {
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
                },
                error ->
                {
                    restoreStoryActionButtonsState();
                    statusLabel.setText("Error: " + taskErrorMessage(error));
                });
    }

    private void runTurn(String userText)
    {
        setStoryActionButtonsBusy(true);
        statusLabel.setText("Generating...");
        submitTask(() ->
                {
                    settings = buildGenerationSettings();
                    GenerationCoordinator.TurnResult result = generationCoordinator.takeTurn(activeStory, userText, settings,
                            App.this::runAutoCardsForGeneration);
                    observePromptCalibration(result.estimatedPromptTokens());
                    activeStory = result.updatedStory();
                    return result.generated();
                },
                generated ->
                {
                    try
                    {
                        boolean created = Boolean.TRUE.equals(generated);
                        blocks = blockRepository.listForStory(activeStory.id());
                        renderStoryBlocks(true);
                        statusLabel.setText(created ? "Ready" : "Last generation was empty.");
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
                },
                error ->
                {
                    restoreStoryActionButtonsState();
                    statusLabel.setText("Error: " + taskErrorMessage(error));
                });
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
