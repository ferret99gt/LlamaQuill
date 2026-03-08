package com.llamaquill.autocards;

import com.llamaquill.model.StoryCard;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class AutoCardsDialogs
{
    private AutoCardsDialogs()
    {
    }

    public static StoryCard showCreateDialog(Stage owner, String storyId, StoryCard draft)
    {
        if (draft == null || storyId == null || storyId.isBlank())
        {
            return null;
        }

        Dialog<StoryCard> dialog = new Dialog<>();
        dialog.setTitle("Auto Card Preview");
        dialog.setHeaderText("Create Story Card");
        dialog.initOwner(owner);

        TextField titleField = new TextField(draft.title());
        TextField triggersField = new TextField(draft.triggers());
        TextArea contentArea = new TextArea(draft.content());
        contentArea.setWrapText(true);
        contentArea.setPrefRowCount(8);
        CheckBox pinnedBox = new CheckBox("Pinned");
        pinnedBox.setSelected(draft.pinned());

        VBox content = new VBox(8,
                new Label("Title"), titleField,
                new Label("Triggers"), triggersField,
                new Label("Content"), contentArea,
                pinnedBox);
        content.setPadding(new Insets(10));

        ButtonType createType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, cancelType);
        dialog.getDialogPane().setContent(content);

        Node createButton = dialog.getDialogPane().lookupButton(createType);
        createButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            if (titleField.getText().trim().isEmpty())
            {
                showValidationInfo(owner, "Card title cannot be empty.");
                event.consume();
                return;
            }
            if (contentArea.getText().trim().isEmpty())
            {
                showValidationInfo(owner, "Card content cannot be empty.");
                event.consume();
            }
        });

        dialog.setResultConverter(buttonType ->
        {
            if (buttonType != createType)
            {
                return null;
            }
            return new StoryCard(
                    draft.id(),
                    storyId,
                    titleField.getText().trim(),
                    triggersField.getText().trim(),
                    contentArea.getText().trim(),
                    pinnedBox.isSelected());
        });

        return dialog.showAndWait().orElse(null);
    }

    public static String showUpdateDialog(Stage owner, StoryCard existing, String proposedContent, boolean summarized)
    {
        if (existing == null)
        {
            return null;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Auto Card Preview");
        dialog.setHeaderText(summarized ? "Summarize Story Card" : "Update Story Card");
        dialog.initOwner(owner);

        Label titleLabel = new Label(existing.title());
        titleLabel.setStyle("-fx-font-weight: bold;");

        TextArea oldArea = new TextArea(existing.content());
        oldArea.setEditable(false);
        oldArea.setWrapText(true);
        oldArea.setPrefRowCount(10);

        TextArea newArea = new TextArea(proposedContent);
        newArea.setWrapText(true);
        newArea.setPrefRowCount(10);

        VBox oldBox = new VBox(6, new Label("Existing"), oldArea);
        VBox newBox = new VBox(6, new Label("Proposed"), newArea);
        oldBox.setPrefWidth(300);
        newBox.setPrefWidth(300);
        HBox.setHgrow(oldBox, Priority.ALWAYS);
        HBox.setHgrow(newBox, Priority.ALWAYS);

        HBox panes = new HBox(10, oldBox, newBox);
        VBox content = new VBox(8, titleLabel, panes);
        content.setPadding(new Insets(10));

        ButtonType updateType = new ButtonType("Update", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(updateType, cancelType);
        dialog.getDialogPane().setContent(content);

        Node updateButton = dialog.getDialogPane().lookupButton(updateType);
        updateButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            if (newArea.getText().trim().isEmpty())
            {
                showValidationInfo(owner, "Updated content cannot be empty.");
                event.consume();
            }
        });

        dialog.setResultConverter(buttonType -> buttonType == updateType ? newArea.getText().trim() : null);
        return dialog.showAndWait().orElse(null);
    }

    private static void showValidationInfo(Stage owner, String message)
    {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("LlamaQuill");
        dialog.setHeaderText(message);
        dialog.initOwner(owner);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.showAndWait();
    }
}
