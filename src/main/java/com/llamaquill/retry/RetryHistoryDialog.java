package com.llamaquill.retry;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.util.List;

public final class RetryHistoryDialog
{
    private RetryHistoryDialog()
    {
    }

    public sealed interface Entry permits TextEntry, ImageEntry
    {
    }

    public record TextEntry(String text) implements Entry
    {
    }

    public record ImageEntry(String prompt, byte[] bytes) implements Entry
    {
    }

    public static Integer show(Stage owner, List<Entry> entries, int initialIndex, boolean imageMode)
    {
        if (entries == null || entries.size() < 2)
        {
            return null;
        }

        int startIndex = Math.max(0, Math.min(entries.size() - 1, initialIndex));

        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("Retry History");
        dialog.setHeaderText("Select a retry");
        dialog.initOwner(owner);

        final TextArea[] textPreviewRef = new TextArea[1];
        final ImageView[] imagePreviewRef = new ImageView[1];
        final TextArea[] promptPreviewRef = new TextArea[1];

        VBox content;
        if (imageMode)
        {
            ImageView imagePreview = new ImageView();
            imagePreview.setPreserveRatio(true);
            imagePreview.setSmooth(true);
            imagePreview.setFitWidth(760);
            imagePreview.setFitHeight(420);

            TextArea promptPreview = new TextArea();
            promptPreview.setWrapText(true);
            promptPreview.setEditable(false);
            promptPreview.setPrefRowCount(4);

            imagePreviewRef[0] = imagePreview;
            promptPreviewRef[0] = promptPreview;
            content = new VBox(8, imagePreview, new Label("Prompt"), promptPreview);
        }
        else
        {
            TextArea textPreview = new TextArea();
            textPreview.setWrapText(true);
            textPreview.setEditable(false);
            textPreview.setPrefRowCount(8);
            textPreviewRef[0] = textPreview;
            content = new VBox(8, textPreview);
        }
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

        final int[] currentIndex = new int[] { startIndex };
        Runnable refresh = () ->
        {
            Entry entry = entries.get(currentIndex[0]);
            if (imageMode && entry instanceof ImageEntry imageEntry)
            {
                imagePreviewRef[0].setImage(new Image(new ByteArrayInputStream(imageEntry.bytes())));
                promptPreviewRef[0].setText(imageEntry.prompt());
            }
            if (!imageMode && entry instanceof TextEntry textEntry)
            {
                textPreviewRef[0].setText(textEntry.text());
            }
            prevButton.setDisable(currentIndex[0] <= 0);
            nextButton.setDisable(currentIndex[0] >= entries.size() - 1);
        };
        refresh.run();

        prevButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            if (currentIndex[0] > 0)
            {
                currentIndex[0]--;
                refresh.run();
            }
        });

        nextButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            if (currentIndex[0] < entries.size() - 1)
            {
                currentIndex[0]++;
                refresh.run();
            }
        });

        selectButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            dialog.setResult(currentIndex[0]);
            dialog.close();
        });

        return dialog.showAndWait().orElse(null);
    }
}
