package com.llamaquill.generation;

import com.llamaquill.model.ChatMessage;
import com.llamaquill.serviceClients.OllamaChatRequestSnapshot;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class LastContextDialog
{
    private LastContextDialog()
    {
    }

    public static void show(Stage owner, OllamaChatRequestSnapshot snapshot)
    {
        if (snapshot == null)
        {
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Last Ollama Context");
        dialog.setHeaderText("Most recent message list submitted to Ollama");
        dialog.initOwner(owner);
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextArea contextArea = new TextArea(format(snapshot));
        contextArea.setEditable(false);
        contextArea.setWrapText(true);
        contextArea.setPrefColumnCount(100);
        contextArea.setPrefRowCount(34);

        VBox content = new VBox(8, contextArea);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(900, 700);
        dialog.showAndWait();
    }

    static String format(OllamaChatRequestSnapshot snapshot)
    {
        StringBuilder text = new StringBuilder()
                .append("Model: ").append(snapshot.model()).append('\n')
                .append("Endpoint: ").append(snapshot.endpoint()).append('\n')
                .append("Streaming: ").append(snapshot.streaming()).append("\n\n")
                .append("Role headings below are viewer-only and were not added to message content.\n");

        int index = 1;
        for (ChatMessage message : snapshot.messages())
        {
            text.append("\n===== MESSAGE ").append(index++)
                    .append(": ").append(message.role().toUpperCase()).append(" =====\n")
                    .append(message.content()).append('\n');
        }
        return text.toString();
    }
}
