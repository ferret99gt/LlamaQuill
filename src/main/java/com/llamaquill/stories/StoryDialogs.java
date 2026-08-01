package com.llamaquill.stories;

import com.llamaquill.model.Story;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
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
            Consumer<String> onUpdate, Runnable onClone, Supplier<Boolean> confirmDelete, Runnable onDelete)
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
        ButtonType cloneType = new ButtonType("Clone", ButtonBar.ButtonData.LEFT);
        ButtonType deleteType = new ButtonType("Delete", ButtonBar.ButtonData.LEFT);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(playType, updateType, cloneType, deleteType, cancelType);
        dialog.getDialogPane().setContent(content);
        boolean[] cloneRequested = { false };

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

        Button cloneButton = (Button) dialog.getDialogPane().lookupButton(cloneType);
        cloneButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            cloneRequested[0] = true;
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
        if (cloneRequested[0])
        {
            onClone.run();
        }
    }

    public static void showCloneStoryDialog(Stage owner, Story story, Consumer<String> showInfo,
            Consumer<StoryCloneRequest> onClone)
    {
        StoryCloneRequest defaults = StoryCloneRequest.defaultsFor(story);
        Dialog<StoryCloneRequest> dialog = new Dialog<>();
        dialog.setTitle("Clone Story");
        dialog.setHeaderText("Choose what to copy into the new story");
        dialog.initOwner(owner);

        TextField nameField = new TextField(defaults.newName());
        nameField.setPrefWidth(360);
        CheckBox details = new CheckBox("Story Details (AI Instructions, Plot Essentials, Author's Note)");
        CheckBox cards = new CheckBox("Story Cards");
        CheckBox initialBlock = new CheckBox("Initial story block");
        CheckBox allBlocks = new CheckBox("All story blocks");
        details.setSelected(defaults.includeStoryDetails());
        cards.setSelected(defaults.includeStoryCards());
        initialBlock.setSelected(defaults.includeInitialBlock());
        allBlocks.setSelected(defaults.includeAllBlocks());
        initialBlock.disableProperty().bind(allBlocks.selectedProperty());

        Label allBlocksHint = new Label("All story blocks includes the initial story block.");
        allBlocksHint.setWrapText(true);

        VBox content = new VBox(10,
                new Label("New Name"), nameField,
                details, cards, initialBlock, allBlocks, allBlocksHint);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        ButtonType cloneType = new ButtonType("Clone", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cloneType, cancelType);
        dialog.setResultConverter(button -> button == cloneType
                ? new StoryCloneRequest(nameField.getText(), details.isSelected(), cards.isSelected(),
                        initialBlock.isSelected(), allBlocks.isSelected())
                : null);

        Button cloneButton = (Button) dialog.getDialogPane().lookupButton(cloneType);
        cloneButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            if (nameField.getText().trim().isEmpty())
            {
                event.consume();
                showInfo.accept("Story name cannot be empty.");
            }
        });

        dialog.showAndWait().ifPresent(onClone);
    }
}
