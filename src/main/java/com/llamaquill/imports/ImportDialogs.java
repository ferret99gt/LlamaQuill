package com.llamaquill.imports;

import com.llamaquill.model.Story;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ImportDialogs
{
    private ImportDialogs()
    {
    }

    @FunctionalInterface
    public interface AdventureImporter
    {
        Story importAdventure(Path path) throws Exception;
    }

    @FunctionalInterface
    public interface StoryCardsImporter
    {
        int importStoryCards(Path path, boolean replaceExisting) throws Exception;
    }

    public static void showAdventureImportDialog(Stage owner, Consumer<String> showInfo,
            BiConsumer<String, Throwable> showError, AdventureImporter importer, Consumer<Story> onImported)
    {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Import Adventure");
        dialog.setHeaderText("Import AI Dungeon adventure backup");
        dialog.initOwner(owner);

        TextField fileField = new TextField();
        fileField.setEditable(false);
        fileField.setPromptText("Select a ZIP file");

        Button browseButton = new Button("Choose File");
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP Files", "*.zip"));
        browseButton.setOnAction(event ->
        {
            java.io.File selected = chooser.showOpenDialog(owner);
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
                importButton.setDisable(newValue == null || newValue.isBlank()));

        importButton.setOnAction(event ->
        {
            try
            {
                Story imported = importer.importAdventure(Path.of(fileField.getText()));
                dialog.close();
                onImported.accept(imported);
                showInfo.accept("Imported adventure \"" + imported.title() + "\".");
            }
            catch (Exception e)
            {
                showError.accept("Failed to import adventure", e);
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

    public static void showStoryCardsImportDialog(Stage owner, Consumer<String> showInfo,
            BiConsumer<String, Throwable> showError, StoryCardsImporter importer, Consumer<Integer> onImported)
    {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Import Story Cards");
        dialog.setHeaderText("Import AI Dungeon story cards");
        dialog.initOwner(owner);

        TextField fileField = new TextField();
        fileField.setEditable(false);
        fileField.setPromptText("Select a JSON file");

        Button browseButton = new Button("Choose File");
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        browseButton.setOnAction(event ->
        {
            java.io.File selected = chooser.showOpenDialog(owner);
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
                importButton.setDisable(newValue == null || newValue.isBlank()));

        importButton.setOnAction(event ->
        {
            try
            {
                int imported = importer.importStoryCards(Path.of(fileField.getText()), replaceBox.isSelected());
                dialog.close();
                onImported.accept(imported);
                showInfo.accept("Imported " + imported + " cards.");
            }
            catch (Exception e)
            {
                showError.accept("Failed to import cards", e);
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
}
