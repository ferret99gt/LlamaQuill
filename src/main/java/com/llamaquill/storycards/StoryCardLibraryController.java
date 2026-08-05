package com.llamaquill.storycards;

import com.llamaquill.model.StoryCard;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class StoryCardLibraryController
{
    private final ObservableList<StoryCard> cards = FXCollections.observableArrayList();
    private final ListView<StoryCard> cardList = new ListView<>(cards);
    private final CheckBox forcePinAllBox = new CheckBox("Force pin all cards");
    private final VBox content;
    private final Tab tab;

    public StoryCardLibraryController(Runnable onNewCard, Runnable onImportCards,
            Consumer<StoryCard> onOpenCard, Consumer<Boolean> onForcePinAllChanged)
    {
        Objects.requireNonNull(onNewCard, "onNewCard");
        Objects.requireNonNull(onImportCards, "onImportCards");
        Objects.requireNonNull(onOpenCard, "onOpenCard");
        Objects.requireNonNull(onForcePinAllChanged, "onForcePinAllChanged");

        Button newCardButton = new Button("Create New Card");
        newCardButton.setMaxWidth(Double.MAX_VALUE);
        newCardButton.setOnAction(event -> onNewCard.run());

        Button importCardsButton = new Button("Import AI Dungeon Cards");
        importCardsButton.setMaxWidth(Double.MAX_VALUE);
        importCardsButton.setOnAction(event -> onImportCards.run());

        forcePinAllBox.setTooltip(new Tooltip(
                "Treat every Story Card as pinned during prompt compilation without changing individual Pinned values."));
        forcePinAllBox.setOnAction(event -> onForcePinAllChanged.accept(forcePinAllBox.isSelected()));

        cardList.setCellFactory(list -> new CardCell());
        cardList.setOnMouseClicked(event ->
        {
            if (event.getClickCount() == 1)
            {
                StoryCard selected = cardList.getSelectionModel().getSelectedItem();
                if (selected != null)
                {
                    onOpenCard.accept(selected);
                }
            }
        });

        content = new VBox(8, newCardButton, cardList, forcePinAllBox, importCardsButton);
        content.setPadding(new Insets(10));
        VBox.setVgrow(cardList, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        tab = new Tab("Story Cards", scrollPane);
    }

    public Tab tab()
    {
        return tab;
    }

    public void setCards(List<StoryCard> updatedCards)
    {
        cards.setAll(updatedCards == null ? List.of() : updatedCards);
    }

    public void clear()
    {
        cards.clear();
        forcePinAllBox.setSelected(false);
    }

    public void setForcePinAll(boolean forcePinAll)
    {
        forcePinAllBox.setSelected(forcePinAll);
    }

    public void setEnabled(boolean enabled)
    {
        content.setDisable(!enabled);
    }

    private static String snippetFor(String text)
    {
        if (text == null || text.isBlank())
        {
            return "";
        }
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() <= 80 ? single : single.substring(0, 77) + "...";
    }

    private final class CardCell extends ListCell<StoryCard>
    {
        private final Label title = new Label();
        private final Label snippet = new Label();
        private final Label group = new Label();
        private final VBox box = new VBox(2, group, title, snippet);

        private CardCell()
        {
            snippet.setStyle("-fx-font-size: 11px;");
            group.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 8 0 4 0;");
        }

        @Override
        protected void updateItem(StoryCard item, boolean empty)
        {
            super.updateItem(item, empty);
            if (empty || item == null)
            {
                setGraphic(null);
                return;
            }

            String type = item.displayType();
            int index = getIndex();
            boolean firstInGroup = index <= 0 || index > cards.size() - 1
                    || !cards.get(index - 1).displayType().equalsIgnoreCase(type);
            group.setVisible(firstInGroup);
            group.setManaged(firstInGroup);
            if (firstInGroup)
            {
                long count = cards.stream()
                        .filter(candidate -> candidate.displayType().equalsIgnoreCase(type))
                        .count();
                group.setText(type + "  " + count);
            }
            title.setText((item.pinned() ? "\uD83D\uDCCC " : "") + item.title());
            snippet.setText(snippetFor(item.content()));
            setGraphic(box);
        }
    }
}
