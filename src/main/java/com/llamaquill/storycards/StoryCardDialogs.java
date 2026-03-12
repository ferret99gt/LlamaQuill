package com.llamaquill.storycards;

import com.llamaquill.autocards.AutoCards;
import com.llamaquill.model.StoryCard;
import com.llamaquill.util.Ids;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class StoryCardDialogs
{
    private StoryCardDialogs()
    {
    }

    @FunctionalInterface
    public interface CardSaver
    {
        void save(StoryCard card) throws Exception;
    }

    @FunctionalInterface
    public interface CardDeleter
    {
        boolean delete() throws Exception;
    }

    @FunctionalInterface
    public interface DraftGenerator
    {
        void generate(String request, Consumer<AutoCards.GeneratedCard> onSuccess, Consumer<Throwable> onFailure);
    }

    public static void showCardDialog(Stage owner, String storyId, StoryCard card, Consumer<String> showInfo,
            BiConsumer<String, Throwable> showError, CardSaver saver, CardDeleter deleter)
    {
        boolean isNew = card == null;
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "New Story Card" : "Edit Story Card");
        dialog.setHeaderText(isNew ? "Create a story card" : "Edit story card");
        dialog.initOwner(owner);

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
                showInfo.accept("Card title cannot be empty.");
                return;
            }

            StoryCard updatedCard = new StoryCard(
                    isNew ? Ids.newId() : card.id(),
                    storyId,
                    title,
                    triggersField.getText().trim(),
                    contentArea.getText().trim(),
                    pinnedBox.isSelected());
            try
            {
                saver.save(updatedCard);
                dialog.close();
            }
            catch (Exception e)
            {
                showError.accept(isNew ? "Failed to create story card" : "Failed to update story card", e);
            }
        });

        if (!isNew)
        {
            Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteType);
            deleteButton.addEventFilter(ActionEvent.ACTION, event ->
            {
                event.consume();
                try
                {
                    if (deleter.delete())
                    {
                        dialog.close();
                    }
                }
                catch (Exception e)
                {
                    showError.accept("Failed to delete story card", e);
                }
            });
        }

        dialog.showAndWait();
    }

    public static void showGenerateDialog(Stage owner, String storyId, Consumer<String> showInfo,
            BiConsumer<String, Throwable> showError, Consumer<String> setStatus, DraftGenerator draftGenerator,
            CardSaver saver)
    {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Generate Story Card");
        dialog.setHeaderText("Generate a new card from a prompt");
        dialog.initOwner(owner);

        TextField titleField = new TextField();
        TextArea contentArea = new TextArea();
        contentArea.setWrapText(true);
        contentArea.setPrefRowCount(6);
        TextField triggersField = new TextField();
        CheckBox pinnedBox = new CheckBox("Pinned");
        TextArea promptArea = new TextArea();
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(4);
        promptArea.setPromptText("Example: Generate a new character who is a wandering mercenary.");

        VBox content = new VBox(8,
                new Label("Prompt"), promptArea,
                new Label("Title"), titleField,
                new Label("Content"), contentArea,
                new Label("Triggers (comma separated)"), triggersField,
                pinnedBox);
        content.setPadding(new Insets(10));

        Button generateButton = new Button("Generate");
        Button createButton = new Button("Create");
        Button createAndCloseButton = new Button("Create and Close");
        Button cancelButton = new Button("Cancel");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox rightActions = new HBox(8, createButton, createAndCloseButton, cancelButton);
        rightActions.setAlignment(Pos.CENTER_RIGHT);
        HBox actions = new HBox(8, generateButton, spacer, rightActions);
        actions.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().add(actions);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        Node cancelNode = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelNode != null)
        {
            cancelNode.setVisible(false);
            cancelNode.setManaged(false);
        }

        Runnable restoreButtons = () ->
        {
            generateButton.setDisable(false);
            createButton.setDisable(false);
            createAndCloseButton.setDisable(false);
        };

        Runnable saveAndKeepOpen = () ->
        {
            String title = titleField.getText().trim();
            if (title.isEmpty())
            {
                showInfo.accept("Card title cannot be empty.");
                return;
            }
            String contentText = contentArea.getText().trim();
            if (contentText.isEmpty())
            {
                showInfo.accept("Card content cannot be empty.");
                return;
            }
            StoryCard newCard = new StoryCard(
                    Ids.newId(),
                    storyId,
                    title,
                    triggersField.getText().trim(),
                    contentText,
                    pinnedBox.isSelected());
            try
            {
                saver.save(newCard);
                titleField.clear();
                triggersField.clear();
                contentArea.clear();
            }
            catch (Exception e)
            {
                showError.accept("Failed to create story card", e);
            }
        };

        createButton.setOnAction(event -> saveAndKeepOpen.run());
        createAndCloseButton.setOnAction(event ->
        {
            String beforeTitle = titleField.getText();
            saveAndKeepOpen.run();
            if (!beforeTitle.equals(titleField.getText()))
            {
                dialog.close();
            }
        });
        cancelButton.setOnAction(event -> dialog.close());

        generateButton.setOnAction(event ->
        {
            String request = promptArea.getText().trim();
            if (request.isEmpty())
            {
                showInfo.accept("Prompt cannot be empty.");
                return;
            }

            generateButton.setDisable(true);
            createButton.setDisable(true);
            createAndCloseButton.setDisable(true);
            setStatus.accept("Generating story card...");
            draftGenerator.generate(request,
                    generated ->
                    {
                        restoreButtons.run();
                        if (generated == null)
                        {
                            setStatus.accept("Generate card: no result");
                            return;
                        }
                        titleField.setText(generated.title());
                        triggersField.setText(generated.triggers());
                        contentArea.setText(generated.content());
                        setStatus.accept("Generated story card draft");
                    },
                    error ->
                    {
                        restoreButtons.run();
                        setStatus.accept("Generate card failed");
                        showError.accept("Failed to generate story card", error);
                    });
        });

        dialog.showAndWait();
    }
}
