package com.llamaquill.generation;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PromptDialog
{
    private PromptDialog()
    {
    }

    @FunctionalInterface
    public interface ResponseGenerator
    {
        void generate(String systemPrompt, String userPrompt, boolean overrideNumPredict,
                Consumer<String> onSuccess, Consumer<Throwable> onFailure);
    }

    public static void show(Stage owner, String defaultSystemPrompt, Consumer<String> showInfo,
            BiConsumer<String, Throwable> showError, Consumer<String> setStatus, Runnable beginBusy, Runnable endBusy,
            ResponseGenerator generator)
    {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Prompt");
        dialog.setHeaderText("Prompt the model against the current story context");
        dialog.initOwner(owner);
        dialog.setResizable(true);

        TextArea systemArea = new TextArea(defaultSystemPrompt == null ? "" : defaultSystemPrompt);
        systemArea.setWrapText(true);
        systemArea.setPrefRowCount(4);

        TextArea userArea = new TextArea();
        userArea.setWrapText(true);
        userArea.setPrefRowCount(6);

        TextArea responseArea = new TextArea();
        responseArea.setWrapText(true);
        responseArea.setPrefRowCount(12);

        CheckBox overrideNumPredict = new CheckBox(
                "Override Response Length (allow full model response)");
        overrideNumPredict.setSelected(true);
        overrideNumPredict.setTooltip(new Tooltip(
                "Omit Response Length for this Prompt without changing the saved Response Length setting."));

        Button generateButton = new Button("Generate Response");
        Button cancelButton = new Button("Cancel");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, generateButton, spacer, cancelButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10,
                new Label("System Prompt"),
                systemArea,
                new Label("User Prompt"),
                userArea,
                overrideNumPredict,
                new Label("Response"),
                responseArea,
                actions);
        content.setPadding(new Insets(10));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(900);
        dialog.getDialogPane().setPrefHeight(760);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        Node hiddenCancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (hiddenCancel != null)
        {
            hiddenCancel.setVisible(false);
            hiddenCancel.setManaged(false);
        }

        Runnable restoreButtons = () -> generateButton.setDisable(false);

        generateButton.setOnAction(event ->
        {
            String systemPrompt = systemArea.getText();
            String userPrompt = userArea.getText();
            if ((systemPrompt == null || systemPrompt.isBlank()) && (userPrompt == null || userPrompt.isBlank()))
            {
                showInfo.accept("System prompt and user prompt cannot both be empty.");
                return;
            }

            generateButton.setDisable(true);
            setStatus.accept("Generating prompt response...");
            beginBusy.run();
            generator.generate(systemPrompt, userPrompt, overrideNumPredict.isSelected(),
                    response ->
                    {
                        restoreButtons.run();
                        endBusy.run();
                        responseArea.setText(response == null ? "" : response);
                        setStatus.accept("Prompt response ready");
                    },
                    error ->
                    {
                        restoreButtons.run();
                        endBusy.run();
                        setStatus.accept("Prompt failed");
                        showError.accept("Failed to generate prompt response", error);
                    });
        });

        cancelButton.setOnAction(event -> dialog.close());
        dialog.showAndWait();
    }
}
