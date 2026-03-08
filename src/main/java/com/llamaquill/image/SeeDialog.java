package com.llamaquill.image;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class SeeDialog
{
    private SeeDialog()
    {
    }

    @FunctionalInterface
    public interface PromptGenerator
    {
        void generate(String request, Consumer<String> onSuccess, Consumer<Throwable> onFailure);
    }

    @FunctionalInterface
    public interface ImageGenerator
    {
        void generate(String promptText, Consumer<List<ImageGenerationCoordinator.PendingImage>> onSuccess,
                Consumer<Throwable> onFailure);
    }

    @FunctionalInterface
    public interface ImageInserter
    {
        void insert(ImageGenerationCoordinator.PendingImage pending, String promptText) throws Exception;
    }

    public static void show(Stage owner, String headerText, String initialPrompt, String defaultRequest,
            String insertButtonText, Consumer<String> showInfo, BiConsumer<String, Throwable> showError,
            Consumer<String> setStatus, Runnable beginBusy, Runnable endBusy, PromptGenerator promptGenerator,
            ImageGenerator imageGenerator, ImageInserter imageInserter)
    {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("See");
        dialog.setHeaderText(headerText);
        dialog.initOwner(owner);
        dialog.setResizable(true);

        TextArea requestArea = new TextArea();
        requestArea.setWrapText(true);
        requestArea.setPrefRowCount(3);
        requestArea.setPromptText("Optional. Leave blank to infer from the latest scene.");

        TextArea promptArea = new TextArea(initialPrompt == null ? "" : initialPrompt);
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(6);
        promptArea.setPromptText("Generated image prompt will appear here.");

        ImageView selectedPreview = new ImageView();
        selectedPreview.setPreserveRatio(true);
        selectedPreview.setSmooth(true);
        selectedPreview.setFitWidth(640);
        selectedPreview.setFitHeight(320);
        selectedPreview.setVisible(false);
        selectedPreview.setManaged(false);

        TilePane thumbnails = new TilePane();
        thumbnails.setHgap(8);
        thumbnails.setVgap(8);
        thumbnails.setPrefColumns(4);
        thumbnails.setPrefTileWidth(140);
        thumbnails.setPrefTileHeight(140);
        thumbnails.setTileAlignment(Pos.CENTER);

        Label imagesPlaceholder = new Label("No images yet. Generate a prompt, then create images.");
        imagesPlaceholder.setWrapText(true);
        imagesPlaceholder.setStyle("-fx-text-fill: #b8b1a5;");

        VBox imageResultsBox = new VBox(8, new Label("Images"), imagesPlaceholder, selectedPreview, thumbnails);
        imageResultsBox.setPadding(new Insets(8));
        imageResultsBox.setStyle("-fx-border-color: rgba(255,255,255,0.12); -fx-border-width: 1; -fx-border-radius: 4;");

        Button regeneratePromptButton = new Button("Regenerate Prompt");
        Button createImagesButton = new Button("Create Images");
        Button insertImageButton = new Button(insertButtonText);
        Button cancelButton = new Button("Cancel");
        insertImageButton.setDisable(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox rightButtons = new HBox(8, createImagesButton, insertImageButton, cancelButton);
        HBox actions = new HBox(8, regeneratePromptButton, spacer, rightButtons);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10,
                new Label("Request"),
                requestArea,
                new Label("Image Prompt"),
                promptArea,
                imageResultsBox,
                actions);
        content.setPadding(new Insets(10));

        ScrollPane contentScroll = new ScrollPane(content);
        contentScroll.setFitToWidth(true);
        contentScroll.setFitToHeight(false);
        contentScroll.setPannable(true);
        contentScroll.setPrefViewportWidth(980);
        contentScroll.setPrefViewportHeight(660);
        dialog.getDialogPane().setContent(contentScroll);
        dialog.getDialogPane().setPrefWidth(1040);
        dialog.getDialogPane().setPrefHeight(720);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        Node hiddenCancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (hiddenCancel != null)
        {
            hiddenCancel.setVisible(false);
            hiddenCancel.setManaged(false);
        }

        final List<ImageGenerationCoordinator.PendingImage>[] pendingImagesRef = new List[] { new ArrayList<>() };
        final int[] selectedIndexRef = new int[] { -1 };

        Runnable refreshImageSelectionUi = () ->
        {
            thumbnails.getChildren().clear();
            List<ImageGenerationCoordinator.PendingImage> pendingImages = pendingImagesRef[0];
            if (pendingImages == null || pendingImages.isEmpty())
            {
                imagesPlaceholder.setVisible(true);
                imagesPlaceholder.setManaged(true);
                selectedPreview.setVisible(false);
                selectedPreview.setManaged(false);
                insertImageButton.setDisable(true);
                return;
            }

            imagesPlaceholder.setVisible(false);
            imagesPlaceholder.setManaged(false);
            ToggleGroup group = new ToggleGroup();
            for (int i = 0; i < pendingImages.size(); i++)
            {
                ImageGenerationCoordinator.PendingImage pending = pendingImages.get(i);
                Image img = new Image(new ByteArrayInputStream(pending.bytes()));
                ImageView thumbView = new ImageView(img);
                thumbView.setPreserveRatio(true);
                thumbView.setSmooth(true);
                thumbView.setFitWidth(132);
                thumbView.setFitHeight(132);

                ToggleButton thumbButton = new ToggleButton();
                thumbButton.setGraphic(thumbView);
                thumbButton.setToggleGroup(group);
                thumbButton.setUserData(i);
                thumbButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                thumbButton.setStyle("-fx-padding: 4;");
                if (i == selectedIndexRef[0])
                {
                    thumbButton.setSelected(true);
                    selectedPreview.setImage(img);
                    selectedPreview.setVisible(true);
                    selectedPreview.setManaged(true);
                }
                thumbnails.getChildren().add(thumbButton);
            }

            group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) ->
            {
                if (newToggle == null)
                {
                    selectedIndexRef[0] = -1;
                    insertImageButton.setDisable(true);
                    return;
                }
                int index = (int) newToggle.getUserData();
                selectedIndexRef[0] = index;
                ImageGenerationCoordinator.PendingImage pending = pendingImagesRef[0].get(index);
                selectedPreview.setImage(new Image(new ByteArrayInputStream(pending.bytes())));
                selectedPreview.setVisible(true);
                selectedPreview.setManaged(true);
                insertImageButton.setDisable(false);
            });

            if (selectedIndexRef[0] < 0 && !pendingImages.isEmpty())
            {
                selectedIndexRef[0] = 0;
                ImageGenerationCoordinator.PendingImage first = pendingImages.getFirst();
                selectedPreview.setImage(new Image(new ByteArrayInputStream(first.bytes())));
                selectedPreview.setVisible(true);
                selectedPreview.setManaged(true);
                insertImageButton.setDisable(false);
            }
            else
            {
                insertImageButton.setDisable(selectedIndexRef[0] < 0);
            }
        };

        Runnable restoreButtons = () ->
        {
            regeneratePromptButton.setDisable(false);
            createImagesButton.setDisable(false);
            insertImageButton.setDisable(selectedIndexRef[0] < 0);
            cancelButton.setDisable(false);
            endBusy.run();
        };

        cancelButton.setOnAction(event -> dialog.close());

        regeneratePromptButton.setOnAction(event ->
        {
            String request = requestArea.getText().trim();
            if (request.isEmpty())
            {
                request = defaultRequest;
            }

            regeneratePromptButton.setDisable(true);
            createImagesButton.setDisable(true);
            insertImageButton.setDisable(true);
            cancelButton.setDisable(true);
            beginBusy.run();
            setStatus.accept("Generating image prompt...");
            promptGenerator.generate(request,
                    generated ->
                    {
                        restoreButtons.run();
                        if (generated == null || generated.isBlank())
                        {
                            setStatus.accept("Image prompt generation returned empty.");
                            return;
                        }
                        promptArea.setText(generated);
                        setStatus.accept("Generated image prompt");
                    },
                    error ->
                    {
                        restoreButtons.run();
                        setStatus.accept("Image prompt generation failed");
                        showError.accept("Failed to generate image prompt", error);
                    });
        });

        createImagesButton.setOnAction(event ->
        {
            String promptText = promptArea.getText();
            if (promptText == null || promptText.isBlank())
            {
                showInfo.accept("Image prompt cannot be empty.");
                return;
            }
            regeneratePromptButton.setDisable(true);
            createImagesButton.setDisable(true);
            insertImageButton.setDisable(true);
            cancelButton.setDisable(true);
            beginBusy.run();
            setStatus.accept("Generating images...");
            imageGenerator.generate(promptText,
                    pending ->
                    {
                        restoreButtons.run();
                        if (pending == null || pending.isEmpty())
                        {
                            setStatus.accept("ComfyUI returned no images.");
                            return;
                        }
                        pendingImagesRef[0] = pending;
                        selectedIndexRef[0] = pending.isEmpty() ? -1 : 0;
                        refreshImageSelectionUi.run();
                        createImagesButton.setText("Regenerate Images");
                        setStatus.accept("Generated " + pending.size() + " image(s)");
                    },
                    error ->
                    {
                        restoreButtons.run();
                        setStatus.accept("Image generation failed");
                        showError.accept("Failed to generate images", error);
                    });
        });

        insertImageButton.setOnAction(event ->
        {
            int selectedIndex = selectedIndexRef[0];
            if (selectedIndex < 0 || selectedIndex >= pendingImagesRef[0].size())
            {
                showInfo.accept("Select an image first.");
                return;
            }
            ImageGenerationCoordinator.PendingImage pending = pendingImagesRef[0].get(selectedIndex);
            String promptText = promptArea.getText() == null ? "" : promptArea.getText().trim();
            if (promptText.isBlank())
            {
                showInfo.accept("Image prompt cannot be empty.");
                return;
            }
            try
            {
                imageInserter.insert(pending, promptText);
                dialog.close();
            }
            catch (Exception ex)
            {
                showError.accept("Failed to insert image", ex);
            }
        });

        if (promptArea.getText().isBlank())
        {
            requestArea.setText("");
        }
        refreshImageSelectionUi.run();
        dialog.showAndWait();
    }
}
