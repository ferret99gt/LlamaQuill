package com.llamaquill.stories;

import com.llamaquill.model.Story;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class StoryDialogs
{
    private StoryDialogs()
    {
    }

    public static void showNewStoryDialog(Stage owner, Consumer<String> showInfo, Consumer<String> onCreate)
    {
        TextInputDialog dialog = new TextInputDialog("New Story");
        dialog.setTitle("New Story");
        dialog.setHeaderText("Enter a story name");
        dialog.initOwner(owner);
        dialog.showAndWait().ifPresent(name ->
        {
            String trimmed = name.trim();
            if (trimmed.isEmpty())
            {
                showInfo.accept("Story name cannot be empty.");
                return;
            }
            onCreate.accept(trimmed);
        });
    }

    public static void showStoryDialog(Stage owner, Story story, Consumer<String> showInfo, Consumer<String> onPlay,
            Consumer<String> onUpdate, Supplier<Boolean> confirmDelete, Runnable onDelete)
    {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Story");
        dialog.setHeaderText("Story settings");
        dialog.initOwner(owner);

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
                showInfo.accept("Story name cannot be empty.");
                return;
            }
            onPlay.accept(name);
            dialog.close();
        });

        Button updateButton = (Button) dialog.getDialogPane().lookupButton(updateType);
        updateButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            String name = titleField.getText().trim();
            if (name.isEmpty())
            {
                showInfo.accept("Story name cannot be empty.");
                return;
            }
            onUpdate.accept(name);
            dialog.close();
        });

        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteType);
        deleteButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            if (confirmDelete.get())
            {
                onDelete.run();
                dialog.close();
            }
        });

        dialog.showAndWait();
    }
}
