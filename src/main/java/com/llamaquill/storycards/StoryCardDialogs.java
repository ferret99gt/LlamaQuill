package com.llamaquill.storycards;

import com.llamaquill.model.StoryCard;
import com.llamaquill.model.StoryCardCommandPreset;
import com.llamaquill.util.Ids;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class StoryCardDialogs
{
    private static final List<String> STANDARD_TYPES =
            List.of("Character", "Class", "Race", "Location", "Faction", "Custom");

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
    public interface GenerationContextSaver
    {
        void save(String context) throws Exception;
    }

    @FunctionalInterface
    public interface DraftGenerator
    {
        void generate(StoryCardGenerationRequest request,
                Consumer<StoryCardGenerationCoordinator.Result> onSuccess,
                Consumer<Throwable> onFailure);
    }

    public static void showCardDialog(Stage owner, String storyId, StoryCard card,
            StoryCardPresetService presetService, String selectedPresetId, Consumer<String> selectPreset,
            String additionalGenerationContext, GenerationContextSaver contextSaver,
            Consumer<String> showInfo,
            BiConsumer<String, Throwable> showError, Consumer<String> setStatus,
            DraftGenerator draftGenerator, CardSaver saver, CardDeleter deleter)
    {
        boolean isNew = card == null;
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "New Story Card" : "Edit Story Card");
        dialog.setHeaderText(isNew ? "Create a story card" : "Edit story card");
        dialog.initOwner(owner);
        dialog.setResizable(true);
        boolean[] generationInProgress = {false};
        dialog.setOnCloseRequest(event ->
        {
            if (generationInProgress[0])
            {
                event.consume();
                showInfo.accept("Please wait for Story Card generation to finish.");
            }
        });

        ComboBox<String> typeChoice = new ComboBox<>();
        typeChoice.getItems().setAll(STANDARD_TYPES);
        typeChoice.setMaxWidth(Double.MAX_VALUE);
        TextField customTypeField = new TextField();
        customTypeField.setPromptText("Custom type");
        configureTypeControls(typeChoice, customTypeField, card);

        TextField titleField = new TextField(isNew ? "" : card.title());
        TextArea entryArea = textArea(isNew ? "" : card.content(), 8);
        TextField triggersField = new TextField(isNew ? "" : card.triggers());
        TextArea notesArea = textArea(isNew ? "" : card.notes(), 5);
        CheckBox pinnedBox = new CheckBox("Pinned");
        pinnedBox.setSelected(!isNew && card.pinned());

        ComboBox<StoryCardCommands.PresetChoice> presetChoice = new ComboBox<>();
        presetChoice.setMaxWidth(Double.MAX_VALUE);
        TextArea commandArea = textArea("", 7);
        TextArea additionalContextArea = textArea(additionalGenerationContext, 5);
        additionalContextArea.setPromptText("Lore, notes, or keywords that should guide this generation.");
        CheckBox logGenerationsBox = new CheckBox("Log replaced entries in Notes");
        logGenerationsBox.setSelected(true);

        Button savePresetButton = new Button("Save");
        Button deletePresetButton = new Button("Delete");
        Button generateFromDetailsButton = new Button("Generate Entry with AI");

        TabPane tabs = new TabPane();
        Tab detailsTab = new Tab("Details");
        detailsTab.setClosable(false);
        Tab commandTab = new Tab("Command");
        commandTab.setClosable(false);
        tabs.getTabs().addAll(detailsTab, commandTab);

        HBox triggerRow = new HBox(8, triggersField, pinnedBox);
        triggerRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(triggersField, Priority.ALWAYS);

        VBox details = new VBox(8,
                new Label("Type"), typeChoice, customTypeField,
                new Label("Title"), titleField,
                new Label("Entry"), entryArea, generateFromDetailsButton,
                new Label("Triggers (comma separated)"), triggerRow,
                new Label("Notes (not sent to the model)"), notesArea);
        details.setPadding(new Insets(10));
        detailsTab.setContent(scrollable(details));

        Label tokenHelp = new Label("Required: {{title}}    Optional: {{triggers}}, {{entry}}");
        tokenHelp.setWrapText(true);
        HBox presetRow = new HBox(8, presetChoice, savePresetButton, deletePresetButton);
        HBox.setHgrow(presetChoice, Priority.ALWAYS);
        VBox command = new VBox(8,
                new Label("Story Card Command Preset"), presetRow,
                new Label("Story Card Command"), commandArea, tokenHelp,
                new Label("Additional Generation Context"), additionalContextArea,
                logGenerationsBox);
        command.setPadding(new Insets(10));
        commandTab.setContent(scrollable(command));

        loadPresetChoices(presetService, presetChoice, selectedPresetId, showError);
        StoryCardCommands.PresetChoice initialPreset = presetChoice.getValue() == null
                ? findDefaultPreset(presetChoice.getItems())
                : presetChoice.getValue();
        presetChoice.setValue(initialPreset);
        commandArea.setText(initialPreset.command());
        deletePresetButton.setDisable(initialPreset.builtIn());
        presetChoice.setOnAction(event ->
        {
            StoryCardCommands.PresetChoice selected = presetChoice.getValue();
            if (selected != null)
            {
                commandArea.setText(selected.command());
                deletePresetButton.setDisable(selected.builtIn());
                selectPreset.accept(selected.id());
            }
        });

        savePresetButton.setOnAction(event ->
                saveOrUpdatePreset(owner, presetService, presetChoice, commandArea, showInfo, showError));
        deletePresetButton.setOnAction(event ->
                deletePreset(owner, presetService, presetChoice, showInfo, showError));

        Runnable generate = () ->
        {
            String title = titleField.getText().trim();
            if (title.isBlank())
            {
                showInfo.accept("Card title cannot be empty.");
                return;
            }
            String triggers = triggersField.getText().trim();
            if (triggers.isBlank())
            {
                triggers = title;
                triggersField.setText(triggers);
            }

            StoryCardGenerationRequest request;
            try
            {
                String validatedCommand = StoryCardCommands.validateCommand(commandArea.getText());
                request = new StoryCardGenerationRequest(
                        isNew ? "" : card.id(),
                        title,
                        triggers,
                        validatedCommand,
                        additionalContextArea.getText());
            }
            catch (IllegalArgumentException e)
            {
                showInfo.accept(e.getMessage());
                return;
            }

            try
            {
                contextSaver.save(request.additionalContext());
            }
            catch (Exception e)
            {
                showError.accept("Failed to save Story Card generation context", e);
                return;
            }

            generationInProgress[0] = true;
            tabs.setDisable(true);
            setDialogActionsDisabled(dialog, true);
            setStatus.accept("Generating Story Card entry...");
            String priorEntry = entryArea.getText();
            draftGenerator.generate(request,
                    result ->
                    {
                        generationInProgress[0] = false;
                        tabs.setDisable(false);
                        setDialogActionsDisabled(dialog, false);
                        deletePresetButton.setDisable(
                                presetChoice.getValue() == null || presetChoice.getValue().builtIn());
                        if (logGenerationsBox.isSelected() && !priorEntry.isBlank())
                        {
                            notesArea.setText(StoryCardCommands.appendGenerationHistory(
                                    notesArea.getText(), priorEntry, LocalDateTime.now()));
                        }
                        entryArea.setText(result.entry());
                        if (triggersField.getText().isBlank())
                        {
                            triggersField.setText(result.resolvedTriggers());
                        }
                        setStatus.accept("Generated Story Card entry");
                        tabs.getSelectionModel().select(detailsTab);
                    },
                    error ->
                    {
                        generationInProgress[0] = false;
                        tabs.setDisable(false);
                        setDialogActionsDisabled(dialog, false);
                        deletePresetButton.setDisable(
                                presetChoice.getValue() == null || presetChoice.getValue().builtIn());
                        setStatus.accept("Story Card generation failed");
                        showError.accept("Failed to generate Story Card entry", error);
                    });
        };

        generateFromDetailsButton.setOnAction(event ->
        {
            generate.run();
        });

        dialog.getDialogPane().setContent(tabs);
        dialog.getDialogPane().setPrefSize(780, 680);

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

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(ActionEvent.ACTION, event ->
        {
            event.consume();
            String title = titleField.getText().trim();
            if (title.isBlank())
            {
                showInfo.accept("Card title cannot be empty.");
                return;
            }
            String triggers = triggersField.getText().trim();
            if (triggers.isBlank())
            {
                triggers = title;
                triggersField.setText(triggers);
            }
            StoryCard updated = new StoryCard(
                    isNew ? Ids.newId() : card.id(),
                    storyId,
                    title,
                    triggers,
                    entryArea.getText().trim(),
                    resolvedType(typeChoice, customTypeField),
                    notesArea.getText(),
                    pinnedBox.isSelected());
            try
            {
                contextSaver.save(additionalContextArea.getText());
                saver.save(updated);
                dialog.close();
            }
            catch (Exception e)
            {
                showError.accept(isNew ? "Failed to create Story Card" : "Failed to update Story Card", e);
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
                    showError.accept("Failed to delete Story Card", e);
                }
            });
        }

        dialog.showAndWait();
    }

    private static void configureTypeControls(ComboBox<String> typeChoice, TextField customTypeField, StoryCard card)
    {
        String savedType = card == null ? "Character" : card.type().trim();
        if (STANDARD_TYPES.subList(0, STANDARD_TYPES.size() - 1).contains(savedType))
        {
            typeChoice.setValue(savedType);
        }
        else
        {
            typeChoice.setValue("Custom");
            customTypeField.setText(savedType);
        }
        Runnable updateVisibility = () ->
        {
            boolean custom = "Custom".equals(typeChoice.getValue());
            customTypeField.setVisible(custom);
            customTypeField.setManaged(custom);
        };
        typeChoice.setOnAction(event -> updateVisibility.run());
        updateVisibility.run();
    }

    private static String resolvedType(ComboBox<String> typeChoice, TextField customTypeField)
    {
        String selected = typeChoice.getValue();
        return "Custom".equals(selected) ? customTypeField.getText().trim() : selected == null ? "" : selected;
    }

    private static TextArea textArea(String value, int rows)
    {
        TextArea area = new TextArea(value == null ? "" : value);
        area.setWrapText(true);
        area.setPrefRowCount(rows);
        return area;
    }

    private static ScrollPane scrollable(VBox content)
    {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(false);
        return scrollPane;
    }

    private static void setDialogActionsDisabled(Dialog<?> dialog, boolean disabled)
    {
        for (ButtonType type : dialog.getDialogPane().getButtonTypes())
        {
            Node control = dialog.getDialogPane().lookupButton(type);
            if (control != null)
            {
                control.setDisable(disabled);
            }
        }
    }

    private static void loadPresetChoices(StoryCardPresetService service,
            ComboBox<StoryCardCommands.PresetChoice> choices, String selectedId,
            BiConsumer<String, Throwable> showError)
    {
        try
        {
            choices.getItems().setAll(service.listChoices());
            StoryCardCommands.PresetChoice selected = choices.getItems().stream()
                    .filter(choice -> choice.id().equals(selectedId))
                    .findFirst()
                    .orElseGet(() -> findDefaultPreset(choices.getItems()));
            choices.setValue(selected);
        }
        catch (Exception e)
        {
            showError.accept("Failed to load Story Card command presets", e);
        }
    }

    private static StoryCardCommands.PresetChoice findDefaultPreset(
            List<StoryCardCommands.PresetChoice> choices)
    {
        return choices.stream()
                .filter(choice -> choice.id().equals(StoryCardCommands.defaultPreset().id()))
                .findFirst()
                .orElse(StoryCardCommands.defaultPreset());
    }

    private static void saveOrUpdatePreset(Stage owner, StoryCardPresetService service,
            ComboBox<StoryCardCommands.PresetChoice> choices, TextArea commandArea,
            Consumer<String> showInfo, BiConsumer<String, Throwable> showError)
    {
        StoryCardCommands.PresetChoice selected = choices.getValue();
        boolean update = selected != null && !selected.builtIn();
        TextInputDialog nameDialog = new TextInputDialog(update ? selected.name() : "");
        nameDialog.initOwner(owner);
        nameDialog.setTitle("Save Story Card Command");
        nameDialog.setHeaderText(update ? "Update this reusable preset" : "Save as a new reusable preset");
        nameDialog.setContentText("Preset name:");
        nameDialog.showAndWait().ifPresent(name ->
        {
            try
            {
                StoryCardCommandPreset saved = update
                        ? service.update(selected.id(), name, commandArea.getText())
                        : service.create(name, commandArea.getText());
                loadPresetChoices(service, choices, saved.id(), showError);
                showInfo.accept(update ? "Story Card command preset updated." : "Story Card command preset saved.");
            }
            catch (Exception e)
            {
                showError.accept("Failed to save Story Card command preset", e);
            }
        });
    }

    private static void deletePreset(Stage owner, StoryCardPresetService service,
            ComboBox<StoryCardCommands.PresetChoice> choices, Consumer<String> showInfo,
            BiConsumer<String, Throwable> showError)
    {
        StoryCardCommands.PresetChoice selected = choices.getValue();
        if (selected == null || selected.builtIn())
        {
            showInfo.accept("Built-in Story Card command presets cannot be deleted.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(owner);
        confirm.setTitle("Delete Story Card Command");
        confirm.setHeaderText("Delete preset \"" + selected.name() + "\"?");
        if (confirm.showAndWait().filter(ButtonType.OK::equals).isEmpty())
        {
            return;
        }
        try
        {
            service.delete(selected.id());
            loadPresetChoices(service, choices, StoryCardCommands.defaultPreset().id(), showError);
            showInfo.accept("Story Card command preset deleted.");
        }
        catch (Exception e)
        {
            showError.accept("Failed to delete Story Card command preset", e);
        }
    }
}
