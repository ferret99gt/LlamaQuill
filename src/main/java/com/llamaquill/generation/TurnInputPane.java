package com.llamaquill.generation;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class TurnInputPane
{
    private final TextArea inputArea;
    private final VBox root;

    public TurnInputPane(Runnable onSubmit, Runnable onCancel)
    {
        inputArea = new TextArea();
        inputArea.setWrapText(true);
        inputArea.setPrefRowCount(4);

        Button submitButton = new Button("Submit");
        submitButton.setOnAction(event -> onSubmit.run());

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> onCancel.run());

        HBox turnButtons = new HBox(8, submitButton, cancelButton);
        turnButtons.setAlignment(Pos.CENTER_RIGHT);

        root = new VBox(6, new Label("Your turn"), inputArea, turnButtons);
        root.setPadding(new Insets(10, 10, 0, 10));
        setVisible(false);
    }

    public VBox root()
    {
        return root;
    }

    public String text()
    {
        return inputArea.getText();
    }

    public void clear()
    {
        inputArea.clear();
    }

    public void setVisible(boolean show)
    {
        root.setVisible(show);
        root.setManaged(show);
        if (show)
        {
            inputArea.requestFocus();
        }
    }
}
