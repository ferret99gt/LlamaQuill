package com.llamaquill;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.db.AppSettingsRepository;
import com.llamaquill.db.ModelSettingsRepository;
import com.llamaquill.model.Block;
import com.llamaquill.model.AppSettings;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.ModelSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.ollama.OllamaClient;
import com.llamaquill.prompt.PromptCompilation;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.util.Ids;
import com.llamaquill.util.Timestamps;
import javafx.application.Application;
import javafx.beans.binding.DoubleBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
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
import javafx.scene.control.Slider;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.geometry.Rectangle2D;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private PromptCompiler promptCompiler;
    private OllamaClient ollamaClient;
    private AppSettings appSettings;
    private ModelSettings activeModelSettings;
    private GenerationSettings settings;
    private ExecutorService executor;

    private Story activeStory;
    private List<Block> blocks = new ArrayList<>();

    private final ObservableList<Story> storyItems = FXCollections.observableArrayList();
    private final ObservableList<StoryCard> cardItems = FXCollections.observableArrayList();

    private Stage primaryStage;
    private ScrollPane storyScroll;
    private VBox storyContent;
    private Label statusLabel;
    private Button continueButton;
    private Button takeTurnButton;
    private Button retryButton;
    private Button deleteButton;
    private Button retryHistoryButton;

    private Button newStoryButton;
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
    private ListView<StoryCard> cardList;

    private TextArea turnInputArea;
    private VBox turnInputBox;
    private Button submitTurnButton;
    private Button cancelTurnButton;

    private final List<String> retryHistory = new ArrayList<>();
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
    private ComboBox<String> modelSelect;
    private boolean updatingModelControls;

    private HBox titleBar;
    private double dragOffsetX;
    private double dragOffsetY;
    private double restoreX;
    private double restoreY;
    private double restoreW;
    private double restoreH;
    private boolean customMaximized;
    private boolean forceScrollPending;

    @Override
    public void start(Stage stage)
    {
        this.primaryStage = stage;
        stage.initStyle(StageStyle.UNDECORATED);
        try
        {
            connection = Database.open();
            Database.initialize(connection);
            storyRepository = new StoryRepository(connection);
            blockRepository = new BlockRepository(connection);
            cardRepository = new StoryCardRepository(connection);
            appSettingsRepository = new AppSettingsRepository(connection);
            modelSettingsRepository = new ModelSettingsRepository(connection);
            promptCompiler = new PromptCompiler();
            ollamaClient = new OllamaClient();
            appSettings = loadOrCreateAppSettings();
            ollamaClient.setHost(appSettings.ollamaUrl());
            syncModelsFromOllama();
            activeModelSettings = loadActiveModelSettings(appSettings.selectedModel());
            ollamaClient.setModel(activeModelSettings.modelName());
            settings = buildGenerationSettings();
            executor = Executors.newSingleThreadExecutor();

            activeStory = loadOrCreateStory();
            blocks = blockRepository.listForStory(activeStory.id());
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Failed to initialize database", e);
        }

        storyContent = new VBox(6);
        storyContent.getStyleClass().add("story-content");
        storyContent.heightProperty().addListener((obs, oldValue, newValue) ->
        {
            if (forceScrollPending)
            {
                scrollToBottom();
                forceScrollPending = false;
            }
        });
        storyScroll = new ScrollPane(storyContent);
        storyScroll.setFitToWidth(true);
        storyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        storyScroll.getStyleClass().add("story-scroll");

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

        statusLabel = new Label("Ready");

        var statusBar = new HBox(statusLabel);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(8, 12, 8, 12));

        var root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setLeft(buildStorySidebar());
        root.setCenter(buildCenterPane());
        root.setRight(buildRightSidebar());
        root.setTop(buildTitleBar());
        root.setBottom(statusBar);

        refreshStoryList(activeStory.id());
        refreshCardList(activeStory.id());
        populateStoryDetails(activeStory);
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

        leftSideBar = new VBox(8, headerRow, newStoryButton, storyList);
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
        centerPane.setCenter(storyScroll);

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

        var actionRow = new HBox(8, takeTurnButton, continueButton, retryButton, retryHistoryButton, deleteButton);
        actionRow.getStyleClass().add("action-row");
        actionRow.setAlignment(Pos.CENTER_LEFT);
        actionRow.setPadding(new Insets(10));

        var bottomBox = new VBox(8, turnInputBox, actionRow);
        centerPane.setBottom(bottomBox);

        return centerPane;
    }

    private HBox buildTitleBar()
    {
        Label title = new Label("LlamaQuill");
        title.getStyleClass().add("title-text");
        title.setMaxWidth(Double.MAX_VALUE);

        Button minimize = new Button("–");
        minimize.getStyleClass().add("title-button");
        minimize.setOnAction(event -> primaryStage.setIconified(true));

        Button maximize = new Button("□");
        maximize.getStyleClass().add("title-button");
        maximize.setOnAction(event -> toggleMaximize());

        Button close = new Button("×");
        close.getStyleClass().add("title-button");
        close.setOnAction(event -> primaryStage.close());

        HBox buttons = new HBox(6, minimize, maximize, close);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        titleBar = new HBox(title, buttons);
        titleBar.getStyleClass().add("title-bar");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(6, 10, 6, 10));
        HBox.setHgrow(title, Priority.ALWAYS);

        titleBar.setOnMousePressed(event ->
        {
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });
        titleBar.setOnMouseDragged(event ->
        {
            if (customMaximized)
            {
                return;
            }
            primaryStage.setX(event.getScreenX() - dragOffsetX);
            primaryStage.setY(event.getScreenY() - dragOffsetY);
        });
        titleBar.setOnMouseClicked(event ->
        {
            if (event.getClickCount() == 2)
            {
                toggleMaximize();
            }
        });

        primaryStage.focusedProperty().addListener((obs, oldValue, newValue) ->
        {
            if (newValue)
            {
                titleBar.getStyleClass().remove("inactive");
            }
            else if (!titleBar.getStyleClass().contains("inactive"))
            {
                titleBar.getStyleClass().add("inactive");
            }
        });

        return titleBar;
    }

    private void toggleMaximize()
    {
        if (!customMaximized)
        {
            restoreX = primaryStage.getX();
            restoreY = primaryStage.getY();
            restoreW = primaryStage.getWidth();
            restoreH = primaryStage.getHeight();

            Rectangle2D bounds = Screen
                    .getScreensForRectangle(primaryStage.getX(), primaryStage.getY(), primaryStage.getWidth(),
                            primaryStage.getHeight())
                    .stream().findFirst().map(Screen::getVisualBounds).orElse(Screen.getPrimary().getVisualBounds());
            primaryStage.setX(bounds.getMinX());
            primaryStage.setY(bounds.getMinY());
            primaryStage.setWidth(bounds.getWidth());
            primaryStage.setHeight(bounds.getHeight());
            customMaximized = true;
        }
        else
        {
            primaryStage.setX(restoreX);
            primaryStage.setY(restoreY);
            primaryStage.setWidth(restoreW);
            primaryStage.setHeight(restoreH);
            customMaximized = false;
        }
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

        VBox content = new VBox(10, new Label("System Prompt"), systemPromptArea, new Label("Plot Essentials"),
                plotEssentialsArea, new Label("Author's Note"), authorNoteArea);
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

        VBox content = new VBox(8, newCardButton, cardList);
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

        content.getChildren().addAll(textFieldRow("Ollama URL", ollamaUrlField), comboRow("Model", modelSelect),
                sliderRow("Context Limit", contextLimitSlider, valueLabel(appSettings.contextLimit(), "tokens"),
                        value -> updateContextLimit(value.intValue())),
                sliderRow("Response Length", responseLengthSlider, valueLabel(appSettings.responseLength(), "tokens"),
                        value -> updateResponseLength(value.intValue())),
                sliderRow("Temperature", temperatureSlider, valueLabel(activeModelSettings.temperature(), "", 1),
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
                spinnerRow("Author's Note Insertion Point", anPlacementSpinner, this::updateAnPlacement));

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
            appSettings = new AppSettings(appSettings.ollamaUrl(), fallback.modelName(), appSettings.contextLimit(),
                    appSettings.responseLength(), appSettings.minStoryWindow(), appSettings.storyCardLookback(),
                    appSettings.anPlacement());
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
            appSettings = new AppSettings(appSettings.ollamaUrl(), modelName, appSettings.contextLimit(),
                    appSettings.responseLength(), appSettings.minStoryWindow(), appSettings.storyCardLookback(),
                    appSettings.anPlacement());
            persistAppSettings();
            updateModelControls();
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
        if (url == null || url.isBlank() || url.equals(appSettings.ollamaUrl()))
        {
            return;
        }
        appSettings = new AppSettings(url.trim(), appSettings.selectedModel(), appSettings.contextLimit(),
                appSettings.responseLength(), appSettings.minStoryWindow(), appSettings.storyCardLookback(),
                appSettings.anPlacement());
        persistAppSettings();
        ollamaClient.setHost(appSettings.ollamaUrl());
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
        String now = Timestamps.now();
        Story updated = new Story(story.id(), name, story.systemPrompt(), story.plotEssentials(), story.authorNote(),
                story.createdAt(), now);
        try
        {
            storyRepository.update(updated);
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
            blockRepository.deleteHead(activeStory.id());
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
            renderStoryBlocks(true);
            statusLabel.setText("Ready");
            populateStoryDetails(story);
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
        if (head.role() != Role.ASSISTANT)
        {
            showInfo("The last block is not an assistant response.");
            return;
        }

        continueButton.setDisable(true);
        takeTurnButton.setDisable(true);
        retryButton.setDisable(true);
        deleteButton.setDisable(true);
        retryHistoryButton.setDisable(true);
        statusLabel.setText("Generating...");

        Task<Block> task = new Task<>()
        {
            @Override
            protected Block call() throws Exception
            {
                if (retryHistory.isEmpty())
                {
                    retryHistory.add(head.text());
                    retryIndex = 0;
                }

                List<Block> promptBlocks = new ArrayList<>(blocks);
                promptBlocks.remove(promptBlocks.size() - 1);
                List<StoryCard> currentCards = cardRepository.listForStory(activeStory.id());

                settings = buildGenerationSettings();
                PromptCompilation compilation = promptCompiler.compile(activeStory, promptBlocks, currentCards, settings);
                String response = ollamaClient.generate(compilation.prompt(), settings);
                String cleaned = normalizeOutput(response);
                if (cleaned.isBlank())
                {
                    return null;
                }

                Block updated = new Block(head.id(), head.storyId(), Role.ASSISTANT, cleaned, Timestamps.now(),
                        head.position());
                blockRepository.replaceHead(updated);
                retryHistory.add(cleaned);
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
                continueButton.setDisable(false);
                takeTurnButton.setDisable(false);
                retryButton.setDisable(false);
                deleteButton.setDisable(false);
                return;
            }
            blocks.set(blocks.size() - 1, updated);
            renderStoryBlocks(true);
            statusLabel.setText("Ready");
            updateRetryCountLabel();
            continueButton.setDisable(false);
            takeTurnButton.setDisable(false);
            retryButton.setDisable(false);
            deleteButton.setDisable(false);
            retryHistoryButton.setDisable(retryHistory.size() < 2);
        });

        task.setOnFailed(event ->
        {
            Throwable error = task.getException();
            continueButton.setDisable(false);
            takeTurnButton.setDisable(false);
            retryButton.setDisable(false);
            deleteButton.setDisable(false);
            retryHistoryButton.setDisable(retryHistory.size() < 2);
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

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Retry History");
        dialog.setHeaderText("Select a retry");
        dialog.initOwner(primaryStage);

        TextArea preview = new TextArea(retryHistory.get(retryIndex));
        preview.setWrapText(true);
        preview.setEditable(false);
        preview.setPrefRowCount(8);

        VBox content = new VBox(8, preview);
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
            preview.setText(retryHistory.get(retryIndex));
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
            Block head = blocks.get(blocks.size() - 1);
            if (head.role() != Role.ASSISTANT)
            {
                dialog.close();
                return;
            }
            String chosen = retryHistory.get(retryIndex);
            if (!chosen.equals(head.text()))
            {
                Block updated = new Block(head.id(), head.storyId(), Role.ASSISTANT, chosen, Timestamps.now(), head.position());
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
            dialog.close();
        });

        dialog.showAndWait();
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
        systemPromptArea.setDisable(!enabled);
        plotEssentialsArea.setDisable(!enabled);
        authorNoteArea.setDisable(!enabled);
        newCardButton.setDisable(!enabled);
        cardList.setDisable(!enabled);
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
        continueButton.setDisable(true);
        statusLabel.setText("Generating...");

        Task<Block> task = new Task<>()
        {
            @Override
            protected Block call() throws Exception
            {
                List<Block> currentBlocks = blockRepository.listForStory(activeStory.id());
                List<StoryCard> currentCards = cardRepository.listForStory(activeStory.id());

                settings = buildGenerationSettings();
                PromptCompilation compilation = promptCompiler.compile(activeStory, currentBlocks, currentCards, settings);
                String response = ollamaClient.generate(compilation.prompt(), settings);
                String cleaned = normalizeOutput(response);
                if (cleaned.isBlank())
                {
                    return null;
                }

                int position = blockRepository.nextPosition(activeStory.id());
                Block block = new Block(Ids.newId(), activeStory.id(), Role.ASSISTANT, cleaned, Timestamps.now(), position);
                blockRepository.insert(block);

                String now = Timestamps.now();
                activeStory = new Story(activeStory.id(), activeStory.title(), activeStory.systemPrompt(),
                        activeStory.plotEssentials(), activeStory.authorNote(), activeStory.createdAt(), now);
                storyRepository.update(activeStory);
                return block;
            }
        };

        task.setOnSucceeded(event ->
        {
            Block block = task.getValue();
            if (block == null)
            {
                continueButton.setDisable(false);
                statusLabel.setText("Last generation was empty.");
                return;
            }
            blocks.add(block);
            renderStoryBlocks(true);
            continueButton.setDisable(false);
            statusLabel.setText("Ready");
            refreshStoryList(activeStory.id());
        });

        task.setOnFailed(event ->
        {
            Throwable error = task.getException();
            continueButton.setDisable(false);
            statusLabel.setText("Error: " + (error == null ? "Unknown" : error.getMessage()));
        });

        executor.submit(task);
    }

    private void runTurn(String userText)
    {
        continueButton.setDisable(true);
        takeTurnButton.setDisable(true);
        retryButton.setDisable(true);
        deleteButton.setDisable(true);
        statusLabel.setText("Generating...");

        Task<Boolean> task = new Task<>()
        {
            @Override
            protected Boolean call() throws Exception
            {
                List<Block> currentBlocks = blockRepository.listForStory(activeStory.id());
                boolean isFirstTurn = currentBlocks.isEmpty();

                int position = blockRepository.nextPosition(activeStory.id());
                Role seedRole = isFirstTurn ? Role.ASSISTANT : Role.USER;
                Block seedBlock = new Block(Ids.newId(), activeStory.id(), seedRole, userText, Timestamps.now(), position);
                blockRepository.insert(seedBlock);

                currentBlocks = blockRepository.listForStory(activeStory.id());
                List<StoryCard> currentCards = cardRepository.listForStory(activeStory.id());

                settings = buildGenerationSettings();
                PromptCompilation compilation = promptCompiler.compile(activeStory, currentBlocks, currentCards, settings);
                String response = ollamaClient.generate(compilation.prompt(), settings);
                String cleaned = normalizeOutput(response);
                if (cleaned.isBlank())
                {
                    return Boolean.FALSE;
                }

                int assistantPosition = blockRepository.nextPosition(activeStory.id());
                Block assistantBlock = new Block(Ids.newId(), activeStory.id(), Role.ASSISTANT, cleaned, Timestamps.now(),
                        assistantPosition);
                blockRepository.insert(assistantBlock);

                String now = Timestamps.now();
                activeStory = new Story(activeStory.id(), activeStory.title(), activeStory.systemPrompt(),
                        activeStory.plotEssentials(), activeStory.authorNote(), activeStory.createdAt(), now);
                storyRepository.update(activeStory);
                return Boolean.TRUE;
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
            }
            catch (SQLException e)
            {
                showError("Failed to reload story", e);
            }
            finally
            {
                continueButton.setDisable(false);
                takeTurnButton.setDisable(false);
                retryButton.setDisable(false);
                deleteButton.setDisable(false);
            }
        });

        task.setOnFailed(event ->
        {
            Throwable error = task.getException();
            continueButton.setDisable(false);
            takeTurnButton.setDisable(false);
            retryButton.setDisable(false);
            deleteButton.setDisable(false);
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
        int capped = Math.max(1024, Math.min(32768, value));
        int percent = minStoryPercentSlider == null ? 90 : (int) Math.round(minStoryPercentSlider.getValue());
        int minWindow = (int) Math.round(capped * (percent / 100.0));
        appSettings = new AppSettings(appSettings.ollamaUrl(), appSettings.selectedModel(), capped,
                appSettings.responseLength(), minWindow, appSettings.storyCardLookback(), appSettings.anPlacement());
        persistAppSettings();
        refreshGenerationSettings();
    }

    private void updateResponseLength(int value)
    {
        int capped = Math.max(1, Math.min(250, value));
        appSettings = new AppSettings(appSettings.ollamaUrl(), appSettings.selectedModel(), appSettings.contextLimit(), capped,
                appSettings.minStoryWindow(), appSettings.storyCardLookback(), appSettings.anPlacement());
        persistAppSettings();
        refreshGenerationSettings();
    }

    private void updateTemperature(double value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = new ModelSettings(activeModelSettings.modelName(), activeModelSettings.active(), value,
                activeModelSettings.topK(), activeModelSettings.topP(), activeModelSettings.minP(),
                activeModelSettings.presencePenalty(), activeModelSettings.frequencyPenalty(),
                activeModelSettings.repetitionPenalty());
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateTopK(int value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = new ModelSettings(activeModelSettings.modelName(), activeModelSettings.active(),
                activeModelSettings.temperature(), value, activeModelSettings.topP(), activeModelSettings.minP(),
                activeModelSettings.presencePenalty(), activeModelSettings.frequencyPenalty(),
                activeModelSettings.repetitionPenalty());
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateTopP(double value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = new ModelSettings(activeModelSettings.modelName(), activeModelSettings.active(),
                activeModelSettings.temperature(), activeModelSettings.topK(), value, activeModelSettings.minP(),
                activeModelSettings.presencePenalty(), activeModelSettings.frequencyPenalty(),
                activeModelSettings.repetitionPenalty());
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateMinP(double value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = new ModelSettings(activeModelSettings.modelName(), activeModelSettings.active(),
                activeModelSettings.temperature(), activeModelSettings.topK(), activeModelSettings.topP(), value,
                activeModelSettings.presencePenalty(), activeModelSettings.frequencyPenalty(),
                activeModelSettings.repetitionPenalty());
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updatePresencePenalty(double value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = new ModelSettings(activeModelSettings.modelName(), activeModelSettings.active(),
                activeModelSettings.temperature(), activeModelSettings.topK(), activeModelSettings.topP(),
                activeModelSettings.minP(), value, activeModelSettings.frequencyPenalty(),
                activeModelSettings.repetitionPenalty());
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateFrequencyPenalty(double value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = new ModelSettings(activeModelSettings.modelName(), activeModelSettings.active(),
                activeModelSettings.temperature(), activeModelSettings.topK(), activeModelSettings.topP(),
                activeModelSettings.minP(), activeModelSettings.presencePenalty(), value,
                activeModelSettings.repetitionPenalty());
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateRepetitionPenalty(double value)
    {
        if (updatingModelControls)
        {
            return;
        }
        activeModelSettings = new ModelSettings(activeModelSettings.modelName(), activeModelSettings.active(),
                activeModelSettings.temperature(), activeModelSettings.topK(), activeModelSettings.topP(),
                activeModelSettings.minP(), activeModelSettings.presencePenalty(), activeModelSettings.frequencyPenalty(),
                value);
        persistModelSettings();
        refreshGenerationSettings();
    }

    private void updateMinStoryPercent(int percent)
    {
        int capped = Math.max(10, Math.min(100, percent));
        int minWindow = (int) Math.round(appSettings.contextLimit() * (capped / 100.0));
        appSettings = new AppSettings(appSettings.ollamaUrl(), appSettings.selectedModel(), appSettings.contextLimit(),
                appSettings.responseLength(), minWindow, appSettings.storyCardLookback(), appSettings.anPlacement());
        persistAppSettings();
        refreshGenerationSettings();
    }

    private void updateStoryCardLookback(int value)
    {
        appSettings = new AppSettings(appSettings.ollamaUrl(), appSettings.selectedModel(), appSettings.contextLimit(),
                appSettings.responseLength(), appSettings.minStoryWindow(), value, appSettings.anPlacement());
        persistAppSettings();
        refreshGenerationSettings();
    }

    private void updateAnPlacement(int value)
    {
        appSettings = new AppSettings(appSettings.ollamaUrl(), appSettings.selectedModel(), appSettings.contextLimit(),
                appSettings.responseLength(), appSettings.minStoryWindow(), appSettings.storyCardLookback(), value);
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
        storyContent.getChildren().clear();
        if (blocks.isEmpty())
        {
            if (forceScroll)
            {
                forceScrollPending = true;
                scrollToBottom();
            }
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
            storyContent.getChildren().add(buildUserBlockNode(block));
        }

        addAssistantGroup(assistantGroup, latestAssistantId);

        if (forceScroll)
        {
            forceScrollPending = true;
            scrollToBottom();
        }
    }

    private void addAssistantGroup(List<Block> group, String latestAssistantId)
    {
        if (group.isEmpty())
        {
            return;
        }
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

            if (i < group.size() - 1 && !endsWithWhitespace(block.text()) && !startsWithPunctuation(group.get(i + 1).text()))
            {
                flow.getChildren().add(new Text(" "));
            }
        }
        Text sentinel = new Text("");
        sentinel.setUserData("sentinel");
        flow.getChildren().add(sentinel);
        storyContent.getChildren().add(flow);
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
        String normalized = newText == null ? "" : newText;
        try
        {
            updateBlockText(blockId, normalized);
        }
        catch (SQLException e)
        {
            showError("Failed to update block", e);
        }

        Block updatedBlock = null;
        for (Block block : blocks)
        {
            if (block.id().equals(blockId))
            {
                updatedBlock = block;
                break;
            }
        }
        if (updatedBlock == null)
        {
            return;
        }

        TextFlow flow = activeAssistantFlow;
        Text updatedText = new Text(normalized);
        updatedText.setFill(javafx.scene.paint.Color.web("#e6e1d8"));
        String latestId = findLatestAssistantId();
        boolean highlight = blockId.equals(latestId);
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
        Block finalUpdatedBlock = updatedBlock;
        updatedText.setOnMouseClicked(event -> beginAssistantInlineEdit(finalUpdatedBlock, flow, updatedText));

        int index = flow.getChildren().indexOf(activeAssistantEditor);
        if (index >= 0)
        {
            flow.getChildren().set(index, updatedText);
            flow.requestLayout();
        }
        activeAssistantEditId = null;
        activeAssistantEditor = null;
        activeAssistantFlow = null;
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

        label.setOnMouseClicked(event -> beginInlineEdit(label, editor));
        label.setOnMouseEntered(event -> label.setUnderline(true));
        label.setOnMouseExited(event -> label.setUnderline(false));
        editor.focusedProperty().addListener((obs, oldValue, newValue) ->
        {
            if (!newValue)
            {
                String newText = editor.getText();
                commitInlineEdit(block.id(), newText, label, editor, false);
            }
        });

        StackPane textStack = new StackPane(label, editor);
        textStack.prefWidthProperty().bind(contentWidthBinding().subtract(24));
        textStack.maxWidthProperty().bind(contentWidthBinding().subtract(24));
        HBox row = new HBox(6, icon, textStack);
        HBox.setHgrow(textStack, Priority.ALWAYS);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-border-color: rgba(255,255,255,0.25); -fx-border-width: 0 0 0 2;");
        row.prefWidthProperty().bind(contentWidthBinding());
        row.maxWidthProperty().bind(contentWidthBinding());
        return row;
    }

    private void beginInlineEdit(Label label, TextArea editor)
    {
        String current = stripTrailingSpace(label.getText());
        editor.setText(current);
        label.setVisible(false);
        label.setManaged(false);
        editor.setVisible(true);
        editor.setManaged(true);
        editor.requestFocus();
        editor.positionCaret(editor.getText().length());
    }

    private void commitInlineEdit(String blockId, String newText, Label label, TextArea editor, boolean trailingSpace)
    {
        String normalized = newText == null ? "" : newText;
        try
        {
            updateBlockText(blockId, normalized);
            label.setText(trailingSpace ? normalized + " " : normalized);
        }
        catch (SQLException e)
        {
            showError("Failed to update block", e);
        }
        label.setVisible(true);
        label.setManaged(true);
        editor.setVisible(false);
        editor.setManaged(false);
    }

    private void updateBlockText(String blockId, String text) throws SQLException
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
                return;
            }
            blockRepository.updateText(blockId, text);
            Block updated = new Block(block.id(), block.storyId(), block.role(), text, block.createdAt(), block.position());
            blocks.set(i, updated);
            return;
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

    private static boolean endsWithWhitespace(String text)
    {
        if (text == null || text.isEmpty())
        {
            return true;
        }
        char last = text.charAt(text.length() - 1);
        return Character.isWhitespace(last);
    }

    private static boolean startsWithPunctuation(String text)
    {
        if (text == null || text.isEmpty())
        {
            return false;
        }
        char first = text.charAt(0);
        return ",.;:!?)]}\"'".indexOf(first) >= 0;
    }

    private void scrollToBottom()
    {
        javafx.application.Platform.runLater(() ->
        {
            storyScroll.applyCss();
            storyScroll.layout();
            storyScroll.setVvalue(1.0);
        });
    }

    private DoubleBinding contentWidthBinding()
    {
        return storyScroll.widthProperty().subtract(32);
    }

    private static String normalizeOutput(String output)
    {
        if (output == null)
        {
            return "";
        }
        String normalized = output.replace("\r\n", "\n").trim();
        while (normalized.contains("\n\n\n"))
        {
            normalized = normalized.replace("\n\n\n", "\n\n");
        }
        return normalized;
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
