package com.llamaquill.image;

import com.llamaquill.model.SeePromptPreset;
import com.llamaquill.model.ImageRatio;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
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
        void generate(String stylePrompt, String request, boolean ignoreResponseLength,
                Consumer<String> onSuccess, Consumer<Throwable> onFailure);
    }

    @FunctionalInterface
    public interface ImageGenerator
    {
        void generate(String promptText, int width, int height, int batchSize,
                Consumer<List<ImageGenerationCoordinator.PendingImage>> onSuccess,
                Consumer<Throwable> onFailure);
    }

    @FunctionalInterface
    public interface ImageInserter
    {
        void insert(ImageGenerationCoordinator.PendingImage pending, String promptText) throws Exception;
    }

    public static void show(Stage owner, String headerText, String initialPrompt, String defaultRequest,
            int defaultImageDimension, ImageRatio defaultImageRatio, int defaultImageBatchSize,
            SeePromptPresetService presetService, String selectedPresetId, Consumer<String> selectPreset,
            String insertButtonText, Consumer<String> showInfo, BiConsumer<String, Throwable> showError,
            Consumer<String> setStatus, Runnable beginBusy, Runnable endBusy, PromptGenerator promptGenerator,
            ImageGenerator imageGenerator, ImageInserter imageInserter)
    {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("See");
        dialog.setHeaderText(headerText);
        dialog.initOwner(owner);
        dialog.setResizable(true);

        ComboBox<SeePromptStyles.PresetChoice> presetChoice = new ComboBox<>();
        presetChoice.setMaxWidth(Double.MAX_VALUE);
        Button savePresetButton = new Button("Save");
        Button deletePresetButton = new Button("Delete");
        HBox presetRow = new HBox(8, presetChoice, savePresetButton, deletePresetButton);
        HBox.setHgrow(presetChoice, Priority.ALWAYS);

        TextArea stylePromptArea = new TextArea();
        stylePromptArea.setWrapText(true);
        stylePromptArea.setPrefRowCount(3);
        stylePromptArea.setPromptText("The selected style prompt is inserted before the custom request.");

        TextArea requestArea = new TextArea();
        requestArea.setWrapText(true);
        requestArea.setPrefRowCount(3);
        requestArea.setPromptText("Optional. Leave blank to infer from the latest scene.");

        CheckBox ignoreResponseLengthBox = new CheckBox("Ignore Response Length");
        ignoreResponseLengthBox.setSelected(true);
        ignoreResponseLengthBox.setTooltip(new Tooltip(
                "Omit the saved Response Length for this prompt without changing the saved setting."));

        TextArea promptArea = new TextArea(initialPrompt == null ? "" : initialPrompt);
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(6);
        promptArea.setPromptText("Generated image prompt will appear here.");

        Spinner<Integer> imageDimensionSpinner = buildSpinner(
                ImageRatio.MIN_DIMENSION, ImageRatio.MAX_DIMENSION,
                ImageRatio.normalizeDimension(defaultImageDimension), ImageRatio.DIMENSION_STEP);
        imageDimensionSpinner.setPrefWidth(150);
        imageDimensionSpinner.setTooltip(new Tooltip("The longer output edge in pixels."));
        ComboBox<ImageRatio> imageRatioChoice = new ComboBox<>();
        imageRatioChoice.getItems().setAll(ImageRatio.values());
        imageRatioChoice.setValue(defaultImageRatio == null ? ImageRatio.SQUARE : defaultImageRatio);
        imageRatioChoice.setPrefWidth(120);
        Spinner<Integer> imageBatchSizeSpinner = buildSpinner(1, 32, defaultImageBatchSize, 1);
        imageBatchSizeSpinner.setPrefWidth(110);
        Label calculatedSizeLabel = new Label();
        calculatedSizeLabel.setMinWidth(150);
        Runnable updateCalculatedSize = () ->
        {
            ImageRatio ratio = imageRatioChoice.getValue() == null ? ImageRatio.SQUARE : imageRatioChoice.getValue();
            ImageRatio.Dimensions dimensions = ratio.dimensions(imageDimensionSpinner.getValue());
            calculatedSizeLabel.setText(dimensions.toString());
        };
        imageDimensionSpinner.valueProperty().addListener((observable, previous, current) -> updateCalculatedSize.run());
        imageRatioChoice.valueProperty().addListener((observable, previous, current) -> updateCalculatedSize.run());
        updateCalculatedSize.run();

        VBox dimensionControl = new VBox(4, new Label("Image Dimension"), imageDimensionSpinner);
        VBox ratioControl = new VBox(4, new Label("Image Ratio"), imageRatioChoice);
        VBox batchControl = new VBox(4, new Label("Image Batch Size"), imageBatchSizeSpinner);
        VBox calculatedControl = new VBox(4, new Label("Output Size"), calculatedSizeLabel);
        HBox imageOptions = new HBox(12, dimensionControl, ratioControl, batchControl, calculatedControl);
        imageOptions.setAlignment(Pos.BOTTOM_LEFT);

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

        Button generatePromptButton = new Button("Generate Prompt");
        Button createImagesButton = new Button("Create Images");
        Button insertImageButton = new Button(insertButtonText);
        Button cancelButton = new Button("Cancel");
        insertImageButton.setDisable(true);

        Region promptActionSpacer = new Region();
        HBox.setHgrow(promptActionSpacer, Priority.ALWAYS);
        HBox promptActions = new HBox(8, ignoreResponseLengthBox, promptActionSpacer, generatePromptButton);
        promptActions.setAlignment(Pos.CENTER_LEFT);

        HBox actions = new HBox(8, createImagesButton, insertImageButton, cancelButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(10,
                new Label("Style Preset"),
                presetRow,
                new Label("Style Prompt"),
                stylePromptArea,
                new Label("Custom Request"),
                requestArea,
                promptActions,
                new Label("Image Prompt"),
                promptArea,
                imageOptions,
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

        loadPresetChoices(presetService, presetChoice, selectedPresetId, showError);
        SeePromptStyles.PresetChoice initialPreset = presetChoice.getValue() == null
                ? findDefaultPreset(presetChoice.getItems())
                : presetChoice.getValue();
        presetChoice.setValue(initialPreset);
        stylePromptArea.setText(initialPreset.prompt());
        deletePresetButton.setDisable(initialPreset.builtIn());
        presetChoice.setOnAction(event ->
        {
            SeePromptStyles.PresetChoice selected = presetChoice.getValue();
            if (selected != null)
            {
                stylePromptArea.setText(selected.prompt());
                deletePresetButton.setDisable(selected.builtIn());
                selectPreset.accept(selected.id());
            }
        });
        savePresetButton.setOnAction(event -> saveOrUpdatePreset(
                owner, presetService, presetChoice, stylePromptArea, selectPreset, showInfo, showError));
        deletePresetButton.setOnAction(event -> deletePreset(
                owner, presetService, presetChoice, selectPreset, showInfo, showError));

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
            generatePromptButton.setDisable(false);
            createImagesButton.setDisable(false);
            insertImageButton.setDisable(selectedIndexRef[0] < 0);
            cancelButton.setDisable(false);
            endBusy.run();
        };

        cancelButton.setOnAction(event -> dialog.close());

        generatePromptButton.setOnAction(event ->
        {
            String request = requestArea.getText().trim();
            if (request.isEmpty())
            {
                request = defaultRequest;
            }

            generatePromptButton.setDisable(true);
            createImagesButton.setDisable(true);
            insertImageButton.setDisable(true);
            cancelButton.setDisable(true);
            beginBusy.run();
            setStatus.accept("Generating image prompt...");
            promptGenerator.generate(stylePromptArea.getText(), request, ignoreResponseLengthBox.isSelected(),
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
            generatePromptButton.setDisable(true);
            createImagesButton.setDisable(true);
            insertImageButton.setDisable(true);
            cancelButton.setDisable(true);
            beginBusy.run();
            setStatus.accept("Generating images...");
            int dimension = ImageRatio.normalizeDimension(readSpinnerValue(imageDimensionSpinner));
            imageDimensionSpinner.getValueFactory().setValue(dimension);
            int batchSize = readSpinnerValue(imageBatchSizeSpinner);
            ImageRatio ratio = imageRatioChoice.getValue() == null ? ImageRatio.SQUARE : imageRatioChoice.getValue();
            ImageRatio.Dimensions dimensions = ratio.dimensions(dimension);
            updateCalculatedSize.run();
            imageGenerator.generate(promptText, dimensions.width(), dimensions.height(), batchSize,
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

    private static Spinner<Integer> buildSpinner(int min, int max, int value, int step)
    {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, value, step));
        spinner.setEditable(true);
        return spinner;
    }

    private static int readSpinnerValue(Spinner<Integer> spinner)
    {
        SpinnerValueFactory<Integer> factory = spinner.getValueFactory();
        Integer fallback = factory.getValue();
        try
        {
            Integer value = factory.getConverter().fromString(spinner.getEditor().getText());
            if (value == null)
            {
                throw new IllegalArgumentException("Spinner value cannot be blank.");
            }
            factory.setValue(value);
        }
        catch (RuntimeException ignored)
        {
            factory.setValue(fallback);
            spinner.getEditor().setText(factory.getConverter().toString(fallback));
        }
        return factory.getValue();
    }

    private static void loadPresetChoices(SeePromptPresetService service,
            ComboBox<SeePromptStyles.PresetChoice> choices, String selectedId,
            BiConsumer<String, Throwable> showError)
    {
        try
        {
            choices.getItems().setAll(service.listChoices());
            SeePromptStyles.PresetChoice selected = choices.getItems().stream()
                    .filter(choice -> choice.id().equals(selectedId))
                    .findFirst()
                    .orElseGet(() -> findDefaultPreset(choices.getItems()));
            choices.setValue(selected);
        }
        catch (Exception e)
        {
            showError.accept("Failed to load See style presets", e);
        }
    }

    private static SeePromptStyles.PresetChoice findDefaultPreset(List<SeePromptStyles.PresetChoice> choices)
    {
        return choices.stream()
                .filter(choice -> choice.id().equals(SeePromptStyles.defaultPreset().id()))
                .findFirst()
                .orElse(SeePromptStyles.defaultPreset());
    }

    private static void saveOrUpdatePreset(Stage owner, SeePromptPresetService service,
            ComboBox<SeePromptStyles.PresetChoice> choices, TextArea promptArea,
            Consumer<String> selectPreset, Consumer<String> showInfo,
            BiConsumer<String, Throwable> showError)
    {
        SeePromptStyles.PresetChoice selected = choices.getValue();
        boolean update = selected != null && !selected.builtIn();
        TextInputDialog nameDialog = new TextInputDialog(update ? selected.name() : "");
        nameDialog.initOwner(owner);
        nameDialog.setTitle("Save See Style");
        nameDialog.setHeaderText(update ? "Update this reusable style" : "Save as a new reusable style");
        nameDialog.setContentText("Preset name:");
        nameDialog.showAndWait().ifPresent(name ->
        {
            try
            {
                SeePromptPreset saved = update
                        ? service.update(selected.id(), name, promptArea.getText())
                        : service.create(name, promptArea.getText());
                loadPresetChoices(service, choices, saved.id(), showError);
                selectPreset.accept(saved.id());
                showInfo.accept(update ? "See style preset updated." : "See style preset saved.");
            }
            catch (Exception e)
            {
                showError.accept("Failed to save See style preset", e);
            }
        });
    }

    private static void deletePreset(Stage owner, SeePromptPresetService service,
            ComboBox<SeePromptStyles.PresetChoice> choices, Consumer<String> selectPreset,
            Consumer<String> showInfo, BiConsumer<String, Throwable> showError)
    {
        SeePromptStyles.PresetChoice selected = choices.getValue();
        if (selected == null || selected.builtIn())
        {
            showInfo.accept("Built-in See style presets cannot be deleted.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(owner);
        confirm.setTitle("Delete See Style");
        confirm.setHeaderText("Delete preset \"" + selected.name() + "\"?");
        if (confirm.showAndWait().filter(ButtonType.OK::equals).isEmpty())
        {
            return;
        }
        try
        {
            service.delete(selected.id());
            loadPresetChoices(service, choices, SeePromptStyles.NONE_ID, showError);
            selectPreset.accept(SeePromptStyles.NONE_ID);
            showInfo.accept("See style preset deleted.");
        }
        catch (Exception e)
        {
            showError.accept("Failed to delete See style preset", e);
        }
    }
}
