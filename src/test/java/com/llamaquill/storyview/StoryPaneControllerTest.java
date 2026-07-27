package com.llamaquill.storyview;

import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.geometry.Bounds;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryPaneControllerTest
{
    @BeforeAll
    static void startJavaFx() throws Exception
    {
        CountDownLatch started = new CountDownLatch(1);
        try
        {
            Platform.startup(started::countDown);
        }
        catch (IllegalStateException alreadyStarted)
        {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS), "JavaFX did not start.");
        runOnFxThread(() -> Platform.setImplicitExit(false));
    }

    @AfterAll
    static void stopJavaFx()
    {
        Platform.exit();
    }

    @Test
    void assistantEditorSurvivesTheFollowingJavaFxTurn() throws Exception
    {
        EditorFixture fixture = createFixture();
        try
        {
            runOnFxThread(() ->
            {
                Text assistant = findText(fixture.root(), "Assistant prose.");
                assertNotNull(assistant);
                fireClick(assistant);
            });
            runOnFxThread(() -> assertVisibleEditor(fixture.root(), "Assistant prose."));
        }
        finally
        {
            runOnFxThread(fixture.stage()::close);
        }
    }

    @Test
    void userEditorSurvivesTheFollowingJavaFxTurn() throws Exception
    {
        EditorFixture fixture = createFixture();
        try
        {
            runOnFxThread(() ->
            {
                Label user = findLabel(fixture.root(), "User action.");
                assertNotNull(user);
                fireClick(user);
            });
            runOnFxThread(() -> assertVisibleEditor(fixture.root(), "User action."));
        }
        finally
        {
            runOnFxThread(fixture.stage()::close);
        }
    }

    @Test
    void oneClickMovesTheSingleEditorBetweenBlocks() throws Exception
    {
        EditorFixture fixture = createFixture();
        try
        {
            runOnFxThread(() -> fireClick(findText(fixture.root(), "Assistant prose.")));
            runOnFxThread(() ->
            {
                assertVisibleEditor(fixture.root(), "Assistant prose.");
                Label user = findLabel(fixture.root(), "User action.");
                assertNotNull(user);
                fireClick(user);
            });
            runOnFxThread(() ->
            {
                assertVisibleEditor(fixture.root(), "User action.");
                assertTrue(fixture.root().lookupAll(".story-block-editor").size() == 1,
                        "More than one story editor is attached after a one-click switch.");
            });
        }
        finally
        {
            runOnFxThread(fixture.stage()::close);
        }
    }

    @Test
    void oneFocusChangeCommitsAndClosesTheEditor() throws Exception
    {
        EditorFixture fixture = createFixture();
        try
        {
            runOnFxThread(() -> fireClick(findText(fixture.root(), "Assistant prose.")));
            runOnFxThread(() ->
            {
                TextArea editor = assertVisibleEditor(fixture.root(), "Assistant prose.");
                editor.setText("Edited assistant prose.");
                ListView<?> storyList = findNode(fixture.root(), ListView.class, ignored -> true);
                assertNotNull(storyList);
                fireMouseEvent(storyList, MouseEvent.MOUSE_PRESSED);
            });
            runOnFxThread(() -> { });
            runOnFxThread(() ->
            {
                assertTrue(fixture.root().lookupAll(".story-block-editor").isEmpty(),
                        "Story editor remained attached after losing focus.");
                assertNotNull(findText(fixture.root(), "Edited assistant prose."),
                        "Committed text was not restored to the story span.");
            });
        }
        finally
        {
            runOnFxThread(fixture.stage()::close);
        }
    }

    @Test
    void longEditorExpandsWithoutAnInternalScrollBar() throws Exception
    {
        String longText = ("The editor should expose every wrapped line without creating its own scrolling region. ")
                .repeat(45);
        EditorFixture fixture = createFixture(List.of(
                new Block("assistant", "story", Role.ASSISTANT, longText, "", 0)));
        try
        {
            runOnFxThread(() -> fireClick(findText(fixture.root(), longText)));
            runOnFxThread(() ->
            {
                TextArea editor = assertVisibleEditor(fixture.root(), longText);
                assertTrue(editor.getHeight() > 300,
                        "Long editor did not expand to its wrapped content: " + editor.getHeight());
                for (Node node : editor.lookupAll(".scroll-bar"))
                {
                    if (node instanceof ScrollBar scrollBar)
                    {
                        assertFalse(scrollBar.isVisible() && scrollBar.getOpacity() > 0,
                                "A story editor exposed an internal scroll bar.");
                    }
                }
            });
        }
        finally
        {
            runOnFxThread(fixture.stage()::close);
        }
    }

    @Test
    void openingAPastEditorDoesNotSnapTheStoryToTheEnd() throws Exception
    {
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < 30; i++)
        {
            blocks.add(new Block("user-" + i, "story", Role.USER,
                    "User action " + i + " with enough text to occupy a visible row.", "", i));
        }
        EditorFixture fixture = createFixture(blocks);
        try
        {
            runOnFxThread(() ->
            {
                ListView<?> list = findNode(fixture.root(), ListView.class, ignored -> true);
                assertNotNull(list);
                list.scrollTo(10);
                fixture.root().applyCss();
                fixture.root().layout();
                Label past = findLabel(fixture.root(), blocks.get(10).text());
                assertNotNull(past);
                fireClick(past);
            });
            runOnFxThread(() ->
            {
                ScrollBar vertical = findNode(fixture.root(), ScrollBar.class,
                        bar -> bar.getOrientation() == javafx.geometry.Orientation.VERTICAL
                                && enclosingList(bar) != null);
                assertNotNull(vertical);
                assertTrue(vertical.getValue() < vertical.getMax() - 0.01,
                        "Opening a past editor snapped the story viewport to the end.");
            });
        }
        finally
        {
            runOnFxThread(fixture.stage()::close);
        }
    }

    @Test
    void editorSurvivesRepeatedVirtualizationPassesInALargeStory() throws Exception
    {
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < 40; i++)
        {
            Role role = i % 2 == 0 ? Role.USER : Role.ASSISTANT;
            blocks.add(new Block("block-" + i, "story", role,
                    "Virtualized " + role + " block " + i + " with wrapped manuscript text for layout.", "", i));
        }
        String targetText = blocks.get(20).text();
        EditorFixture fixture = createFixture(blocks);
        try
        {
            runOnFxThread(() ->
            {
                ListView<?> list = findNode(fixture.root(), ListView.class, ignored -> true);
                assertNotNull(list);
                list.scrollTo(20);
                fixture.root().applyCss();
                fixture.root().layout();
                Label target = findLabel(fixture.root(), targetText);
                assertNotNull(target);
                fireMouseEvent(target, MouseEvent.MOUSE_PRESSED);
            });
            for (int i = 0; i < 6; i++)
            {
                runOnFxThread(() ->
                {
                    fixture.root().applyCss();
                    fixture.root().layout();
                });
            }
            runOnFxThread(() -> assertVisibleEditor(fixture.root(), targetText));
        }
        finally
        {
            runOnFxThread(fixture.stage()::close);
        }
    }

    @Test
    void whitespaceDoesNotSelectAndExistingSelectionDoesNotInvalidateEdit() throws Exception
    {
        EditorFixture fixture = createFixture();
        try
        {
            runOnFxThread(() ->
            {
                Text assistant = findText(fixture.root(), "Assistant prose.");
                assertNotNull(assistant);
                ListCell<?> cell = enclosingCell(assistant);
                assertNotNull(cell);
                Node whitespace = assistant.getParent().getParent();
                fireClick(whitespace);
                assertTrue(cell.getListView().getSelectionModel().isEmpty(),
                        "Assistant whitespace selected a virtualized story cell.");

                cell.getListView().getSelectionModel().select(cell.getIndex());
                fixture.root().applyCss();
                fixture.root().layout();
                Text selectedAssistant = findText(fixture.root(), "Assistant prose.");
                assertNotNull(selectedAssistant);
                fireMouseEvent(selectedAssistant, MouseEvent.MOUSE_PRESSED);
            });
            for (int i = 0; i < 4; i++)
            {
                runOnFxThread(() ->
                {
                    fixture.root().applyCss();
                    fixture.root().layout();
                });
            }
            runOnFxThread(() -> assertVisibleEditor(fixture.root(), "Assistant prose."));
        }
        finally
        {
            runOnFxThread(fixture.stage()::close);
        }
    }

    @Test
    void olderAssistantSpanUsesADashedHoverUnderline() throws Exception
    {
        EditorFixture fixture = createFixture();
        try
        {
            runOnFxThread(() ->
            {
                Text older = findText(fixture.root(), "Older prose. ");
                assertNotNull(older);
                fireMouseEvent(older, MouseEvent.MOUSE_ENTERED);
                Path underline = findNode(fixture.root(), Path.class,
                        path -> path.isVisible() && !path.getElements().isEmpty()
                                && !path.getStrokeDashArray().isEmpty());
                assertNotNull(underline, "No dashed hover underline was rendered.");

                Text latest = findText(fixture.root(), "Assistant prose.");
                assertNotNull(latest);
                assertTrue(latest.isUnderline(), "Latest assistant span lost its solid underline.");
                Path latestBackground = findNode(fixture.root(), Path.class,
                        path -> path.isVisible() && !path.getElements().isEmpty()
                                && path.getStrokeDashArray().isEmpty()
                                && path.getFill() instanceof Color color
                                && color.getOpacity() > 0);
                assertNotNull(latestBackground, "Latest assistant span lost its background highlight.");
                assertTrue(latestBackground.getBoundsInParent().getWidth()
                                <= latest.getBoundsInParent().getWidth() + 5,
                        "Latest assistant highlight includes an earlier assistant span.");
                fireMouseEvent(latest, MouseEvent.MOUSE_ENTERED);
                Path staleUnderline = findNode(fixture.root(), Path.class,
                        path -> path.isVisible() && !path.getElements().isEmpty()
                                && !path.getStrokeDashArray().isEmpty());
                assertFalse(staleUnderline != null,
                        "Latest assistant hover retained another span's dashed underline.");
            });
        }
        finally
        {
            runOnFxThread(fixture.stage()::close);
        }
    }

    private EditorFixture createFixture() throws Exception
    {
        return createFixture(List.of(
                new Block("older", "story", Role.ASSISTANT, "Older prose. ", "", 0),
                new Block("assistant", "story", Role.ASSISTANT, "Assistant prose.", "", 1),
                new Block("user", "story", Role.USER, "User action.", "", 2)));
    }

    private EditorFixture createFixture(List<Block> blocks) throws Exception
    {
        AtomicReference<EditorFixture> result = new AtomicReference<>();
        runOnFxThread(() ->
        {
            StoryPaneController controller = new StoryPaneController(
                    null,
                    () -> { },
                    ignored -> null,
                    ignored -> { },
                    (block, forceScroll) -> true,
                    (blockId, text, onSuccess, onFailure) -> onSuccess.run(),
                    (message, error) ->
                    {
                        throw new AssertionError(message, error);
                    });
            BorderPane root = controller.buildCenterPane(
                    new Button(), new Button(), new Button(), new Button(), new Button(), new Button(), new Button());
            Stage stage = new Stage();
            stage.setX(-10_000);
            stage.setY(-10_000);
            stage.setOpacity(0);
            Scene scene = new Scene(root, 800, 600);
            scene.getStylesheets().add(StoryPaneControllerTest.class.getResource("/styles.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
            controller.renderBlocks(blocks, false);
            root.resize(800, 600);
            root.applyCss();
            root.layout();
            result.set(new EditorFixture(root, stage));
        });
        return result.get();
    }

    private static TextArea assertVisibleEditor(Parent root, String text)
    {
        TextArea editor = findNode(root, TextArea.class, node -> text.equals(node.getText()));
        assertNotNull(editor, "No editor was created for " + text);
        assertTrue(editor.isVisible(), "Editor is not visible.");
        assertTrue(editor.isManaged(), "Editor is not managed.");
        editor.applyCss();
        Bounds editorBounds = editor.localToScene(editor.getBoundsInLocal());
        ListCell<?> cell = enclosingCell(editor);
        assertNotNull(cell, "Editor is not inside a story cell.");
        Bounds cellBounds = cell.localToScene(cell.getBoundsInLocal());
        String geometry = "editor=" + editorBounds + ", prefWidth=" + editor.prefWidth(-1)
                + ", cell=" + cellBounds + ", list=" + cell.getListView().localToScene(
                        cell.getListView().getBoundsInLocal()) + ", parents=" + parentGeometry(editor);
        assertTrue(editorBounds.getWidth() > 20, "Editor has no visible width: " + geometry);
        assertTrue(editorBounds.getHeight() > 20, "Editor has no visible height: " + geometry);
        assertTrue(cellBounds.intersects(editorBounds),
                "Editor lies outside its visible story cell. " + geometry);
        assertTrue(cellBounds.getMaxY() >= editorBounds.getMaxY() - 1,
                "Editor is clipped by the story cell. " + geometry);
        return editor;
    }

    private static String parentGeometry(Node node)
    {
        StringBuilder result = new StringBuilder();
        Parent parent = node.getParent();
        while (parent != null && !(parent instanceof ListCell<?>))
        {
            result.append(parent.getClass().getSimpleName())
                    .append('=')
                    .append(parent.localToScene(parent.getBoundsInLocal()))
                    .append(';');
            parent = parent.getParent();
        }
        return result.toString();
    }

    private static Text findText(Parent root, String value)
    {
        return findNode(root, Text.class, node -> value.equals(node.getText()));
    }

    private static Label findLabel(Parent root, String value)
    {
        return findNode(root, Label.class, node -> value.equals(node.getText()));
    }

    private static ListCell<?> enclosingCell(Node node)
    {
        Parent parent = node.getParent();
        while (parent != null)
        {
            if (parent instanceof ListCell<?> cell)
            {
                return cell;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private static ListView<?> enclosingList(Node node)
    {
        Parent parent = node.getParent();
        while (parent != null)
        {
            if (parent instanceof ListView<?> list)
            {
                return list;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private static <T extends Node> T findNode(Parent root, Class<T> type,
            java.util.function.Predicate<T> predicate)
    {
        for (Node child : root.getChildrenUnmodifiable())
        {
            if (type.isInstance(child))
            {
                T candidate = type.cast(child);
                if (predicate.test(candidate))
                {
                    return candidate;
                }
            }
            if (child instanceof Parent parent)
            {
                T nested = findNode(parent, type, predicate);
                if (nested != null)
                {
                    return nested;
                }
            }
        }
        return null;
    }

    private static void fireClick(Node node)
    {
        fireMouseEvent(node, MouseEvent.MOUSE_PRESSED);
        fireMouseEvent(node, MouseEvent.MOUSE_RELEASED);
        fireMouseEvent(node, MouseEvent.MOUSE_CLICKED);
    }

    private static void fireMouseEvent(Node node, javafx.event.EventType<MouseEvent> eventType)
    {
        MouseEvent event = new MouseEvent(
                eventType,
                0, 0, 0, 0,
                MouseButton.PRIMARY,
                eventType == MouseEvent.MOUSE_CLICKED ? 1 : 0,
                false, false, false, false,
                true, false, false,
                true, false, false,
                null);
        node.fireEvent(event);
    }

    private static void runOnFxThread(Runnable action) throws Exception
    {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Platform.runLater(() ->
        {
            try
            {
                action.run();
            }
            catch (Throwable error)
            {
                failure.set(error);
            }
            finally
            {
                completed.countDown();
            }
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS), "JavaFX action timed out.");
        if (failure.get() instanceof Exception exception)
        {
            throw exception;
        }
        if (failure.get() instanceof Error error)
        {
            throw error;
        }
    }

    private record EditorFixture(BorderPane root, Stage stage)
    {
    }
}
