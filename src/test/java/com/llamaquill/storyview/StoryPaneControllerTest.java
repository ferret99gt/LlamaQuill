package com.llamaquill.storyview;

import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryImage;
import com.llamaquill.stories.StoryLibraryController;
import javafx.animation.AnimationTimer;
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
import java.util.Base64;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void storyLibraryOpensPressedStoryWhenDetailSaveRefreshesTheListBeforeClick() throws Exception
    {
        AtomicReference<Stage> stageReference = new AtomicReference<>();
        AtomicReference<Story> openedStory = new AtomicReference<>();
        try
        {
            runOnFxThread(() ->
            {
                Story active = new Story("new", "New Story", "System", "", "", "created", "updated-1");
                Story intended = new Story("old", "Older Story", "System", "Plot", "Note", "created", "updated");
                StoryLibraryController controller = new StoryLibraryController(
                        280, () -> { }, () -> { }, openedStory::set);
                controller.setStories(List.of(active, intended), active.id());

                BorderPane root = new BorderPane(controller.root());
                Stage stage = new Stage();
                stage.setX(-10_000);
                stage.setY(-10_000);
                stage.setOpacity(0);
                stage.setScene(new Scene(root, 800, 600));
                stage.show();
                root.applyCss();
                root.layout();
                stageReference.set(stage);

                ListView<?> list = findNode(root, ListView.class, ignored -> true);
                ListCell<?> intendedCell = findNode(root, ListCell.class,
                        cell -> intended.equals(cell.getItem()));
                assertNotNull(list);
                assertNotNull(intendedCell);

                fireMouseEvent(intendedCell, MouseEvent.MOUSE_PRESSED);
                list.getSelectionModel().select(1);

                Story savedActive = new Story(
                        active.id(), active.title(), "Copied instructions", "", "", active.createdAt(), "updated-2");
                controller.setStories(List.of(savedActive, intended), savedActive.id());
                assertSame(savedActive, list.getSelectionModel().getSelectedItem());

                fireMouseEvent(intendedCell, MouseEvent.MOUSE_RELEASED);
                fireMouseEvent(intendedCell, MouseEvent.MOUSE_CLICKED);

                assertSame(intended, openedStory.get(),
                        "The save-triggered refresh replaced the story selected by the user's mouse press.");
            });
        }
        finally
        {
            Stage stage = stageReference.get();
            if (stage != null)
            {
                runOnFxThread(stage::close);
            }
        }
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
    void explicitFlushCommitsAFocusedEditorBeforeShutdownOrStorySwitch() throws Exception
    {
        AtomicReference<String> persisted = new AtomicReference<>();
        EditorFixture fixture = createFixture(
                List.of(new Block("assistant", "story", Role.ASSISTANT, "Before.", "", 0)),
                (blockId, text, onSuccess, onFailure) ->
                {
                    persisted.set(blockId + ":" + text);
                    onSuccess.run();
                });
        try
        {
            runOnFxThread(() -> fireClick(findText(fixture.root(), "Before.")));
            runOnFxThread(() ->
            {
                TextArea editor = assertVisibleEditor(fixture.root(), "Before.");
                editor.setText("After.");
                fixture.controller().commitActiveEdit();
            });
            runOnFxThread(() ->
            {
                assertTrue(fixture.root().lookupAll(".story-block-editor").isEmpty());
                assertTrue("assistant:After.".equals(persisted.get()));
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
            waitForFxCondition(() ->
            {
                TextArea editor = findNode(fixture.root(), TextArea.class,
                        node -> longText.equals(node.getText()));
                return editor != null && editor.getHeight() > 300;
            }, "Long editor did not complete its wrapped-content layout.");
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
    void scrollingAboveAnImageKeepsEveryVirtualizedGraphicInsideItsCell() throws Exception
    {
        List<Block> blocks = new ArrayList<>();
        int position = 0;
        int[] importedActionLengths = { 1716, 758, 739, 692, 691 };
        for (int i = 0; i < importedActionLengths.length; i++)
        {
            String paragraph = "The imported adventure continues with descriptive prose that wraps across the pane. "
                    + "You study the room, count each breath, and hold your position at the keyhole.\n\n";
            String text = paragraph.repeat((importedActionLengths[i] / paragraph.length()) + 1)
                    .substring(0, importedActionLengths[i]);
            blocks.add(new Block("before-" + i, "story", Role.ASSISTANT, text, "", position++));
        }
        blocks.add(new Block("image-block", "story", Role.IMAGE, "image", "", position++));
        blocks.add(new Block("after-image", "story", Role.ASSISTANT,
                ("Still.\n\nYou hold the position. Eye to keyhole. Counting breaths that do not come from "
                        + "the other side. The silver hair does not stir. No sway. No shift of fabric.\n\n").repeat(5),
                "", position));

        byte[] png = squarePng();
        StoryImage image = new StoryImage("image", "story", "Test prompt", "image/png",
                1, 1, "", png, "");
        EditorFixture fixture = createFixture(
                blocks,
                (blockId, text, onSuccess, onFailure) -> onSuccess.run(),
                ignored -> image);
        try
        {
            runOnFxThread(() ->
            {
                ListView<?> list = findNode(fixture.root(), ListView.class, ignored -> true);
                assertNotNull(list);
                list.scrollTo(1);
                fixture.root().applyCss();
                fixture.root().layout();
                assertVisibleCellGraphicsAreContained(list);
            });
            runOnFxThread(() ->
            {
                ScrollBar vertical = findNode(fixture.root(), ScrollBar.class,
                        bar -> bar.getOrientation() == javafx.geometry.Orientation.VERTICAL
                                && enclosingList(bar) != null);
                assertNotNull(vertical);
                vertical.decrement();
                fixture.root().applyCss();
                fixture.root().layout();
            });
            runOnFxThread(() -> assertVisibleCellGraphicsAreContained(
                    findNode(fixture.root(), ListView.class, ignored -> true)));
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

    @Test
    void streamingReusesOneTextNodeAndKeepsPartialContinuationInOneFlow() throws Exception
    {
        String originalText = "The door";
        EditorFixture fixture = createFixture(List.of(
                new Block("assistant", "story", Role.ASSISTANT, originalText, "", 0)));
        long[] token = { 0 };
        AtomicReference<Text> initialDraftNode = new AtomicReference<>();
        AtomicReference<ListCell<?>> initialDraftCell = new AtomicReference<>();
        double[] initialDraftHeight = { 0 };
        try
        {
            runOnFxThread(() ->
            {
                token[0] = fixture.controller().startStreaming(StoryPaneController.StreamingMode.APPEND);
                fixture.controller().queueStreamingText(token[0], " opens");
                fixture.controller().renderQueuedStreamingFrame();
                fixture.root().applyCss();
                fixture.root().layout();

                Text prefix = findText(fixture.root(), originalText);
                Text draft = findText(fixture.root(), " opens");
                assertNotNull(prefix);
                assertNotNull(draft);
                assertSame(prefix.getParent(), draft.getParent(),
                        "A partial continuation was split across separate TextFlows.");
                initialDraftNode.set(draft);
                initialDraftCell.set(enclosingCell(draft));
                assertNotNull(initialDraftCell.get());
                initialDraftHeight[0] = initialDraftCell.get().getPrefHeight();
            });

            for (int i = 0; i < 40; i++)
            {
                int chunk = i;
                runOnFxThread(() ->
                {
                    fixture.controller().queueStreamingText(token[0], " " + chunk);
                    fixture.controller().renderQueuedStreamingFrame();
                });
            }

            runOnFxThread(() ->
            {
                fixture.root().applyCss();
                fixture.root().layout();
                Text updatedDraft = findText(fixture.root(),
                        " opens" + java.util.stream.IntStream.range(0, 40)
                                .mapToObj(value -> " " + value)
                                .collect(java.util.stream.Collectors.joining()));
                assertNotNull(updatedDraft);
                assertSame(initialDraftNode.get(), updatedDraft,
                        "Streaming replaced the draft Text node instead of updating it in place.");

                String streamedText = updatedDraft.getText();
                String paragraphGrowth = "\n\n"
                        + "A new paragraph adds enough prose to wrap the streaming cell across several more lines. "
                                .repeat(18);
                fixture.controller().queueStreamingText(token[0], paragraphGrowth);
                fixture.controller().renderQueuedStreamingFrame();
                fixture.root().applyCss();
                fixture.root().layout();

                Text paragraphDraft = findText(fixture.root(), streamedText + paragraphGrowth);
                assertNotNull(paragraphDraft);
                assertSame(initialDraftNode.get(), paragraphDraft,
                        "Paragraph growth replaced the streaming Text node.");
                assertSame(initialDraftCell.get(), enclosingCell(paragraphDraft),
                        "Paragraph growth recycled the active streaming cell.");
                assertTrue(enclosingCell(paragraphDraft).getPrefHeight() > initialDraftHeight[0],
                        "Paragraph growth did not expand the streaming cell.");
                assertVisibleCellGraphicsAreContained(
                        findNode(fixture.root(), ListView.class, ignored -> true));
            });
        }
        finally
        {
            runOnFxThread(() ->
            {
                fixture.controller().cancelStreaming(token[0]);
                fixture.stage().close();
            });
        }
    }

    @Test
    void streamingAfterAnEditorInteractionKeepsTheDraftAndCommittedHeadInView() throws Exception
    {
        List<Block> blocks = new ArrayList<>();
        int position = 0;
        for (int i = 0; i < 20; i++)
        {
            Role role = i % 2 == 0 ? Role.USER : Role.ASSISTANT;
            blocks.add(new Block("block-" + i, "story", role,
                    "Scrollable " + role + " block " + i
                            + " with enough manuscript text to occupy a wrapped row.",
                    "", position++));
        }
        Block tallPrefix = new Block("tall-prefix", "story", Role.ASSISTANT,
                "A long uninterrupted assistant passage keeps the final TextFlow taller than the viewport. "
                        .repeat(110),
                "", position++);
        Block edited = new Block("edited", "story", Role.ASSISTANT,
                "A non-lead assistant block near the bottom. ", "", position++);
        Block head = new Block("head", "story", Role.ASSISTANT,
                "The current head remains visible.", "", position++);
        blocks.add(tallPrefix);
        blocks.add(edited);
        blocks.add(head);

        EditorFixture fixture = createFixture(blocks);
        long[] token = { 0 };
        String streamed = "\n\nThe streamed response grows into a second paragraph while the viewport follows.";
        String fullGrowth = "\n\n"
                + "A sustained generation keeps adding prose while the final assistant span grows. ".repeat(85);
        try
        {
            runOnFxThread(() -> fixture.controller().renderBlocks(blocks, true));
            waitForFxCondition(() ->
            {
                Text target = findText(fixture.root(), edited.text());
                return target != null && isNodeInsideListViewport(fixture.root(), target);
            }, "The requested block did not settle into the viewport before editing.");
            runOnFxThread(() ->
            {
                Text target = findText(fixture.root(), edited.text());
                assertNotNull(target);
                assertNodeInsideListViewport(fixture.root(), target,
                        "The requested block was not visible before opening its editor.");
                fireClick(target);
            });
            waitForFxCondition(() ->
            {
                TextArea editor = findNode(fixture.root(), TextArea.class,
                        node -> edited.text().equals(node.getText()) && node.isVisible() && node.isManaged());
                return editor != null && isNodeInsideListViewport(fixture.root(), editor);
            }, "The edited block did not settle into the viewport.");
            runOnFxThread(() ->
            {
                TextArea editor = assertVisibleEditor(fixture.root(), edited.text());
                assertNodeInsideListViewport(fixture.root(), editor,
                        "Opening the editor moved it outside the viewport.");
                ListView<?> list = findNode(fixture.root(), ListView.class, ignored -> true);
                assertNotNull(list);
                fireMouseEvent(list, MouseEvent.MOUSE_PRESSED);
                token[0] = fixture.controller().startStreaming(StoryPaneController.StreamingMode.APPEND);
                fixture.controller().queueStreamingText(token[0], streamed);
                fixture.controller().renderQueuedStreamingFrame();
            });
            waitForFxCondition(() ->
            {
                Text draft = findText(fixture.root(), streamed);
                return draft != null && isNodeInsideListViewport(fixture.root(), draft)
                        && isStoryAtBottom(fixture.root());
            }, "Streaming draft did not settle at the bottom after the editor closed.");
            runOnFxThread(() ->
            {
                Text draft = findText(fixture.root(), streamed);
                assertNotNull(draft, "Streaming draft disappeared after the editor closed.");
                assertNodeInsideListViewport(fixture.root(), draft,
                        "Streaming draft was displaced outside the viewport.");
                assertStoryAtBottom(fixture.root());
            });

            runOnFxThread(() ->
            {
                fixture.controller().queueStreamingText(token[0], fullGrowth);
                fixture.controller().renderQueuedStreamingFrame();
            });
            waitForFxCondition(() ->
            {
                Text grownDraft = findText(fixture.root(), streamed + fullGrowth);
                return grownDraft != null && isNodeBottomInsideListViewport(fixture.root(), grownDraft);
            }, "The growing streaming response did not settle at the viewport bottom.");
            runOnFxThread(() ->
            {
                Text grownDraft = findText(fixture.root(), streamed + fullGrowth);
                assertNotNull(grownDraft);
                assertNodeBottomInsideListViewport(fixture.root(), grownDraft,
                        "A tall growing streaming response was displaced above or below the viewport.");
            });

            List<Block> committed = new ArrayList<>(blocks);
            committed.add(new Block("generated", "story", Role.ASSISTANT,
                    streamed + fullGrowth, "", position));
            runOnFxThread(() ->
            {
                fixture.controller().endStreaming(token[0]);
                fixture.controller().renderBlocks(committed, true);
            });
            waitForFxCondition(() ->
            {
                Text generated = findText(fixture.root(), streamed + fullGrowth);
                return generated != null && isNodeBottomInsideListViewport(fixture.root(), generated)
                        && isStoryAtBottom(fixture.root());
            }, "The committed response did not settle at the viewport bottom.");
            runOnFxThread(() ->
            {
                Text generated = findText(fixture.root(), streamed + fullGrowth);
                assertNotNull(generated);
                assertNodeBottomInsideListViewport(fixture.root(), generated,
                        "Committed response was replaced by stale blank cell height.");
                assertStoryAtBottom(fixture.root());
                assertVisibleCellGraphicsAreContained(
                        findNode(fixture.root(), ListView.class, ignored -> true));
            });
        }
        finally
        {
            runOnFxThread(() ->
            {
                fixture.controller().cancelStreaming(token[0]);
                fixture.stage().close();
            });
        }
    }

    @Test
    void openingTheHeadEditorAtTheBottomKeepsTheEditorInTheViewport() throws Exception
    {
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < 28; i++)
        {
            Role role = i % 2 == 0 ? Role.USER : Role.ASSISTANT;
            blocks.add(new Block("block-" + i, "story", role,
                    "Bottom-anchor " + role + " block " + i
                            + " with wrapped text for variable-height virtualization.",
                    "", i));
        }
        Block tallPrefix = new Block("tall-prefix", "story", Role.ASSISTANT,
                "A long uninterrupted assistant passage keeps the final TextFlow taller than the viewport. "
                        .repeat(110),
                "", blocks.size());
        blocks.add(tallPrefix);
        Block head = new Block("head", "story", Role.ASSISTANT,
                "The newest editable response.", "", blocks.size());
        blocks.add(head);
        EditorFixture fixture = createFixture(blocks);
        try
        {
            runOnFxThread(() -> fixture.controller().renderBlocks(blocks, true));
            waitForFxCondition(() ->
            {
                Text headText = findText(fixture.root(), head.text());
                return headText != null && isNodeInsideListViewport(fixture.root(), headText);
            }, "The newest block did not settle into the bottom viewport.");
            runOnFxThread(() ->
            {
                Text headText = findText(fixture.root(), head.text());
                assertNotNull(headText);
                assertNodeInsideListViewport(fixture.root(), headText,
                        "The tall final assistant cell was aligned to its top instead of its bottom.");
                fireClick(headText);
            });
            waitForFxCondition(() ->
            {
                TextArea editor = findNode(fixture.root(), TextArea.class,
                        node -> head.text().equals(node.getText()) && node.isVisible() && node.isManaged());
                return editor != null && isNodeInsideListViewport(fixture.root(), editor);
            }, "The newest editor did not settle into the viewport.");
            runOnFxThread(() ->
            {
                TextArea editor = assertVisibleEditor(fixture.root(), head.text());
                assertNodeInsideListViewport(fixture.root(), editor,
                        "Opening the newest editor moved it outside the viewport.");
            });
        }
        finally
        {
            runOnFxThread(fixture.stage()::close);
        }
    }

    @Test
    void retryKeepsTheOldHeadUntilTextArrivesAndRestoresItBetweenAttempts() throws Exception
    {
        String oldResponse = "The response being retried.";
        EditorFixture fixture = createFixture(List.of(
                new Block("assistant", "story", Role.ASSISTANT, oldResponse, "", 0)));
        long[] token = { 0 };
        try
        {
            runOnFxThread(() ->
            {
                token[0] = fixture.controller().startStreaming(StoryPaneController.StreamingMode.RETRY);
                fixture.root().applyCss();
                fixture.root().layout();
                assertNotNull(findText(fixture.root(), oldResponse),
                        "Retry removed the old head before replacement text arrived.");

                fixture.controller().queueStreamingText(token[0], "A new response.");
                fixture.controller().renderQueuedStreamingFrame();
                fixture.root().applyCss();
                fixture.root().layout();
                assertNull(findText(fixture.root(), oldResponse),
                        "Retry retained the old head after replacement text arrived.");
                assertNotNull(findText(fixture.root(), "A new response."));
            });

            runOnFxThread(() ->
            {
                fixture.controller().queueStreamingAttempt(token[0], "");
                fixture.controller().renderQueuedStreamingFrame();
                fixture.root().applyCss();
                fixture.root().layout();
                assertNotNull(findText(fixture.root(), oldResponse),
                        "Starting another attempt did not restore the original retry head.");
                assertNull(findText(fixture.root(), "A new response."));
            });
        }
        finally
        {
            runOnFxThread(() ->
            {
                fixture.controller().cancelStreaming(token[0]);
                fixture.stage().close();
            });
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
        return createFixture(blocks, (blockId, text, onSuccess, onFailure) -> onSuccess.run());
    }

    private EditorFixture createFixture(List<Block> blocks, StoryPaneController.BlockTextPersister persister)
            throws Exception
    {
        return createFixture(blocks, persister, ignored -> null);
    }

    private EditorFixture createFixture(List<Block> blocks, StoryPaneController.BlockTextPersister persister,
            java.util.function.Function<String, StoryImage> imageLoader)
            throws Exception
    {
        AtomicReference<EditorFixture> result = new AtomicReference<>();
        runOnFxThread(() ->
        {
            StoryPaneController controller = new StoryPaneController(
                    null,
                    () -> { },
                    imageLoader,
                    ignored -> { },
                    (block, forceScroll) -> true,
                    persister,
                    (message, error) ->
                    {
                        throw new AssertionError(message, error);
                    });
            BorderPane root = controller.buildCenterPane(
                    new Button(), new Button(), new Button(), new Button(), new Button(), new Button(),
                    new Button(), new Button());
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
            result.set(new EditorFixture(root, stage, controller));
        });
        return result.get();
    }

    private static void assertVisibleCellGraphicsAreContained(ListView<?> list)
    {
        assertNotNull(list);
        for (Node node : list.lookupAll(".list-cell"))
        {
            if (!(node instanceof ListCell<?> cell) || cell.isEmpty() || cell.getGraphic() == null
                    || !cell.isVisible())
            {
                continue;
            }
            Bounds cellBounds = cell.localToScene(cell.getBoundsInLocal());
            Bounds graphicBounds = cell.getGraphic().localToScene(cell.getGraphic().getBoundsInLocal());
            assertTrue(cellBounds.getMinY() <= graphicBounds.getMinY() + 1
                            && cellBounds.getMaxY() >= graphicBounds.getMaxY() - 1,
                    "Virtualized graphic escaped its cell: cell=" + cellBounds + ", graphic=" + graphicBounds);
        }

        List<Bounds> cellBounds = list.lookupAll(".list-cell").stream()
                .filter(ListCell.class::isInstance)
                .map(ListCell.class::cast)
                .filter(cell -> !cell.isEmpty() && cell.isVisible())
                .map(cell -> cell.localToScene(cell.getBoundsInLocal()))
                .sorted(Comparator.comparingDouble(Bounds::getMinY))
                .toList();
        for (int i = 1; i < cellBounds.size(); i++)
        {
            Bounds previous = cellBounds.get(i - 1);
            Bounds current = cellBounds.get(i);
            assertTrue(previous.getMaxY() <= current.getMinY() + 1,
                    "Virtualized story cells overlap: previous=" + previous + ", current=" + current);
        }
    }

    private static void assertStoryAtBottom(Parent root)
    {
        assertTrue(isStoryAtBottom(root), "Story viewport is not at the bottom.");
    }

    private static boolean isStoryAtBottom(Parent root)
    {
        ScrollBar vertical = findNode(root, ScrollBar.class,
                bar -> bar.getOrientation() == javafx.geometry.Orientation.VERTICAL
                        && enclosingList(bar) != null);
        return vertical != null && vertical.getValue() >= vertical.getMax() - 0.01;
    }

    private static void assertNodeInsideListViewport(Parent root, Node node, String message)
    {
        assertTrue(isNodeInsideListViewport(root, node), message);
    }

    private static boolean isNodeInsideListViewport(Parent root, Node node)
    {
        ListView<?> list = findNode(root, ListView.class, ignored -> true);
        if (list == null)
        {
            return false;
        }
        Bounds listBounds = list.localToScene(list.getBoundsInLocal());
        Bounds nodeBounds = node.localToScene(node.getBoundsInLocal());
        return listBounds != null && nodeBounds != null && listBounds.intersects(nodeBounds);
    }

    private static void assertNodeBottomInsideListViewport(Parent root, Node node, String message)
    {
        assertTrue(isNodeBottomInsideListViewport(root, node), message);
    }

    private static boolean isNodeBottomInsideListViewport(Parent root, Node node)
    {
        ListView<?> list = findNode(root, ListView.class, ignored -> true);
        if (list == null)
        {
            return false;
        }
        Bounds listBounds = list.localToScene(list.getBoundsInLocal());
        Bounds nodeBounds = node.localToScene(node.getBoundsInLocal());
        return listBounds != null && nodeBounds != null
                && nodeBounds.getMaxY() >= listBounds.getMinY() - 1
                && nodeBounds.getMaxY() <= listBounds.getMaxY() + 1;
    }

    private static byte[] squarePng()
    {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
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

    private static void waitForFxCondition(BooleanSupplier condition, String message) throws Exception
    {
        CountDownLatch satisfied = new CountDownLatch(1);
        AtomicReference<AnimationTimer> timerReference = new AtomicReference<>();
        runOnFxThread(() ->
        {
            AnimationTimer timer = new AnimationTimer()
            {
                @Override
                public void handle(long now)
                {
                    if (condition.getAsBoolean())
                    {
                        stop();
                        satisfied.countDown();
                    }
                }
            };
            timerReference.set(timer);
            timer.start();
            Platform.requestNextPulse();
        });

        boolean completed = satisfied.await(5, TimeUnit.SECONDS);
        if (!completed)
        {
            runOnFxThread(() ->
            {
                AnimationTimer timer = timerReference.get();
                if (timer != null)
                {
                    timer.stop();
                }
            });
        }
        assertTrue(completed, message);
    }

    private record EditorFixture(BorderPane root, Stage stage, StoryPaneController controller)
    {
    }
}
