package com.llamaquill;

import com.llamaquill.db.ImageRepository;
import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.db.AppSettingsRepository;
import com.llamaquill.db.ModelSettingsRepository;
import com.llamaquill.db.StoryCardCommandPresetRepository;
import com.llamaquill.generation.AuxiliaryGenerationService;
import com.llamaquill.generation.GenerationCoordinator;
import com.llamaquill.generation.LastContextDialog;
import com.llamaquill.generation.PromptDialog;
import com.llamaquill.generation.StoryPromptCoordinator;
import com.llamaquill.image.ImageGenerationCoordinator;
import com.llamaquill.image.SeeDialog;
import com.llamaquill.model.Block;
import com.llamaquill.model.AppSettings;
import com.llamaquill.model.ConversationLayout;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.ModelSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.model.StoryImage;
import com.llamaquill.model.StoryCardWrappingStyle;
import com.llamaquill.imports.AIDungeonImports;
import com.llamaquill.imports.ImportDialogs;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.serviceClients.ComfyUiClient;
import com.llamaquill.serviceClients.OllamaChatResult;
import com.llamaquill.serviceClients.OllamaChatRequestSnapshot;
import com.llamaquill.serviceClients.OllamaClient;
import com.llamaquill.serviceClients.OllamaEndpoint;
import com.llamaquill.serviceClients.OllamaException;
import com.llamaquill.serviceClients.OllamaModelDetails;
import com.llamaquill.retry.RetryHistoryDialog;
import com.llamaquill.session.StoryOperationRegistry;
import com.llamaquill.session.StoryRetryHistory;
import com.llamaquill.session.StorySession;
import com.llamaquill.session.StoryWorkspace;
import com.llamaquill.settings.SettingsCoordinator;
import com.llamaquill.settings.SettingsPaneController;
import com.llamaquill.stories.StoryBlockService;
import com.llamaquill.stories.StoryCloneService;
import com.llamaquill.stories.StoryDialogs;
import com.llamaquill.stories.StoryLibraryController;
import com.llamaquill.stories.StoryService;
import com.llamaquill.storycards.StoryCardDialogs;
import com.llamaquill.storycards.StoryCardGenerationCoordinator;
import com.llamaquill.storycards.StoryCardLibraryController;
import com.llamaquill.storycards.StoryCardPresetService;
import com.llamaquill.storycards.StoryCardService;
import com.llamaquill.storyview.StoryPaneController;
import com.llamaquill.util.Timestamps;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
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
    private PromptCompiler promptCompiler;
    private GenerationCoordinator generationCoordinator;
    private AuxiliaryGenerationService auxiliaryGenerationService;
    private StoryPromptCoordinator storyPromptCoordinator;
    private ImageGenerationCoordinator imageGenerationCoordinator;
    private StoryCardGenerationCoordinator storyCardGenerationCoordinator;
    private StoryCardPresetService storyCardPresetService;
    private StoryBlockService storyBlockService;
    private StoryCloneService storyCloneService;
    private StoryService storyService;
    private StoryCardService storyCardService;
    private AIDungeonImports aiDungeonImports;
    private OllamaClient ollamaClient;
    private ComfyUiClient comfyUiClient;
    private AppSettings appSettings;
    private ModelSettings activeModelSettings;
    private GenerationSettings settings;
    private ExecutorService executor;

    private final StoryWorkspace storyWorkspace = new StoryWorkspace();
    private final StoryOperationRegistry storyOperations = new StoryOperationRegistry();
    private final Set<Task<?>> backgroundTasks = ConcurrentHashMap.newKeySet();

    private Stage primaryStage;
    private StoryPaneController storyPaneController;
    private StoryLibraryController storyLibraryController;
    private StoryCardLibraryController storyCardLibraryController;
    private Label statusLabel;
    private Button continueButton;
    private Button takeTurnButton;
    private Button retryButton;
    private Button deleteButton;
    private Button retryHistoryButton;
    private Button seeButton;
    private Button promptButton;
    private Button contextButton;
    private volatile OllamaChatRequestSnapshot lastOllamaRequest;

    private Button collapseRightButton;
    private VBox rightSidebar;
    private TabPane rightTabs;

    private TextArea systemPromptArea;
    private TextArea plotEssentialsArea;
    private TextArea authorNoteArea;

    private final StoryRetryHistory<RetryHistoryEntry> retryHistory = new StoryRetryHistory<>();

    private SettingsPaneController settingsPaneController;
    private boolean modelRefreshInProgress;
    private boolean updatingStoryDetails;
    private boolean storyDetailsDirty;

    private List<String> comfyWorkflowNames = new ArrayList<>();
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

    private record StoryTaskContext(StorySession session, Story story, AppSettings appSettings,
            GenerationSettings generationSettings)
    {
    }

    private record ModelDiscoveryResult(String endpoint, List<String> models, String selectedModel,
            OllamaModelDetails selectedDetails, Exception metadataError)
    {
    }

    private Story currentStory()
    {
        return storyWorkspace.story();
    }

    private StorySession currentSession()
    {
        return storyWorkspace.session();
    }

    private List<Block> currentBlocks()
    {
        return storyWorkspace.blocks();
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
            storyService = new StoryService(storyRepository, blockRepository);
            storyCardService = new StoryCardService(cardRepository);
            ImageRepository imageRepository = new ImageRepository(database);
            storyCloneService = new StoryCloneService(
                    database, storyRepository, blockRepository, cardRepository, imageRepository);
            appSettingsRepository = new AppSettingsRepository(database);
            modelSettingsRepository = new ModelSettingsRepository(database);
            StoryCardCommandPresetRepository presetRepository = new StoryCardCommandPresetRepository(database);
            promptCompiler = new PromptCompiler();
            aiDungeonImports = new AIDungeonImports(database, storyRepository, blockRepository, cardRepository,
                    imageRepository, DEFAULT_SYSTEM_PROMPT);
            ollamaClient = new OllamaClient();
            ollamaClient.setChatRequestObserver(this::captureLastOllamaRequest);
            comfyUiClient = new ComfyUiClient();
            generationCoordinator = new GenerationCoordinator(database, blockRepository, storyRepository, cardRepository,
                    promptCompiler, ollamaClient);
            auxiliaryGenerationService = new AuxiliaryGenerationService(promptCompiler, ollamaClient);
            storyPromptCoordinator = new StoryPromptCoordinator(
                    blockRepository, cardRepository, auxiliaryGenerationService);
            storyCardGenerationCoordinator = new StoryCardGenerationCoordinator(
                    blockRepository, cardRepository, auxiliaryGenerationService);
            storyCardPresetService = new StoryCardPresetService(presetRepository);
            imageGenerationCoordinator = new ImageGenerationCoordinator(database, imageRepository, blockRepository,
                    storyRepository, cardRepository, auxiliaryGenerationService, comfyUiClient);
            storyBlockService = new StoryBlockService(
                    database, blockRepository, imageGenerationCoordinator::deleteImageById);
            appSettings = loadOrCreateAppSettings();
            refreshComfyWorkflowNames();
            ensureValidComfyWorkflowSelection();
            ollamaClient.setHost(appSettings.ollamaUrl());
            comfyUiClient.setHost(appSettings.comfyUiUrl());
            activeModelSettings = loadActiveModelSettings(appSettings.selectedModel());
            ollamaClient.setModel(activeModelSettings.modelName());
            settings = buildGenerationSettings();
            executor = Executors.newSingleThreadExecutor();

            StoryService.StoryDocument initialDocument =
                    storyService.loadOrCreate("Untitled Story", DEFAULT_SYSTEM_PROMPT);
            storyWorkspace.open(initialDocument.story(), initialDocument.blocks());
            retryHistory.activate(currentSession());
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

        contextButton = new Button("View Last Context");
        contextButton.setDisable(true);
        contextButton.setTooltip(new Tooltip(
                "Shows the exact role-aware message list most recently submitted to Ollama."));
        contextButton.setOnAction(event -> LastContextDialog.show(primaryStage, lastOllamaRequest));

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

        storyLibraryController = new StoryLibraryController(
                LEFT_SIDEBAR_WIDTH,
                this::showNewStoryDialog,
                this::showImportAdventureDialog,
                this::showStoryDialog);
        storyCardLibraryController = new StoryCardLibraryController(
                () -> showCardDialog(null),
                this::showImportCardsDialog,
                this::showCardDialog);

        var statusBar = new HBox(statusLabel);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(8, 12, 8, 12));

        var root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setLeft(storyLibraryController.root());
        root.setCenter(storyPaneController.buildCenterPane(
                takeTurnButton, continueButton, seeButton, retryButton, retryHistoryButton,
                deleteButton, promptButton, contextButton));
        root.setRight(buildRightSidebar());
        root.setBottom(statusBar);

        refreshStoryList(currentStory().id());
        refreshCardList(currentStory().id());
        populateStoryDetails(currentStory());
        renderStoryBlocks(true);
        setStoryDependentControlsEnabled(currentStory() != null);

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
        flushPendingEdits();
        storyOperations.cancelAll();
        for (Task<?> task : backgroundTasks)
        {
            task.cancel(true);
        }
        backgroundTasks.clear();
        if (executor != null)
        {
            executor.shutdown();
            try
            {
                if (!executor.awaitTermination(3, TimeUnit.SECONDS))
                {
                    executor.shutdownNow();
                }
            }
            catch (InterruptedException e)
            {
                executor.shutdownNow();
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

    private void showTurnInput(boolean show)
    {
        if (storyPaneController != null)
        {
            storyPaneController.showTurnInput(show);
        }
    }

    private void captureLastOllamaRequest(OllamaChatRequestSnapshot snapshot)
    {
        lastOllamaRequest = snapshot;
        try
        {
            Platform.runLater(() ->
            {
                if (contextButton != null)
                {
                    contextButton.setDisable(snapshot == null);
                }
            });
        }
        catch (IllegalStateException ignored)
        {
            // JavaFX is shutting down; the snapshot remains intentionally in memory only.
        }
    }

    private VBox buildRightSidebar()
    {
        collapseRightButton = new Button(">>");
        collapseRightButton.setOnAction(event -> toggleRightSidebar());

        rightTabs = new TabPane();
        rightTabs.getTabs().addAll(buildStoryTab(), storyCardLibraryController.tab(), buildOptionsTab());
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

        VBox content = new VBox(10, new Label("System Prompt"), systemPromptArea, new Label("Plot Essentials"),
                plotEssentialsArea, new Label("Author's Note"), authorNoteArea);
        content.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        return new Tab("Story", scrollPane);
    }

    private Tab buildOptionsTab()
    {
        settingsPaneController = new SettingsPaneController(
                appSettings, activeModelSettings, comfyWorkflowNames, database.paths().databaseFile(),
                new SettingsPaneController.Actions()
                {
                    @Override
                    public void settingChanged(SettingsPaneController.Change change)
                    {
                        applySettingChange(change);
                    }

                    @Override
                    public void modelSelected(String modelName)
                    {
                        selectModel(modelName);
                    }

                    @Override
                    public void refreshModels()
                    {
                        refreshModelsFromOllama(true);
                    }

                    @Override
                    public void backupDatabase()
                    {
                        createDatabaseBackup();
                    }

                    @Override
                    public void checkDatabase()
                    {
                        checkDatabaseHealth();
                    }
                });
        refreshModelSelect();
        return settingsPaneController.tab();
    }

    private void applySettingChange(SettingsPaneController.Change change)
    {
        switch (change.setting())
        {
            case OLLAMA_URL -> updateOllamaUrl(change.stringValue());
            case OLLAMA_KEEP_ALIVE_MINUTES -> updateOllamaKeepAliveMinutes(change.intValue());
            case CONTEXT_LIMIT -> updateContextLimit(change.intValue());
            case STORY_CARD_WRAPPING_STYLE -> updateStoryCardWrappingStyle(
                    (StoryCardWrappingStyle) change.value());
            case CONVERSATION_LAYOUT -> updateConversationLayout(
                    (ConversationLayout) change.value());
            case RESPONSE_LENGTH -> updateResponseLength(change.intValue());
            case RESPONSE_LENGTH_ENABLED -> updateResponseLengthEnabled(change.booleanValue());
            case TEMPERATURE -> updateTemperature(roundTo(change.doubleValue(), 0.1));
            case TEMPERATURE_ENABLED -> updateTemperatureEnabled(change.booleanValue());
            case TOP_K -> updateTopK(change.intValue());
            case TOP_K_ENABLED -> updateTopKEnabled(change.booleanValue());
            case TOP_P -> updateTopP(roundTo(change.doubleValue(), 0.01));
            case TOP_P_ENABLED -> updateTopPEnabled(change.booleanValue());
            case MIN_P -> updateMinP(roundTo(change.doubleValue(), 0.001));
            case MIN_P_ENABLED -> updateMinPEnabled(change.booleanValue());
            case TYPICAL_P -> updateTypicalP(roundTo(change.doubleValue(), 0.01));
            case TYPICAL_P_ENABLED -> updateTypicalPEnabled(change.booleanValue());
            case PRESENCE_PENALTY -> updatePresencePenalty(roundTo(change.doubleValue(), 0.01));
            case PRESENCE_PENALTY_ENABLED -> updatePresencePenaltyEnabled(change.booleanValue());
            case FREQUENCY_PENALTY -> updateFrequencyPenalty(roundTo(change.doubleValue(), 0.01));
            case FREQUENCY_PENALTY_ENABLED -> updateFrequencyPenaltyEnabled(change.booleanValue());
            case REPEAT_LAST_N -> updateRepeatLastN(change.intValue());
            case REPEAT_LAST_N_ENABLED -> updateRepeatLastNEnabled(change.booleanValue());
            case REPETITION_PENALTY -> updateRepetitionPenalty(roundTo(change.doubleValue(), 0.01));
            case REPETITION_PENALTY_ENABLED -> updateRepetitionPenaltyEnabled(change.booleanValue());
            case MIN_STORY_PERCENT -> updateMinStoryPercent(change.intValue());
            case STORY_CARD_LOOKBACK -> updateStoryCardLookback(change.intValue());
            case COMFY_UI_URL -> updateComfyUiUrl(change.stringValue());
            case COMFY_WORKFLOW -> updateComfyWorkflow(change.stringValue());
            case COMFY_WIDTH -> updateComfyWidth(change.intValue());
            case COMFY_HEIGHT -> updateComfyHeight(change.intValue());
            case COMFY_BATCH_SIZE -> updateComfyBatchSize(change.intValue());
        }
    }

    private void createDatabaseBackup()
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
    }

    private void checkDatabaseHealth()
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
        area.textProperty().addListener((obs, oldValue, newValue) ->
        {
            if (!updatingStoryDetails && currentStory() != null)
            {
                storyDetailsDirty = true;
                if (statusLabel != null && !storyOperations.hasActive(currentStory().id()))
                {
                    statusLabel.setText("Unsaved story details");
                }
            }
        });
        area.focusedProperty().addListener((obs, oldValue, newValue) ->
        {
            if (!newValue)
            {
                saveStoryDetails();
            }
        });
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

    private void refreshModelsFromOllama(boolean userInitiated)
    {
        if (modelRefreshInProgress || executor == null)
        {
            return;
        }

        modelRefreshInProgress = true;
        if (settingsPaneController != null)
        {
            settingsPaneController.setRefreshInProgress(true);
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
                            settingsPaneController.setModelDetails("No local Ollama models were found.");
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
                                settingsPaneController.setModelDetails("Model metadata unavailable: "
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
                    if (settingsPaneController != null)
                    {
                        settingsPaneController.setModelDetails(
                                "Model discovery failed. Existing saved settings remain available.");
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
        if (settingsPaneController != null)
        {
            settingsPaneController.setRefreshInProgress(false);
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
                minStoryWindow, appSettings.storyCardLookback(),
                appSettings.ollamaKeepAliveMinutes(),
                activeModelSettings.storyCardWrappingStyle(), activeModelSettings.conversationLayout());
    }

    private void refreshModelSelect()
    {
        if (settingsPaneController == null)
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
            settingsPaneController.setModels(names, appSettings.selectedModel());
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
        appSettings = SettingsCoordinator.withSelectedModel(appSettings, modelName);
        persistAppSettings();
        ollamaClient.setModel(modelName);
        applyModelDetails(details);
        updateModelControls();
        refreshGenerationSettings();
    }

    private void loadModelDetails(String modelName)
    {
        String endpoint = ollamaClient.getHost();
        if (settingsPaneController != null)
        {
            settingsPaneController.setModelDetails("Loading model metadata...");
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
                        settingsPaneController.setModelDetails(
                                "Model metadata unavailable: " + taskErrorMessage(error));
                        statusLabel.setText("Could not validate model context.");
                        showError("Failed to inspect Ollama model", error);
                    }
                });
    }

    private void applyModelDetails(OllamaModelDetails details)
    {
        if (settingsPaneController == null || activeModelSettings == null)
        {
            return;
        }
        if (details == null)
        {
            settingsPaneController.setModelDetails("Model metadata has not been loaded.");
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
        settingsPaneController.setModelDetails(summary);
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
            if (settingsPaneController != null)
            {
                settingsPaneController.setOllamaUrl(appSettings.ollamaUrl());
            }
            showError("Invalid Ollama URL", e);
            return;
        }
        if (normalized.equals(appSettings.ollamaUrl()))
        {
            if (settingsPaneController != null)
            {
                settingsPaneController.setOllamaUrl(normalized);
            }
            return;
        }
        appSettings = SettingsCoordinator.withOllamaUrl(appSettings, normalized);
        persistAppSettings();
        ollamaClient.setHost(appSettings.ollamaUrl());
        if (settingsPaneController != null)
        {
            settingsPaneController.setOllamaUrl(appSettings.ollamaUrl());
        }
        modelDetailsByName.clear();
        refreshModelsFromOllama(false);
    }

    private void updateComfyUiUrl(String url)
    {
        String normalized = url == null ? "" : url.trim();
        if (normalized.isBlank())
        {
            if (settingsPaneController != null)
            {
                settingsPaneController.setComfyUiUrl(appSettings.comfyUiUrl());
            }
            return;
        }
        if (normalized.equals(appSettings.comfyUiUrl()))
        {
            if (settingsPaneController != null)
            {
                settingsPaneController.setComfyUiUrl(normalized);
            }
            return;
        }
        appSettings = SettingsCoordinator.withComfyUiUrl(appSettings, normalized);
        persistAppSettings();
        comfyUiClient.setHost(appSettings.comfyUiUrl());
        if (settingsPaneController != null)
        {
            settingsPaneController.setComfyUiUrl(appSettings.comfyUiUrl());
        }
    }

    private void updateComfyWorkflow(String workflowName)
    {
        String normalized = workflowName == null ? "" : workflowName.trim();
        if (normalized.isBlank())
        {
            if (settingsPaneController != null)
            {
                settingsPaneController.setComfyWorkflow(appSettings.comfyWorkflow());
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
            ModelSettings calibrated = SettingsCoordinator.calibratePromptTokenScale(
                    modelSettings, estimatedPromptTokens, response.promptEvalCount());
            if (calibrated.equals(modelSettings))
            {
                return;
            }
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

    private static String readyStatus(OllamaChatResult response)
    {
        if (response == null)
        {
            return "Ready";
        }
        List<String> details = new ArrayList<>();
        if (!response.promptTokensProcessedLabel().isBlank())
        {
            details.add(response.promptTokensProcessedLabel());
        }
        if (!response.clientRequestDurationLabel().isBlank())
        {
            details.add(response.clientRequestDurationLabel());
        }
        if (!response.generationDurationLabel().isBlank())
        {
            details.add(response.generationDurationLabel());
        }
        return details.isEmpty() ? "Ready" : "Ready - " + String.join(" - ", details);
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
        boolean busy = currentSession() != null && storyOperations.hasActive(currentSession().storyId());
        setStoryActionButtonsBusy(busy);
        if (!busy && currentStory() != null && retryHistoryButton != null)
        {
            retryHistoryButton.setDisable(retryHistory.size(currentSession()) < 2);
        }
    }

    private void updateModelControls()
    {
        if (activeModelSettings == null || settingsPaneController == null)
        {
            return;
        }
        OllamaModelDetails details = modelDetailsByName.get(activeModelSettings.modelName());
        settingsPaneController.applyModelSettings(activeModelSettings, details);
    }

    private StoryTaskContext captureStoryTaskContext()
    {
        if (currentSession() == null || currentStory() == null || appSettings == null || activeModelSettings == null)
        {
            return null;
        }
        return new StoryTaskContext(currentSession(), currentStory(), appSettings, buildGenerationSettings());
    }

    private void refreshStoryList(String selectedId)
    {
        try
        {
            storyLibraryController.setStories(storyService.list(), selectedId);
        }
        catch (SQLException e)
        {
            showError("Failed to load stories", e);
        }
    }

    private void refreshCardList(String storyId)
    {
        if (storyId == null)
        {
            storyCardLibraryController.clear();
            return;
        }
        try
        {
            storyCardLibraryController.setCards(storyCardService.listForStory(storyId));
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
                Story story = storyService.create(trimmed, DEFAULT_SYSTEM_PROMPT);
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
                () -> showCloneStoryDialog(story),
                () -> confirmDelete(story.title()),
                () -> deleteStory(story));
    }

    private void showCloneStoryDialog(Story source)
    {
        flushPendingEdits();
        StoryDialogs.showCloneStoryDialog(primaryStage, source, this::showInfo, request ->
        {
            try
            {
                Story clone = storyCloneService.cloneStory(source.id(), request);
                refreshStoryList(clone.id());
                loadStory(clone, true);
                statusLabel.setText("Story cloned");
            }
            catch (SQLException | IllegalArgumentException e)
            {
                showError("Failed to clone story", e);
            }
        });
    }

    private void showCardDialog(StoryCard card)
    {
        StoryTaskContext context = captureStoryTaskContext();
        if (context == null)
        {
            showInfo("Select a story and model first.");
            return;
        }
        Story story = context.story();
        StoryCardDialogs.showCardDialog(
                primaryStage,
                story.id(),
                card,
                storyCardPresetService,
                this::showInfo,
                this::showError,
                statusLabel::setText,
                (request, onSuccess, onFailure) -> submitStoryTask(
                        context.session(),
                        "Generate Story Card",
                        () -> storyCardGenerationCoordinator.generate(
                                story, request, context.generationSettings()),
                        result ->
                        {
                            recordOllamaResponse(
                                    result.compilation().estimatedTokens(), result.response());
                            onSuccess.accept(result);
                        },
                        onFailure),
                savedCard ->
                {
                    if (card == null)
                    {
                        storyCardService.create(savedCard);
                    }
                    else
                    {
                        storyCardService.update(savedCard);
                    }
                    if (currentStory() != null && currentStory().id().equals(story.id()))
                    {
                        refreshCardList(story.id());
                    }
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

    private void showPromptDialog()
    {
        StoryTaskContext context = captureStoryTaskContext();
        if (context == null)
        {
            showInfo("Select a story and model first.");
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
                (systemPrompt, userPrompt, overrideNumPredict, forceRoleAwareTurns, onSuccess, onFailure) ->
                        submitStoryTask(
                                context.session(), "Story Prompt",
                                () -> storyPromptCoordinator.generateResponse(
                                        story,
                                        systemPrompt,
                                        userPrompt,
                                        context.generationSettings(),
                                        overrideNumPredict,
                                        forceRoleAwareTurns),
                                result ->
                                {
                                    recordOllamaResponse(
                                            result.compilation().estimatedTokens(), result.response());
                                    onSuccess.accept(result.content());
                                },
                                onFailure));
    }

    private void showSeeDialog()
    {
        showSeeDialog(null, null);
    }

    private void showSeeDialog(String initialPrompt, Block replaceImageBlock)
    {
        StoryTaskContext context = captureStoryTaskContext();
        if (context == null)
        {
            showInfo("Select a story and model first.");
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
                        () -> imageGenerationCoordinator.generateImagePromptResult(
                                story,
                                request,
                                context.generationSettings()),
                        result ->
                        {
                            recordOllamaResponse(
                                    result.compilation().estimatedTokens(), result.response());
                            onSuccess.accept(result.content());
                        },
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
                            retryHistory.add(currentSession(), new ImageRetryHistoryEntry(storyImage.prompt(),
                                    storyImage.imageBytes(), storyImage.mimeType(), storyImage.workflowJson()));
                            updateRetryCountLabel();
                        }
                    }
                    else
                    {
                        refreshStoryList(currentStory() == null ? null : currentStory().id());
                    }
                    statusLabel.setText(replaceImageBlock == null ? "Inserted image" : "Replaced image");
                });
        setStoryDependentControlsEnabled(currentStory() != null);
    }

    private void deleteCard(StoryCard card)
    {
        try
        {
            storyCardService.delete(card);
            refreshCardList(currentStory().id());
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
        if (currentStory() != null && currentStory().id().equals(story.id()))
        {
            base = currentStory();
        }
        try
        {
            Story updated = storyService.rename(base, name);
            if (currentStory() != null && currentStory().id().equals(updated.id()))
            {
                storyWorkspace.updateStory(updated);
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
        if (currentStory() == null)
        {
            storyDetailsDirty = false;
            return;
        }
        String systemPrompt = systemPromptArea.getText();
        String plotEssentials = plotEssentialsArea.getText();
        String authorNote = authorNoteArea.getText();

        try
        {
            Story original = currentStory();
            Story updated = storyService.updateDetails(original, systemPrompt, plotEssentials, authorNote);
            if (updated == original)
            {
                storyDetailsDirty = false;
                return;
            }
            storyWorkspace.updateStory(updated);
            storyDetailsDirty = false;
            if (statusLabel != null && !storyOperations.hasActive(updated.id()))
            {
                statusLabel.setText("Saved");
            }
            refreshStoryList(currentStory().id());
        }
        catch (SQLException e)
        {
            storyDetailsDirty = true;
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
            storyService.delete(story);
            refreshStoryList(null);
            if (currentStory() != null && currentStory().id().equals(story.id()))
            {
                storyWorkspace.clear();
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
        if (currentStory() == null)
        {
            showInfo("Select a story first.");
            return;
        }
        if (currentBlocks().isEmpty())
        {
            return;
        }
        try
        {
            Block head = currentBlocks().getLast();
            List<Block> refreshedBlocks = storyBlockService.deleteHead(currentStory(), head);
            storyWorkspace.advance(refreshedBlocks);
            retryHistory.activate(currentSession());
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
        try
        {
            Story updated = storyService.touch(story);
            refreshStoryList(updated.id());
            loadStory(updated, true);
        }
        catch (SQLException e)
        {
            showError("Failed to update story timestamp", e);
        }
    }

    private void loadStory(Story story, boolean updateSelection)
    {
        if (currentStory() != null && !currentStory().id().equals(story.id()))
        {
            flushPendingEdits();
        }
        try
        {
            StoryService.StoryDocument document = storyService.load(story);
            storyWorkspace.open(document.story(), document.blocks());
            retryHistory.activate(currentSession());
            renderStoryBlocks(true);
            statusLabel.setText("Ready");
            populateStoryDetails(story);
            refreshCardList(story.id());
            setStoryDependentControlsEnabled(true);
            restoreStoryActionButtonsState();
            updateRetryCountLabel();
            if (updateSelection)
            {
                storyLibraryController.select(story.id());
            }
        }
        catch (SQLException e)
        {
            showError("Failed to load story", e);
        }
    }

    private boolean canApplyToActiveSession(StorySession source)
    {
        return storyWorkspace.canApply(source);
    }

    private void reloadActiveStoryIfCompatible(StorySession source, Story updatedStory, boolean forceScroll)
            throws SQLException
    {
        if (!canApplyToActiveSession(source))
        {
            refreshStoryList(currentStory() == null ? null : currentStory().id());
            return;
        }

        StoryService.StoryDocument document = storyService.reload(source.storyId(), updatedStory);
        storyWorkspace.advance(document.story(), document.blocks());
        retryHistory.activate(currentSession());
        renderStoryBlocks(forceScroll);
        refreshStoryList(source.storyId());
        refreshCardList(source.storyId());
        updateRetryCountLabel();
    }

    private void runRetry()
    {
        if (currentStory() == null)
        {
            showInfo("Select a story first.");
            return;
        }
        if (currentBlocks().isEmpty())
        {
            return;
        }
        Block head = currentBlocks().getLast();
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

        StoryTaskContext context = captureStoryTaskContext();
        if (context == null)
        {
            showInfo("Generation settings are not loaded yet.");
            return;
        }
        StorySession session = context.session();
        List<Block> blockSnapshot = currentBlocks();
        GenerationSettings operationSettings = context.generationSettings();
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
                            retryHistory.add(currentSession(), new TextRetryHistoryEntry(result.updatedBlock().text()));
                            statusLabel.setText(readyStatus(result.ollamaResponse()));
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
        if (currentSession() == null || retryHistory.size(currentSession()) < 2)
        {
            return;
        }
        int retryIndex = retryHistory.selectedIndex(currentSession());
        List<RetryHistoryEntry> historyEntries = retryHistory.entries(currentSession());
        if (retryIndex < 0 || retryIndex >= historyEntries.size())
        {
            retryIndex = historyEntries.size() - 1;
            retryHistory.select(currentSession(), retryIndex);
        }

        Block head = currentBlocks().isEmpty() ? null : currentBlocks().getLast();
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
        retryHistory.select(currentSession(), selectedIndex);
        if (currentBlocks().isEmpty())
        {
            return;
        }

        Block currentHead = currentBlocks().getLast();
        RetryHistoryEntry chosen = retryHistory.selected(currentSession());
        if (currentHead.role() == Role.ASSISTANT && chosen instanceof TextRetryHistoryEntry textEntry)
        {
            if (!textEntry.text.equals(currentHead.text()))
            {
                Block updated = new Block(currentHead.id(), currentHead.storyId(), Role.ASSISTANT, textEntry.text,
                        Timestamps.now(), currentHead.position());
                try
                {
                    if (!storyBlockService.replaceHeadIfCurrent(updated))
                    {
                        statusLabel.setText("Retry selection was stale; the story was not changed.");
                        return;
                    }
                    storyWorkspace.replaceHead(updated);
                    retryHistory.activate(currentSession());
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
        if (currentSession() == null || imageHead == null || imageHead.role() != Role.IMAGE
                || !retryHistory.isEmpty(currentSession()))
        {
            return;
        }
        StoryImage image = loadStoryImage(imageHead.text());
        if (image == null || image.imageBytes() == null || image.imageBytes().length == 0)
        {
            return;
        }
        retryHistory.add(currentSession(),
                new ImageRetryHistoryEntry(image.prompt(), image.imageBytes(), image.mimeType(), image.workflowJson()));
        updateRetryCountLabel();
    }

    private void replaceImageBlockFromRetryHistory(Block headBlock, ImageRetryHistoryEntry imageEntry) throws SQLException
    {
        if (currentStory() == null || headBlock == null || headBlock.role() != Role.IMAGE)
        {
            return;
        }
        ImageGenerationCoordinator.ImageMutationResult result = imageGenerationCoordinator.replaceImageFromRetryHistory(
                currentStory(), headBlock, imageEntry.prompt, imageEntry.bytes, imageEntry.mimeType, imageEntry.workflowJson);
        if (result.stale())
        {
            statusLabel.setText("Retry selection was stale; the story was not changed.");
            return;
        }
        StorySession source = currentSession();
        reloadActiveStoryIfCompatible(source, result.updatedStory(), true);
        statusLabel.setText("Applied image retry selection");
    }

    private void showImportCardsDialog()
    {
        if (currentStory() == null)
        {
            showInfo("Select a story first.");
            return;
        }

        String storyId = currentStory().id();
        ImportDialogs.showStoryCardsImportDialog(
                primaryStage,
                this::showInfo,
                this::showError,
                (path, replaceExisting) -> aiDungeonImports.importStoryCards(path, storyId, replaceExisting),
                imported -> refreshCardList(storyId));
    }

    private void populateStoryDetails(Story story)
    {
        updatingStoryDetails = true;
        try
        {
            if (story == null)
            {
                systemPromptArea.setText("");
                plotEssentialsArea.setText("");
                authorNoteArea.setText("");
            }
            else
            {
                systemPromptArea.setText(story.systemPrompt());
                plotEssentialsArea.setText(story.plotEssentials());
                authorNoteArea.setText(story.authorNote());
            }
            storyDetailsDirty = false;
        }
        finally
        {
            updatingStoryDetails = false;
        }
    }

    private void setStoryDependentControlsEnabled(boolean enabled)
    {
        continueButton.setDisable(!enabled);
        takeTurnButton.setDisable(!enabled);
        retryButton.setDisable(!enabled);
        deleteButton.setDisable(!enabled);
        retryHistoryButton.setDisable(!enabled || currentSession() == null || retryHistory.size(currentSession()) < 2);
        seeButton.setDisable(!enabled);
        promptButton.setDisable(!enabled);
        systemPromptArea.setDisable(!enabled);
        plotEssentialsArea.setDisable(!enabled);
        authorNoteArea.setDisable(!enabled);
        storyCardLibraryController.setEnabled(enabled);
    }

    private void submitTurn()
    {
        if (currentStory() == null)
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
        if (currentStory() == null)
        {
            showInfo("Select a story first.");
            return;
        }

        StoryTaskContext context = captureStoryTaskContext();
        if (context == null)
        {
            showInfo("Generation settings are not loaded yet.");
            return;
        }
        GenerationSettings operationSettings = context.generationSettings();
        clearRetryHistory();
        setStoryActionButtonsBusy(true);
        statusLabel.setText("Generating...");
        long streamingToken = storyPaneController.startStreaming(StoryPaneController.StreamingMode.APPEND);
        GenerationCoordinator.GenerationObserver observer = streamingObserver(streamingToken);
        submitStoryTask(context.session(), "Continue", () ->
                {
                    return generationCoordinator.continueStory(context.story(), operationSettings, observer);
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
                        if (currentStory() != null && currentStory().id().equals(context.story().id()))
                        {
                            statusLabel.setText(readyStatus(result.ollamaResponse()));
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
        StoryTaskContext context = captureStoryTaskContext();
        if (context == null)
        {
            showInfo("Generation settings are not loaded yet.");
            return;
        }
        GenerationSettings operationSettings = context.generationSettings();
        setStoryActionButtonsBusy(true);
        statusLabel.setText("Generating...");
        long streamingToken = storyPaneController.startStreaming(StoryPaneController.StreamingMode.TURN);
        GenerationCoordinator.GenerationObserver observer = streamingObserver(streamingToken);
        submitStoryTask(context.session(), "Take A Turn", () ->
                {
                    return generationCoordinator.takeTurn(context.story(), userText, operationSettings, observer);
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
                        if (currentStory() != null && currentStory().id().equals(context.story().id()))
                        {
                            statusLabel.setText(result.generated()
                                    ? readyStatus(result.ollamaResponse())
                                    : "Last generation was empty.");
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

    private void updateContextLimit(int value)
    {
        OllamaModelDetails details = modelDetailsByName.get(activeModelSettings.modelName());
        int maximum = details != null && details.maxContextLength() > 0
                ? Math.min(ModelSettings.MAX_CONTEXT_LIMIT, details.maxContextLength())
                : ModelSettings.MAX_CONTEXT_LIMIT;
        activeModelSettings = SettingsCoordinator.withContextLimit(activeModelSettings, Math.min(value, maximum));
        updateModelControls();
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateStoryCardWrappingStyle(StoryCardWrappingStyle value)
    {
        activeModelSettings = SettingsCoordinator.withStoryCardWrappingStyle(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateConversationLayout(ConversationLayout value)
    {
        activeModelSettings = SettingsCoordinator.withConversationLayout(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateResponseLength(int value)
    {
        appSettings = SettingsCoordinator.withResponseLength(appSettings, value);
        persistAppSettings();
        refreshGenerationSettings();
    }

    private void updateOllamaKeepAliveMinutes(int minutes)
    {
        appSettings = SettingsCoordinator.withOllamaKeepAliveMinutes(appSettings, minutes);
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
        activeModelSettings = SettingsCoordinator.withTemperature(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateTemperatureEnabled(boolean enabled)
    {
        activeModelSettings = SettingsCoordinator.withTemperatureEnabled(activeModelSettings, enabled);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateTopK(int value)
    {
        activeModelSettings = SettingsCoordinator.withTopK(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateTopKEnabled(boolean enabled)
    {
        activeModelSettings = SettingsCoordinator.withTopKEnabled(activeModelSettings, enabled);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateTopP(double value)
    {
        activeModelSettings = SettingsCoordinator.withTopP(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateTopPEnabled(boolean enabled)
    {
        activeModelSettings = SettingsCoordinator.withTopPEnabled(activeModelSettings, enabled);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateMinP(double value)
    {
        activeModelSettings = SettingsCoordinator.withMinP(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateMinPEnabled(boolean enabled)
    {
        activeModelSettings = SettingsCoordinator.withMinPEnabled(activeModelSettings, enabled);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateTypicalP(double value)
    {
        activeModelSettings = SettingsCoordinator.withTypicalP(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateTypicalPEnabled(boolean enabled)
    {
        activeModelSettings = SettingsCoordinator.withTypicalPEnabled(activeModelSettings, enabled);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updatePresencePenalty(double value)
    {
        activeModelSettings = SettingsCoordinator.withPresencePenalty(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updatePresencePenaltyEnabled(boolean enabled)
    {
        activeModelSettings = SettingsCoordinator.withPresencePenaltyEnabled(activeModelSettings, enabled);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateFrequencyPenalty(double value)
    {
        activeModelSettings = SettingsCoordinator.withFrequencyPenalty(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateFrequencyPenaltyEnabled(boolean enabled)
    {
        activeModelSettings = SettingsCoordinator.withFrequencyPenaltyEnabled(activeModelSettings, enabled);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateRepeatLastN(int value)
    {
        activeModelSettings = SettingsCoordinator.withRepeatLastN(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateRepeatLastNEnabled(boolean enabled)
    {
        activeModelSettings = SettingsCoordinator.withRepeatLastNEnabled(activeModelSettings, enabled);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateRepetitionPenalty(double value)
    {
        activeModelSettings = SettingsCoordinator.withRepetitionPenalty(activeModelSettings, value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateRepetitionPenaltyEnabled(boolean enabled)
    {
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

    private void refreshGenerationSettings()
    {
        settings = buildGenerationSettings();
    }

    private void clearRetryHistory()
    {
        if (currentSession() != null)
        {
            retryHistory.clear(currentSession());
        }
        updateRetryCountLabel();
    }

    private void updateRetryCountLabel()
    {
        int count = currentSession() == null ? 0 : Math.max(0, retryHistory.size(currentSession()) - 1);
        boolean hasRetries = count > 0;
        retryHistoryButton.setText(hasRetries ? String.valueOf(count) : "");
        retryHistoryButton.setVisible(hasRetries);
        retryHistoryButton.setManaged(hasRetries);
    }

    private static double roundTo(double value, double step)
    {
        return Math.round(value / step) * step;
    }

    private void renderStoryBlocks(boolean forceScroll)
    {
        if (storyPaneController == null)
        {
            return;
        }
        storyPaneController.renderBlocks(currentBlocks(), forceScroll);
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
            List<Block> refreshedBlocks = storyBlockService.delete(currentStory(), block);
            storyWorkspace.advance(refreshedBlocks);
            retryHistory.activate(currentSession());
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

    private void persistBlockTextAsync(String blockId, String text, Runnable onSuccess, Consumer<Exception> onFailure)
    {
        Block originalBlock = storyWorkspace.findBlock(blockId);
        if (originalBlock == null)
        {
            onFailure.accept(new IllegalStateException("Story block is no longer available: " + blockId));
            return;
        }
        if (originalBlock != null && storyWorkspace.updateBlockText(blockId, originalBlock.text(), text))
        {
            retryHistory.activate(currentSession());
        }
        if (statusLabel != null)
        {
            statusLabel.setText("Saving...");
        }
        if (executor == null)
        {
            try
            {
                storyBlockService.updateText(originalBlock, text);
                onSuccess.run();
                if (statusLabel != null && isCurrentStory(originalBlock))
                {
                    statusLabel.setText("Saved");
                }
            }
            catch (SQLException e)
            {
                rollbackWorkspaceBlockEdit(originalBlock, text);
                onFailure.accept(e);
            }
            return;
        }

        CompletableFuture.runAsync(() ->
        {
            try
            {
                storyBlockService.updateText(originalBlock, text);
            }
            catch (SQLException e)
            {
                throw new RuntimeException(e);
            }
        }, executor).thenRun(() -> Platform.runLater(() ->
        {
            onSuccess.run();
            if (statusLabel != null && isCurrentStory(originalBlock))
            {
                statusLabel.setText("Saved");
            }
        })).exceptionally(throwable ->
        {
            Platform.runLater(() ->
            {
                rollbackWorkspaceBlockEdit(originalBlock, text);
                onFailure.accept(asException(throwable));
            });
            return null;
        });
    }

    private void rollbackWorkspaceBlockEdit(Block originalBlock, String attemptedText)
    {
        if (!isCurrentStory(originalBlock))
        {
            return;
        }
        if (storyWorkspace.updateBlockText(originalBlock.id(), attemptedText, originalBlock.text()))
        {
            retryHistory.activate(currentSession());
        }
    }

    private boolean isCurrentStory(Block block)
    {
        return block != null && currentStory() != null && currentStory().id().equals(block.storyId());
    }

    private void flushPendingEdits()
    {
        if (storyPaneController != null)
        {
            storyPaneController.commitActiveEdit();
        }
        if (storyDetailsDirty)
        {
            saveStoryDetails();
        }
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
        if (args.length == 1 && "--smoke-test".equals(args[0]))
        {
            int exitCode = runSmokeTest();
            // A jpackage launcher initializes the JavaFX runtime for an Application subclass
            // before invoking main. Force that runtime down after the non-UI smoke path.
            System.exit(exitCode);
            return;
        }
        launch(args);
    }

    private static int runSmokeTest()
    {
        try (Database smokeDatabase = Database.open())
        {
            Database.Diagnostics diagnostics = smokeDatabase.diagnostics();
            if (!diagnostics.healthy() || diagnostics.schemaVersion() != AppVersion.DATABASE_SCHEMA)
            {
                System.err.println("LlamaQuill smoke test failed database diagnostics: " + diagnostics);
                return 1;
            }
            System.out.println(AppVersion.displayName() + " smoke test passed using " + diagnostics.databaseFile());
            return 0;
        }
        catch (Exception e)
        {
            e.printStackTrace(System.err);
            return 1;
        }
    }
}
