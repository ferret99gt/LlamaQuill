package com.llamaquill.image;

import com.llamaquill.model.StoryImage;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.function.BooleanSupplier;

public final class StoryImageDialogs
{
    private StoryImageDialogs()
    {
    }

    public static void showImageBlockDialog(Stage owner, StoryImage storyImage, Image image, Runnable onSave,
            BooleanSupplier onDelete)
    {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Story Image");
        dialog.setHeaderText("Image block");
        dialog.initOwner(owner);
        dialog.setResizable(true);

        ImageView large = new ImageView(image);
        large.setPreserveRatio(true);
        large.setSmooth(true);
        large.setFitWidth(860);
        large.setFitHeight(520);

        TextArea promptArea = new TextArea(storyImage.prompt());
        promptArea.setWrapText(true);
        promptArea.setEditable(false);
        promptArea.setPrefRowCount(5);

        VBox content = new VBox(10, large, new Label("Prompt"), promptArea);
        content.setPadding(new Insets(10));
        content.setMaxWidth(980);

        ScrollPane contentScroll = new ScrollPane(content);
        contentScroll.setFitToWidth(true);
        contentScroll.setFitToHeight(false);
        contentScroll.setPannable(true);
        contentScroll.setPrefViewportWidth(980);
        contentScroll.setPrefViewportHeight(660);
        dialog.getDialogPane().setContent(contentScroll);
        dialog.getDialogPane().setPrefWidth(1040);
        dialog.getDialogPane().setPrefHeight(720);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.LEFT);
        ButtonType deleteType = new ButtonType("Delete", ButtonBar.ButtonData.LEFT);
        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, deleteType, closeType);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            onSave.run();
        });

        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteType);
        deleteButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            if (onDelete.getAsBoolean())
            {
                dialog.close();
            }
        });

        dialog.showAndWait();
    }
}
