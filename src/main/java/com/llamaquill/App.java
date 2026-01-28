package com.llamaquill;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
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
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

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

    private Connection connection;
    private StoryRepository storyRepository;
    private BlockRepository blockRepository;
    private StoryCardRepository cardRepository;
    private PromptCompiler promptCompiler;
    private OllamaClient ollamaClient;
    private GenerationSettings settings;
    private ExecutorService executor;

    private Story activeStory;
    private List<Block> blocks = new ArrayList<>();
    private List<StoryCard> cards = new ArrayList<>();

    private final ObservableList<Story> storyItems = FXCollections.observableArrayList();

    private Stage primaryStage;
    private TextArea storyArea;
    private Label statusLabel;
    private Button continueButton;
    private Button newStoryButton;
    private Button collapseButton;
    private ListView<Story> storyList;
    private VBox storySidebar;
    private Label storyHeader;

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
            promptCompiler = new PromptCompiler();
            ollamaClient = new OllamaClient();
            settings = GenerationSettings.defaults();
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
        root.setBottom(controls);

        refreshStoryList(activeStory.id());

        var scene = new Scene(root, 1100, 700);
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

        collapseButton = new Button("<<");
        collapseButton.setOnAction(event -> toggleSidebar());

        var headerRow = new HBox(8, storyHeader, collapseButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(storyHeader, Priority.ALWAYS);

        storySidebar = new VBox(8, headerRow, newStoryButton, storyList);
        storySidebar.setPadding(new Insets(10));
        storySidebar.setPrefWidth(240);
        storySidebar.setMinWidth(200);

        VBox.setVgrow(storyList, Priority.ALWAYS);
        return storySidebar;
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
            collapseButton.setText(">>");
        }
        else
        {
            storySidebar.setPrefWidth(240);
            storySidebar.setMinWidth(200);
            collapseButton.setText("<<");
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
            cards = cardRepository.listForStory(story.id());
            storyArea.setText(renderBlocks(blocks));
            statusLabel.setText("Ready");
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

                PromptCompilation compilation = promptCompiler.compile(activeStory, currentBlocks, currentCards, settings);
                String response = ollamaClient.generate(compilation.prompt(), settings);
                String cleaned = normalizeOutput(response);

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
            blocks.add(block);
            storyArea.setText(renderBlocks(blocks));
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
