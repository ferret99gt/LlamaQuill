package com.llamaquill.settings;

import com.llamaquill.model.AppSettings;
import com.llamaquill.model.ConversationLayout;
import com.llamaquill.model.ModelSettings;
import com.llamaquill.model.StoryCardWrappingStyle;
import com.llamaquill.serviceClients.OllamaModelDetails;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SettingsPaneController
{
    private final Actions actions;
    private final Tab tab;
    private final TextField ollamaUrlField;
    private final TextField comfyUiUrlField;
    private final ComboBox<String> comfyWorkflowSelect;
    private final ComboBox<String> modelSelect;
    private final ComboBox<StoryCardWrappingStyle> storyCardWrappingStyleSelect;
    private final ComboBox<ConversationLayout> conversationLayoutSelect;
    private final Button refreshModelsButton;
    private final Label modelDetailsLabel;
    private final Label contextLimitValueLabel;
    private final Slider contextLimitSlider;
    private final Slider responseLengthSlider;
    private final Slider temperatureSlider;
    private final Slider topKSlider;
    private final Slider topPSlider;
    private final Slider minPSlider;
    private final Slider typicalPSlider;
    private final Slider presencePenaltySlider;
    private final Slider frequencyPenaltySlider;
    private final Slider repeatLastNSlider;
    private final Slider repetitionPenaltySlider;
    private final CheckBox responseLengthEnabledBox;
    private final CheckBox temperatureEnabledBox;
    private final CheckBox topKEnabledBox;
    private final CheckBox topPEnabledBox;
    private final CheckBox minPEnabledBox;
    private final CheckBox typicalPEnabledBox;
    private final CheckBox presencePenaltyEnabledBox;
    private final CheckBox frequencyPenaltyEnabledBox;
    private final CheckBox repeatLastNEnabledBox;
    private final CheckBox repetitionPenaltyEnabledBox;
    private boolean updating;

    public SettingsPaneController(AppSettings appSettings, ModelSettings modelSettings,
            List<String> comfyWorkflowNames, Path databasePath, Actions actions)
    {
        Objects.requireNonNull(appSettings, "appSettings");
        Objects.requireNonNull(modelSettings, "modelSettings");
        Objects.requireNonNull(databasePath, "databasePath");
        this.actions = Objects.requireNonNull(actions, "actions");

        VBox content = new VBox(12);
        content.setPadding(new Insets(10));

        ollamaUrlField = new TextField(appSettings.ollamaUrl());
        ollamaUrlField.setPromptText("Ollama URL");
        ollamaUrlField.focusedProperty().addListener((obs, oldValue, focused) ->
        {
            if (!focused)
            {
                actions.settingChanged(new Change(Setting.OLLAMA_URL, ollamaUrlField.getText()));
            }
        });

        comfyUiUrlField = new TextField(appSettings.comfyUiUrl());
        comfyUiUrlField.setPromptText("ComfyUI URL");
        comfyUiUrlField.focusedProperty().addListener((obs, oldValue, focused) ->
        {
            if (!focused)
            {
                actions.settingChanged(new Change(Setting.COMFY_UI_URL, comfyUiUrlField.getText()));
            }
        });

        comfyWorkflowSelect = new ComboBox<>();
        comfyWorkflowSelect.setMaxWidth(Double.MAX_VALUE);
        comfyWorkflowSelect.setItems(FXCollections.observableArrayList(
                comfyWorkflowNames == null ? List.of() : comfyWorkflowNames));
        comfyWorkflowSelect.setValue(appSettings.comfyWorkflow());
        comfyWorkflowSelect.setOnAction(event ->
        {
            if (!updating && comfyWorkflowSelect.getValue() != null)
            {
                actions.settingChanged(new Change(Setting.COMFY_WORKFLOW, comfyWorkflowSelect.getValue()));
            }
        });

        Spinner<Integer> comfyWidthSpinner = buildSpinner(64, 4096, appSettings.comfyWidth());
        Spinner<Integer> comfyHeightSpinner = buildSpinner(64, 4096, appSettings.comfyHeight());
        Spinner<Integer> comfyBatchSizeSpinner = buildSpinner(1, 32, appSettings.comfyBatchSize());

        modelSelect = new ComboBox<>();
        modelSelect.setMaxWidth(Double.MAX_VALUE);
        modelSelect.setOnAction(event ->
        {
            String selected = modelSelect.getValue();
            if (!updating && selected != null)
            {
                actions.modelSelected(selected);
            }
        });
        refreshModelsButton = new Button("Refresh");
        refreshModelsButton.setOnAction(event -> actions.refreshModels());
        modelDetailsLabel = new Label("Model metadata has not been loaded.");
        modelDetailsLabel.setWrapText(true);

        storyCardWrappingStyleSelect = new ComboBox<>();
        storyCardWrappingStyleSelect.getItems().setAll(StoryCardWrappingStyle.values());
        storyCardWrappingStyleSelect.setValue(modelSettings.storyCardWrappingStyle());
        storyCardWrappingStyleSelect.setMaxWidth(Double.MAX_VALUE);
        storyCardWrappingStyleSelect.setTooltip(new Tooltip(
                "Wraps each Story Card only when compiling prompts for the selected model. Stored card text is unchanged."));
        storyCardWrappingStyleSelect.setOnAction(event ->
        {
            if (!updating && storyCardWrappingStyleSelect.getValue() != null)
            {
                actions.settingChanged(new Change(
                        Setting.STORY_CARD_WRAPPING_STYLE, storyCardWrappingStyleSelect.getValue()));
            }
        });

        conversationLayoutSelect = new ComboBox<>();
        conversationLayoutSelect.getItems().setAll(ConversationLayout.values());
        conversationLayoutSelect.setValue(modelSettings.conversationLayout());
        conversationLayoutSelect.setMaxWidth(Double.MAX_VALUE);
        conversationLayoutSelect.setTooltip(new Tooltip(
                "Controls how the selected model receives story history. Role-aware uses chat turns; "
                        + "Flattened sends one user document; Flattened with Prefill keeps the latest AI output "
                        + "as assistant prefill on Continue."));
        conversationLayoutSelect.setOnAction(event ->
        {
            if (!updating && conversationLayoutSelect.getValue() != null)
            {
                actions.settingChanged(new Change(
                        Setting.CONVERSATION_LAYOUT, conversationLayoutSelect.getValue()));
            }
        });

        contextLimitSlider = buildIntSlider(ModelSettings.MIN_CONTEXT_LIMIT,
                Math.max(131072, modelSettings.contextLimit()), modelSettings.contextLimit(), 512);
        contextLimitValueLabel = valueLabel(modelSettings.contextLimit(), "tokens");
        Slider ollamaKeepAliveSlider = buildIntSlider(
                AppSettings.MIN_OLLAMA_KEEP_ALIVE_MINUTES,
                AppSettings.MAX_OLLAMA_KEEP_ALIVE_MINUTES,
                appSettings.ollamaKeepAliveMinutes(),
                1);
        ollamaKeepAliveSlider.setTooltip(new Tooltip(
                "How long Ollama keeps the model loaded after each LlamaQuill chat request."));
        responseLengthSlider = buildIntSlider(1, 32768, appSettings.responseLength(), 1);
        temperatureSlider = buildDoubleSlider(0.0, 5.0, modelSettings.temperature(), 0.1);
        topKSlider = buildIntSlider(0, 10000, modelSettings.topK(), 1);
        topPSlider = buildDoubleSlider(0.0, 1.0, modelSettings.topP(), 0.01);
        minPSlider = buildDoubleSlider(0.0, 1.0, modelSettings.minP(), 0.001);
        typicalPSlider = buildDoubleSlider(0.0, 1.0, modelSettings.typicalP(), 0.01);
        presencePenaltySlider = buildDoubleSlider(-2.0, 2.0, modelSettings.presencePenalty(), 0.01);
        frequencyPenaltySlider = buildDoubleSlider(-2.0, 2.0, modelSettings.frequencyPenalty(), 0.01);
        repeatLastNSlider = buildIntSlider(-1, modelSettings.contextLimit(), modelSettings.repeatLastN(), 1);
        repetitionPenaltySlider = buildDoubleSlider(0.0, 5.0, modelSettings.repetitionPenalty(), 0.01);
        responseLengthEnabledBox = optionCheckBox("Response Length", appSettings.responseLengthEnabled());
        responseLengthEnabledBox.setTooltip(new Tooltip(
                "When disabled, Ollama chooses the response length and LlamaQuill reserves 200 context tokens for output."));
        temperatureEnabledBox = optionCheckBox("Temperature", modelSettings.temperatureEnabled());
        topKEnabledBox = optionCheckBox("Top K", modelSettings.topKEnabled());
        topPEnabledBox = optionCheckBox("Top P", modelSettings.topPEnabled());
        minPEnabledBox = optionCheckBox("Min P", modelSettings.minPEnabled());
        typicalPEnabledBox = optionCheckBox("Typical P", modelSettings.typicalPEnabled());
        typicalPEnabledBox.setTooltip(new Tooltip(
                "Locally typical sampling. 1.0 has no effect; disabling leaves the value to Ollama and the model."));
        presencePenaltyEnabledBox = optionCheckBox("Presence Penalty", modelSettings.presencePenaltyEnabled());
        frequencyPenaltyEnabledBox = optionCheckBox("Frequency Penalty", modelSettings.frequencyPenaltyEnabled());
        repeatLastNEnabledBox = optionCheckBox("Repeat Last N", modelSettings.repeatLastNEnabled());
        repeatLastNEnabledBox.setTooltip(new Tooltip(
                "Tokens checked by repetition penalty. 0 disables the lookback; -1 uses the full context."));
        repetitionPenaltyEnabledBox = optionCheckBox(
                "Repetition Penalty", modelSettings.repetitionPenaltyEnabled());
        Slider minStoryPercentSlider = buildIntSlider(10, 100, appSettings.minStoryPercent(), 1);
        Spinner<Integer> storyCardLookbackSpinner = buildSpinner(0, 100, appSettings.storyCardLookback());

        Label databaseLocationLabel = new Label(databasePath.toString());
        databaseLocationLabel.setWrapText(true);
        Button backupDatabaseButton = fullWidthButton("Back Up Database", actions::backupDatabase);
        Button checkDatabaseButton = fullWidthButton("Check Database", actions::checkDatabase);

        content.getChildren().addAll(
                textFieldRow("Ollama URL", ollamaUrlField),
                sliderRow("Ollama Model Keep Alive", ollamaKeepAliveSlider,
                        valueLabel(appSettings.ollamaKeepAliveMinutes(), "minutes"),
                        Setting.OLLAMA_KEEP_ALIVE_MINUTES),
                modelSelectorRow(),
                modelDetailsLabel,
                sliderRow("Context Limit", contextLimitSlider, contextLimitValueLabel, Setting.CONTEXT_LIMIT),
                optionalSliderRow(responseLengthEnabledBox, responseLengthSlider,
                        valueLabel(appSettings.responseLength(), "tokens"),
                        Setting.RESPONSE_LENGTH, Setting.RESPONSE_LENGTH_ENABLED),
                optionalSliderRow(temperatureEnabledBox, temperatureSlider,
                        valueLabel(modelSettings.temperature(), "", 2),
                        Setting.TEMPERATURE, Setting.TEMPERATURE_ENABLED),
                optionalSliderRow(topKEnabledBox, topKSlider, valueLabel(modelSettings.topK(), ""),
                        Setting.TOP_K, Setting.TOP_K_ENABLED),
                optionalSliderRow(topPEnabledBox, topPSlider, valueLabel(modelSettings.topP(), "", 2),
                        Setting.TOP_P, Setting.TOP_P_ENABLED),
                optionalSliderRow(minPEnabledBox, minPSlider, valueLabel(modelSettings.minP(), "", 3),
                        Setting.MIN_P, Setting.MIN_P_ENABLED),
                optionalSliderRow(typicalPEnabledBox, typicalPSlider,
                        valueLabel(modelSettings.typicalP(), "", 2),
                        Setting.TYPICAL_P, Setting.TYPICAL_P_ENABLED),
                optionalSliderRow(presencePenaltyEnabledBox, presencePenaltySlider,
                        valueLabel(modelSettings.presencePenalty(), "", 2),
                        Setting.PRESENCE_PENALTY, Setting.PRESENCE_PENALTY_ENABLED),
                optionalSliderRow(frequencyPenaltyEnabledBox, frequencyPenaltySlider,
                        valueLabel(modelSettings.frequencyPenalty(), "", 2),
                        Setting.FREQUENCY_PENALTY, Setting.FREQUENCY_PENALTY_ENABLED),
                optionalSliderRow(repeatLastNEnabledBox, repeatLastNSlider,
                        valueLabel(modelSettings.repeatLastN(), ""),
                        Setting.REPEAT_LAST_N, Setting.REPEAT_LAST_N_ENABLED),
                optionalSliderRow(repetitionPenaltyEnabledBox, repetitionPenaltySlider,
                        valueLabel(modelSettings.repetitionPenalty(), "", 2),
                        Setting.REPETITION_PENALTY, Setting.REPETITION_PENALTY_ENABLED),
                sliderRow("Context to Use for Story", minStoryPercentSlider,
                        valueLabel(appSettings.minStoryPercent(), "%"), Setting.MIN_STORY_PERCENT),
                spinnerRow("Story Card Look Back", storyCardLookbackSpinner, Setting.STORY_CARD_LOOKBACK),
                comboRow("Story Card Wrapping Style", storyCardWrappingStyleSelect),
                comboRow("Conversation Layout", conversationLayoutSelect),
                underlinedLabel("Image Generation"),
                textFieldRow("ComfyUI URL", comfyUiUrlField),
                comboRow("ComfyUI Workflow", comfyWorkflowSelect),
                spinnerRow("Image Width", comfyWidthSpinner, Setting.COMFY_WIDTH),
                spinnerRow("Image Height", comfyHeightSpinner, Setting.COMFY_HEIGHT),
                spinnerRow("Image Batch Size", comfyBatchSizeSpinner, Setting.COMFY_BATCH_SIZE),
                underlinedLabel("Local Data"),
                new Label("Database"),
                databaseLocationLabel,
                backupDatabaseButton,
                checkDatabaseButton);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        tab = new Tab("Options", scrollPane);
    }

    public Tab tab()
    {
        return tab;
    }

    public void setModels(List<String> names, String selectedModel)
    {
        updating = true;
        try
        {
            List<String> available = names == null ? List.of() : List.copyOf(names);
            modelSelect.setItems(FXCollections.observableArrayList(available));
            modelSelect.setValue(available.contains(selectedModel) ? selectedModel : null);
        }
        finally
        {
            updating = false;
        }
    }

    public void setRefreshInProgress(boolean refreshInProgress)
    {
        refreshModelsButton.setDisable(refreshInProgress);
    }

    public void setModelDetails(String text)
    {
        modelDetailsLabel.setText(text == null ? "" : text);
    }

    public void setOllamaUrl(String value)
    {
        ollamaUrlField.setText(value == null ? "" : value);
    }

    public void setComfyUiUrl(String value)
    {
        comfyUiUrlField.setText(value == null ? "" : value);
    }

    public void setComfyWorkflow(String value)
    {
        updating = true;
        try
        {
            comfyWorkflowSelect.setValue(value);
        }
        finally
        {
            updating = false;
        }
    }

    public void applyModelSettings(ModelSettings settings, OllamaModelDetails details)
    {
        if (settings == null)
        {
            return;
        }
        updating = true;
        try
        {
            int sliderMaximum = details != null && details.maxContextLength() > 0
                    ? Math.min(ModelSettings.MAX_CONTEXT_LIMIT, details.maxContextLength())
                    : Math.max(131072, settings.contextLimit());
            sliderMaximum = Math.max(ModelSettings.MIN_CONTEXT_LIMIT, sliderMaximum);
            contextLimitSlider.setMax(sliderMaximum);
            contextLimitSlider.setValue(Math.min(settings.contextLimit(), sliderMaximum));
            contextLimitValueLabel.setText(formatValue(settings.contextLimit(), "tokens"));
            temperatureSlider.setValue(settings.temperature());
            topKSlider.setValue(settings.topK());
            topPSlider.setValue(settings.topP());
            minPSlider.setValue(settings.minP());
            typicalPSlider.setValue(settings.typicalP());
            presencePenaltySlider.setValue(settings.presencePenalty());
            frequencyPenaltySlider.setValue(settings.frequencyPenalty());
            repeatLastNSlider.setMax(settings.contextLimit());
            repeatLastNSlider.setValue(settings.repeatLastN());
            repetitionPenaltySlider.setValue(settings.repetitionPenalty());
            temperatureEnabledBox.setSelected(settings.temperatureEnabled());
            topKEnabledBox.setSelected(settings.topKEnabled());
            topPEnabledBox.setSelected(settings.topPEnabled());
            minPEnabledBox.setSelected(settings.minPEnabled());
            typicalPEnabledBox.setSelected(settings.typicalPEnabled());
            presencePenaltyEnabledBox.setSelected(settings.presencePenaltyEnabled());
            frequencyPenaltyEnabledBox.setSelected(settings.frequencyPenaltyEnabled());
            repeatLastNEnabledBox.setSelected(settings.repeatLastNEnabled());
            repetitionPenaltyEnabledBox.setSelected(settings.repetitionPenaltyEnabled());
            storyCardWrappingStyleSelect.setValue(settings.storyCardWrappingStyle());
            conversationLayoutSelect.setValue(settings.conversationLayout());
        }
        finally
        {
            updating = false;
        }
    }

    private VBox sliderRow(String labelText, Slider slider, Label valueLabel, Setting setting)
    {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, new Label(labelText), spacer, valueLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(6, header, slider);
        slider.valueProperty().addListener((obs, oldValue, newValue) ->
        {
            valueLabel.setText(formatValue(newValue, valueLabel));
            if (!updating)
            {
                actions.settingChanged(new Change(setting, newValue));
            }
        });
        return box;
    }

    private VBox optionalSliderRow(CheckBox enabledBox, Slider slider, Label valueLabel,
            Setting valueSetting, Setting enabledSetting)
    {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, enabledBox, spacer, valueLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(6, header, slider);
        slider.disableProperty().bind(enabledBox.selectedProperty().not());
        valueLabel.disableProperty().bind(enabledBox.selectedProperty().not());
        slider.valueProperty().addListener((obs, oldValue, newValue) ->
        {
            valueLabel.setText(formatValue(newValue, valueLabel));
            if (!updating)
            {
                actions.settingChanged(new Change(valueSetting, newValue));
            }
        });
        enabledBox.setOnAction(event ->
        {
            if (!updating)
            {
                actions.settingChanged(new Change(enabledSetting, enabledBox.isSelected()));
            }
        });
        return box;
    }

    private VBox modelSelectorRow()
    {
        HBox row = new HBox(8, modelSelect, refreshModelsButton);
        HBox.setHgrow(modelSelect, Priority.ALWAYS);
        return new VBox(6, new Label("Model"), row);
    }

    private VBox spinnerRow(String labelText, Spinner<Integer> spinner, Setting setting)
    {
        spinner.valueProperty().addListener((obs, oldValue, newValue) ->
        {
            if (!updating)
            {
                actions.settingChanged(new Change(setting, newValue));
            }
        });
        return new VBox(6, new Label(labelText), spinner);
    }

    private static VBox textFieldRow(String labelText, TextField field)
    {
        return new VBox(6, new Label(labelText), field);
    }

    private static VBox comboRow(String labelText, ComboBox<?> comboBox)
    {
        return new VBox(6, new Label(labelText), comboBox);
    }

    private static Slider buildIntSlider(int min, int max, int value, int step)
    {
        Slider slider = new Slider(min, max, value);
        slider.setShowTickMarks(false);
        slider.setShowTickLabels(false);
        slider.setBlockIncrement(step);
        slider.setSnapToTicks(true);
        slider.setMajorTickUnit(step);
        slider.setMinorTickCount(0);
        slider.setOrientation(Orientation.HORIZONTAL);
        return slider;
    }

    private static Slider buildDoubleSlider(double min, double max, double value, double step)
    {
        Slider slider = new Slider(min, max, value);
        slider.setShowTickMarks(false);
        slider.setShowTickLabels(false);
        slider.setBlockIncrement(step);
        slider.setSnapToTicks(true);
        slider.setMajorTickUnit(step);
        slider.setMinorTickCount(0);
        slider.setOrientation(Orientation.HORIZONTAL);
        return slider;
    }

    private static Spinner<Integer> buildSpinner(int min, int max, int value)
    {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, value));
        spinner.setEditable(true);
        spinner.setMaxWidth(Double.MAX_VALUE);
        return spinner;
    }

    private static CheckBox optionCheckBox(String labelText, boolean selected)
    {
        CheckBox checkBox = new CheckBox(labelText);
        checkBox.setSelected(selected);
        return checkBox;
    }

    private static Label valueLabel(double value, String suffix)
    {
        return new Label(formatValue(value, suffix));
    }

    private static Label valueLabel(double value, String suffix, int decimals)
    {
        Label label = new Label(formatValue(value, suffix, decimals));
        label.setUserData(decimals);
        return label;
    }

    private static Label underlinedLabel(String text)
    {
        Label label = new Label(text);
        label.setUnderline(true);
        return label;
    }

    private static Button fullWidthButton(String label, Runnable action)
    {
        Button button = new Button(label);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static String formatValue(Number value, Label label)
    {
        String text = label.getText();
        String suffix = "";
        int space = text.lastIndexOf(' ');
        if (space > -1)
        {
            suffix = text.substring(space + 1);
        }
        int decimals = label.getUserData() instanceof Integer count ? count : 2;
        return formatValue(value.doubleValue(), suffix, decimals);
    }

    private static String formatValue(double value, String suffix)
    {
        return formatValue(value, suffix, 2);
    }

    private static String formatValue(double value, String suffix, int decimals)
    {
        String formatted;
        if (Math.abs(value - Math.round(value)) < 0.0001)
        {
            formatted = String.valueOf((int) Math.round(value));
        }
        else
        {
            formatted = String.format(Locale.US, "%." + Math.max(0, decimals) + "f", value);
        }
        return suffix == null || suffix.isEmpty() ? formatted : formatted + " " + suffix;
    }

    public enum Setting
    {
        OLLAMA_URL,
        OLLAMA_KEEP_ALIVE_MINUTES,
        CONTEXT_LIMIT,
        STORY_CARD_WRAPPING_STYLE,
        CONVERSATION_LAYOUT,
        RESPONSE_LENGTH,
        RESPONSE_LENGTH_ENABLED,
        TEMPERATURE,
        TEMPERATURE_ENABLED,
        TOP_K,
        TOP_K_ENABLED,
        TOP_P,
        TOP_P_ENABLED,
        MIN_P,
        MIN_P_ENABLED,
        TYPICAL_P,
        TYPICAL_P_ENABLED,
        PRESENCE_PENALTY,
        PRESENCE_PENALTY_ENABLED,
        FREQUENCY_PENALTY,
        FREQUENCY_PENALTY_ENABLED,
        REPEAT_LAST_N,
        REPEAT_LAST_N_ENABLED,
        REPETITION_PENALTY,
        REPETITION_PENALTY_ENABLED,
        MIN_STORY_PERCENT,
        STORY_CARD_LOOKBACK,
        COMFY_UI_URL,
        COMFY_WORKFLOW,
        COMFY_WIDTH,
        COMFY_HEIGHT,
        COMFY_BATCH_SIZE
    }

    public record Change(Setting setting, Object value)
    {
        public Change
        {
            Objects.requireNonNull(setting, "setting");
        }

        public int intValue()
        {
            return ((Number) value).intValue();
        }

        public double doubleValue()
        {
            return ((Number) value).doubleValue();
        }

        public boolean booleanValue()
        {
            return (Boolean) value;
        }

        public String stringValue()
        {
            return value == null ? "" : value.toString();
        }
    }

    public interface Actions
    {
        void settingChanged(Change change);

        void modelSelected(String modelName);

        void refreshModels();

        void backupDatabase();

        void checkDatabase();
    }
}
