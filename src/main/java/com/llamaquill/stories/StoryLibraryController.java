package com.llamaquill.stories;

import com.llamaquill.model.Story;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class StoryLibraryController
{
    private final int expandedWidth;
    private final ObservableList<Story> stories = FXCollections.observableArrayList();
    private final ListView<Story> storyList = new ListView<>(stories);
    private final Label header = new Label("Stories");
    private final Button newStoryButton = new Button("New Story");
    private final Button importButton = new Button("Import AI Dungeon Adventure");
    private final Button collapseButton = new Button("<<");
    private final VBox root;

    public StoryLibraryController(int expandedWidth, Runnable onNewStory, Runnable onImport,
            Consumer<Story> onOpenStory)
    {
        if (expandedWidth < 200)
        {
            throw new IllegalArgumentException("expandedWidth must be at least 200.");
        }
        this.expandedWidth = expandedWidth;
        Objects.requireNonNull(onNewStory, "onNewStory");
        Objects.requireNonNull(onImport, "onImport");
        Objects.requireNonNull(onOpenStory, "onOpenStory");

        header.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        newStoryButton.setMaxWidth(Double.MAX_VALUE);
        newStoryButton.setOnAction(event -> onNewStory.run());
        importButton.setMaxWidth(Double.MAX_VALUE);
        importButton.setOnAction(event -> onImport.run());

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
                    onOpenStory.accept(selected);
                }
            }
        });

        collapseButton.setOnAction(event -> toggle());
        HBox headerRow = new HBox(8, header, collapseButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(header, Priority.ALWAYS);

        root = new VBox(8, headerRow, newStoryButton, storyList, importButton);
        root.getStyleClass().add("sidebar");
        root.setPadding(new Insets(10));
        root.setPrefWidth(expandedWidth);
        root.setMinWidth(200);
        VBox.setVgrow(storyList, Priority.ALWAYS);
    }

    public VBox root()
    {
        return root;
    }

    public void setStories(List<Story> updatedStories, String selectedId)
    {
        stories.setAll(updatedStories == null ? List.of() : updatedStories);
        select(selectedId);
    }

    public void select(String storyId)
    {
        storyList.getSelectionModel().clearSelection();
        if (storyId == null)
        {
            return;
        }
        for (Story story : stories)
        {
            if (story.id().equals(storyId))
            {
                storyList.getSelectionModel().select(story);
                return;
            }
        }
    }

    public void toggle()
    {
        boolean collapsing = storyList.isVisible();
        setExpanded(!collapsing);
    }

    private void setExpanded(boolean expanded)
    {
        storyList.setVisible(expanded);
        storyList.setManaged(expanded);
        newStoryButton.setVisible(expanded);
        newStoryButton.setManaged(expanded);
        header.setVisible(expanded);
        header.setManaged(expanded);
        importButton.setVisible(expanded);
        importButton.setManaged(expanded);
        root.setPrefWidth(expanded ? expandedWidth : 48);
        root.setMinWidth(expanded ? 200 : 48);
        collapseButton.setText(expanded ? "<<" : ">>");
    }
}
