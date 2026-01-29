package com.llamaquill;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.db.SettingsRepository;
import com.llamaquill.model.Block;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.ollama.OllamaClient;
import com.llamaquill.prompt.PromptCompilation;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.util.Ids;
import com.llamaquill.util.Timestamps;
import javafx.application.Application;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application
{
    private static final String DEFAULT_SYSTEM_PROMPT = "You're a masterful storyteller and gamemaster. "
            + "Write in second person present tense (You are), crafting vivid, engaging narratives with authority and confidence.";

    private static final int MAX_STORY_LINES = 20;
    private static final int SIDEBAR_WIDTH = 240;
    private static final int RIGHT_SIDEBAR_WIDTH = 320;

    private Connection connection;
    private StoryRepository storyRepository;
    private BlockRepository blockRepository;
    private StoryCardRepository cardRepository;
    private SettingsRepository settingsRepository;
    private PromptCompiler promptCompiler;
    private OllamaClient ollamaClient;
    private GenerationSettings settings;
    private ExecutorService executor;

    private Story activeStory;
    private List<Block> blocks = new ArrayList<>();
    private List<StoryCard> cards = new ArrayList<>();

    private final ObservableList<Story> storyItems = FXCollections.observableArrayList();
    private final ObservableList<StoryCard> cardItems = FXCollections.observableArrayList();

    private Stage primaryStage;
    private TextArea storyArea;
    private Label statusLabel;
    private Button continueButton;

    private Button newStoryButton;
    private Button collapseLeftButton;
    private ListView<Story> storyList;
    private VBox storySidebar;
    private Label storyHeader;

    private Button collapseRightButton;
    private VBox rightSidebar;
    private TabPane rightTabs;

    private TextArea systemPromptArea;
    private TextArea plotEssentialsArea;
    private TextArea authorNoteArea;

    private Button newCardButton;
    private ListView<StoryCard> cardList;

    private Slider contextLimitSlider;
    private Slider responseLengthSlider;
    private Slider temperatureSlider;
    private Slider topKSlider;
    private Slider topPSlider;
    private Slider presencePenaltySlider;
    private Slider frequencyPenaltySlider;
    private Slider minStoryPercentSlider;
    private Spinner<Integer> storyCardLookbackSpinner;
    private Spinner<Integer> anPlacementSpinner;

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
            settingsRepository = new SettingsRepository(connection);
            promptCompiler = new PromptCompiler();
            ollamaClient = new OllamaClient();
            settings = loadOrCreateSettings();
            executor = Executors.newSingleThreadExecutor();

            activeStory = loadOrCreateStory();
            blocks = blockRepository.listForStory(activeStory.id());
            cards = cardRepository.listForStory(activeStory.id());
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("Failed to initialize database", e);
        }

        storyArea = new TextArea(renderBlocks(blocks));
        storyArea.setWrapText(true);
        storyArea.setEditable(false);

        continueButton = new Button("Continue");
        continueButton.setOnAction(event -> runContinue());

        statusLabel = new Label("Ready");

        var controls = new HBox(12, continueButton, statusLabel);
        controls.setPadding(new Insets(10));

        var root = new BorderPane();
        root.setLeft(buildStorySidebar());
        root.setCenter(storyArea);
        root.setRight(buildRightSidebar());
        root.setBottom(controls);

        refreshStoryList(activeStory.id());
        refreshCardList(activeStory.id());
        populateStoryDetails(activeStory);
        setStoryDependentControlsEnabled(activeStory != null);

        var scene = new Scene(root, 1280, 720);
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
        storyList.setOnMouseClicked(event -> {
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

        storySidebar = new VBox(8, headerRow, newStoryButton, storyList);
        storySidebar.setPadding(new Insets(10));
        storySidebar.setPrefWidth(SIDEBAR_WIDTH);
        storySidebar.setMinWidth(200);

        VBox.setVgrow(storyList, Priority.ALWAYS);
        return storySidebar;
    }

    private VBox buildRightSidebar()
    {
        collapseRightButton = new Button(">>");
        collapseRightButton.setOnAction(event -> toggleRightSidebar());

        rightTabs = new TabPane();
        rightTabs.getTabs().addAll(buildStoryTab(), buildStoryCardsTab(), buildOptionsTab());
        rightTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Label rightHeader = new Label("Details");
        rightHeader.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");


        var headerRow = new HBox(8, collapseRightButton, rightHeader);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(rightHeader, Priority.ALWAYS);

        rightSidebar = new VBox(8, headerRow, rightTabs);
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

        VBox content = new VBox(10,
                new Label("System Prompt"),
                systemPromptArea,
                new Label("Plot Essentials"),
                plotEssentialsArea,
                new Label("Author's Note"),
                authorNoteArea);
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
        cardList.setOnMouseClicked(event -> {
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

        contextLimitSlider = buildIntSlider(1024, 32768, settings.contextLimit(), 1024);
        responseLengthSlider = buildIntSlider(1, 250, settings.responseLength(), 1);
        temperatureSlider = buildDoubleSlider(0.1, 2.0, settings.temperature(), 0.1);
        topKSlider = buildIntSlider(1, 999, settings.topK(), 1);
        topPSlider = buildDoubleSlider(0.1, 1.0, settings.topP(), 0.01);
        presencePenaltySlider = buildDoubleSlider(-2.0, 2.0, settings.presencePenalty(), 0.1);
        frequencyPenaltySlider = buildDoubleSlider(-2.0, 2.0, settings.frequencyPenalty(), 0.1);
        minStoryPercentSlider = buildIntSlider(10, 100, percentFromSettings(), 1);
        storyCardLookbackSpinner = buildSpinner(0, 20, settings.storyCardLookback());
        anPlacementSpinner = buildSpinner(1, 10, settings.anPlacement());

        content.getChildren().addAll(
                sliderRow("Context Limit", contextLimitSlider,
                        valueLabel(settings.contextLimit(), "tokens"), value -> updateContextLimit(value.intValue())),
                sliderRow("Response Length", responseLengthSlider,
                        valueLabel(settings.responseLength(), "tokens"), value -> updateResponseLength(value.intValue())),
                sliderRow("Temperature", temperatureSlider,
                        valueLabel(settings.temperature(), ""), value -> updateTemperature(roundTo(value.doubleValue(), 0.1))),
                sliderRow("Top K", topKSlider,
                        valueLabel(settings.topK(), ""), value -> updateTopK(value.intValue())),
                sliderRow("Top P", topPSlider,
                        valueLabel(settings.topP(), ""), value -> updateTopP(roundTo(value.doubleValue(), 0.01))),
                sliderRow("Presence Penalty", presencePenaltySlider,
                        valueLabel(settings.presencePenalty(), ""), value -> updatePresencePenalty(roundTo(value.doubleValue(), 0.1))),
                sliderRow("Frequency Penalty", frequencyPenaltySlider,
                        valueLabel(settings.frequencyPenalty(), ""), value -> updateFrequencyPenalty(roundTo(value.doubleValue(), 0.1))),
                sliderRow("Context to Use for Story", minStoryPercentSlider,
                        valueLabel(percentFromSettings(), "%"), value -> updateMinStoryPercent(value.intValue())),
                spinnerRow("Story Card Look Back", storyCardLookbackSpinner, this::updateStoryCardLookback),
                spinnerRow("Author's Note Insertion Point", anPlacementSpinner, this::updateAnPlacement)
        );

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        return new Tab("Options", scrollPane);
    }

    private TextArea buildStoryArea()
    {
        TextArea area = new TextArea();
        area.setWrapText(true);
        area.setPrefRowCount(6);
        area.setMinHeight(Region.USE_PREF_SIZE);
        area.setMaxHeight(MAX_STORY_LINES * 18.0);
        return area;
    }

    private void attachSaveOnBlur(TextArea area)
    {
        area.focusedProperty().addListener((obs, oldValue, newValue) -> {
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
            storySidebar.setPrefWidth(48);
            storySidebar.setMinWidth(48);
            collapseLeftButton.setText(">>");
        }
        else
        {
            storySidebar.setPrefWidth(SIDEBAR_WIDTH);
            storySidebar.setMinWidth(200);
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

    private GenerationSettings loadOrCreateSettings() throws SQLException
    {
        return settingsRepository.load().orElseGet(() -> {
            GenerationSettings defaults = GenerationSettings.defaults();
            try
            {
                settingsRepository.save(defaults);
            }
            catch (SQLException e)
            {
                showError("Failed to save default settings", e);
            }
            return defaults;
        });
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
        dialog.showAndWait().ifPresent(name -> {
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
        playButton.addEventFilter(ActionEvent.ACTION, event -> {
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
        updateButton.addEventFilter(ActionEvent.ACTION, event -> {
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
        deleteButton.addEventFilter(ActionEvent.ACTION, event -> {
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

        VBox content = new VBox(8,
                new Label("Title"), titleField,
                new Label("Content"), contentArea,
                new Label("Triggers (comma separated)"), triggersField,
                pinnedBox);
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
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
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
            deleteButton.addEventFilter(ActionEvent.ACTION, event -> {
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

        if (systemPrompt.equals(activeStory.systemPrompt())
                && plotEssentials.equals(activeStory.plotEssentials())
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
                cards = new ArrayList<>();
                storyArea.setText("");
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

    private void playStory(Story story)
    {
        String now = Timestamps.now();
        Story updated = new Story(story.id(), story.title(), story.systemPrompt(), story.plotEssentials(),
                story.authorNote(), story.createdAt(), now);
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
            cards = cardRepository.listForStory(story.id());
            storyArea.setText(renderBlocks(blocks));
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
        systemPromptArea.setDisable(!enabled);
        plotEssentialsArea.setDisable(!enabled);
        authorNoteArea.setDisable(!enabled);
        newCardButton.setDisable(!enabled);
        cardList.setDisable(!enabled);
    }

    private void runContinue()
    {
        if (activeStory == null)
        {
            showInfo("Select a story first.");
            return;
        }

        continueButton.setDisable(true);
        statusLabel.setText("Generating...");

        Task<Block> task = new Task<>()
        {
            @Override
            protected Block call() throws Exception
            {
                List<Block> currentBlocks = blockRepository.listForStory(activeStory.id());
                List<StoryCard> currentCards = cardRepository.listForStory(activeStory.id());

                PromptCompilation compilation = promptCompiler.compile(activeStory, currentBlocks, currentCards,
                        settings);
                String response = ollamaClient.generate(compilation.prompt(), settings);
                String cleaned = normalizeOutput(response);

                int position = blockRepository.nextPosition(activeStory.id());
                Block block = new Block(Ids.newId(), activeStory.id(), Role.ASSISTANT, cleaned, Timestamps.now(),
                        position);
                blockRepository.insert(block);

                String now = Timestamps.now();
                activeStory = new Story(activeStory.id(), activeStory.title(), activeStory.systemPrompt(),
                        activeStory.plotEssentials(), activeStory.authorNote(), activeStory.createdAt(), now);
                storyRepository.update(activeStory);
                return block;
            }
        };

        task.setOnSucceeded(event -> {
            Block block = task.getValue();
            blocks.add(block);
            storyArea.setText(renderBlocks(blocks));
            continueButton.setDisable(false);
            statusLabel.setText("Ready");
            refreshStoryList(activeStory.id());
        });

        task.setOnFailed(event -> {
            Throwable error = task.getException();
            continueButton.setDisable(false);
            statusLabel.setText("Error: " + (error == null ? "Unknown" : error.getMessage()));
        });

        executor.submit(task);
    }
    private Slider buildIntSlider(int min, int max, int value, int step)
    {
        Slider slider = new Slider(min, max, value);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setBlockIncrement(step);
        slider.setSnapToTicks(true);
        slider.setMajorTickUnit(step * 4.0);
        slider.setMinorTickCount(3);
        slider.setOrientation(Orientation.HORIZONTAL);
        return slider;
    }

    private Slider buildDoubleSlider(double min, double max, double value, double step)
    {
        Slider slider = new Slider(min, max, value);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setBlockIncrement(step);
        slider.setSnapToTicks(true);
        slider.setMajorTickUnit(step * 4.0);
        slider.setMinorTickCount(3);
        slider.setOrientation(Orientation.HORIZONTAL);
        return slider;
    }

    private VBox sliderRow(String labelText, Slider slider, Label valueLabel, java.util.function.Consumer<Number> handler)
    {
        Label label = new Label(labelText);
        VBox box = new VBox(6, label, slider, valueLabel);
        slider.valueProperty().addListener((obs, oldValue, newValue) -> {
            valueLabel.setText(formatValue(newValue, valueLabel));
            handler.accept(newValue);
        });
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

    private String formatValue(Number value, Label label)
    {
        String text = label.getText();
        String suffix = "";
        int space = text.lastIndexOf(' ');
        if (space > -1)
        {
            suffix = text.substring(space + 1);
        }
        return formatValue(value.doubleValue(), suffix.equals("") ? "" : suffix);
    }

    private String formatValue(double value, String suffix)
    {
        String formatted;
        if (Math.abs(value - Math.round(value)) < 0.0001)
        {
            formatted = String.valueOf((int) Math.round(value));
        }
        else
        {
            formatted = String.format(java.util.Locale.US, "%.2f", value);
        }
        if (!suffix.isEmpty())
        {
            return formatted + " " + suffix;
        }
        return formatted;
    }

    private int percentFromSettings()
    {
        if (settings.contextLimit() == 0)
        {
            return 0;
        }
        return (int) Math.round((settings.minStoryWindow() * 100.0) / settings.contextLimit());
    }

    private void updateContextLimit(int value)
    {
        int capped = Math.max(1024, Math.min(32768, value));
        int percent = minStoryPercentSlider == null ? 90 : (int) Math.round(minStoryPercentSlider.getValue());
        int minWindow = (int) Math.round(capped * (percent / 100.0));
        settings = new GenerationSettings(capped, settings.responseLength(), settings.temperature(), settings.topK(),
                settings.topP(), settings.presencePenalty(), settings.frequencyPenalty(), minWindow,
                settings.storyCardLookback(), settings.anPlacement());
        persistSettings();
    }

    private void updateResponseLength(int value)
    {
        int capped = Math.max(1, Math.min(250, value));
        settings = new GenerationSettings(settings.contextLimit(), capped, settings.temperature(), settings.topK(),
                settings.topP(), settings.presencePenalty(), settings.frequencyPenalty(), settings.minStoryWindow(),
                settings.storyCardLookback(), settings.anPlacement());
        persistSettings();
    }

    private void updateTemperature(double value)
    {
        settings = new GenerationSettings(settings.contextLimit(), settings.responseLength(), value, settings.topK(),
                settings.topP(), settings.presencePenalty(), settings.frequencyPenalty(), settings.minStoryWindow(),
                settings.storyCardLookback(), settings.anPlacement());
        persistSettings();
    }

    private void updateTopK(int value)
    {
        settings = new GenerationSettings(settings.contextLimit(), settings.responseLength(), settings.temperature(),
                value, settings.topP(), settings.presencePenalty(), settings.frequencyPenalty(),
                settings.minStoryWindow(), settings.storyCardLookback(), settings.anPlacement());
        persistSettings();
    }

    private void updateTopP(double value)
    {
        settings = new GenerationSettings(settings.contextLimit(), settings.responseLength(), settings.temperature(),
                settings.topK(), value, settings.presencePenalty(), settings.frequencyPenalty(),
                settings.minStoryWindow(), settings.storyCardLookback(), settings.anPlacement());
        persistSettings();
    }

    private void updatePresencePenalty(double value)
    {
        settings = new GenerationSettings(settings.contextLimit(), settings.responseLength(), settings.temperature(),
                settings.topK(), settings.topP(), value, settings.frequencyPenalty(), settings.minStoryWindow(),
                settings.storyCardLookback(), settings.anPlacement());
        persistSettings();
    }

    private void updateFrequencyPenalty(double value)
    {
        settings = new GenerationSettings(settings.contextLimit(), settings.responseLength(), settings.temperature(),
                settings.topK(), settings.topP(), settings.presencePenalty(), value, settings.minStoryWindow(),
                settings.storyCardLookback(), settings.anPlacement());
        persistSettings();
    }

    private void updateMinStoryPercent(int percent)
    {
        int capped = Math.max(10, Math.min(100, percent));
        int minWindow = (int) Math.round(settings.contextLimit() * (capped / 100.0));
        settings = new GenerationSettings(settings.contextLimit(), settings.responseLength(), settings.temperature(),
                settings.topK(), settings.topP(), settings.presencePenalty(), settings.frequencyPenalty(), minWindow,
                settings.storyCardLookback(), settings.anPlacement());
        persistSettings();
    }

    private void updateStoryCardLookback(int value)
    {
        settings = new GenerationSettings(settings.contextLimit(), settings.responseLength(), settings.temperature(),
                settings.topK(), settings.topP(), settings.presencePenalty(), settings.frequencyPenalty(),
                settings.minStoryWindow(), value, settings.anPlacement());
        persistSettings();
    }

    private void updateAnPlacement(int value)
    {
        settings = new GenerationSettings(settings.contextLimit(), settings.responseLength(), settings.temperature(),
                settings.topK(), settings.topP(), settings.presencePenalty(), settings.frequencyPenalty(),
                settings.minStoryWindow(), settings.storyCardLookback(), value);
        persistSettings();
    }

    private void persistSettings()
    {
        if (settingsRepository == null)
        {
            return;
        }
        try
        {
            settingsRepository.save(settings);
        }
        catch (SQLException e)
        {
            showError("Failed to save settings", e);
        }
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

    private static String renderBlocks(List<Block> blocks)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++)
        {
            if (i > 0)
            {
                sb.append("\n\n");
            }
            sb.append(blocks.get(i).text());
        }
        return sb.toString();
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
