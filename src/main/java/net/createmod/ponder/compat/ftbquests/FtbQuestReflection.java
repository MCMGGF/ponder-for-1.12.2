package net.createmod.ponder.compat.ftbquests;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

/**
 * API members verified against FTB Quests 1202.9.0.15 / FTBLib 5.4.7.2.
 * No FTB types occur in class descriptors, keeping both mods optional.
 */
final class FtbQuestReflection {

    private static final String WRAPPER = "com.feed_the_beast.ftblib.lib.gui.GuiWrapper";
    private static final String TREE = "com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree";
    private static final String ITEM_TASK = "com.feed_the_beast.ftbquests.quest.task.ItemTask";
    private final Method getGui;
    private final Method getViewedQuest;
    private final Method getX;
    private final Method getY;
    private final Method setY;
    private final Method setHeight;
    private final Method getScrollX;
    private final Method getScrollY;
    private final Method setScrollX;
    private final Method setScrollY;
    private final Method getContentWidth;
    private final Method getContentHeight;
    private final Method setScrollbarValue;
    private final Method getQuest;
    private final Method getChapter;
    private final Method canEdit;
    private final Method questVisible;
    private final Method chapterVisible;
    private final Method selectChapter;
    private final Method viewQuest;
    private final Method closeQuest;
    private final Method getWrapper;
    private final Method getPrevScreen;
    private final Field previousScreen;
    private final Field currentFile;
    private final Field treeFile;
    private final Field currentTree;
    private final Field self;
    private final Field disableGui;
    private final Field questChapter;
    private final Field panelQuest;
    private final Field viewQuestPanel;
    private final Field chapterPanel;
    private final Field questPanel;
    private final Field selectedChapter;
    private final Field hidePanel;
    private final Field panelContent;
    private final Field panelTasks;
    private final Field panelRewards;
    private final Field panelText;
    private final Field attachedScrollbar;
    private final Field scrollbarPlane;
    private final Field scrollbarVertical;
    private final Field contextMenu;
    private final Field width;
    private final Field height;
    private final Field posY;
    private final Field id;
    private final Field invalid;
    private final Field icon;
    private final Field title;
    private final Field tasks;
    private final Field items;
    private final Class<?> treeClass;
    private final Class<?> wrapperClass;
    private final Class<?> itemTaskClass;

    FtbQuestReflection(ClassLoader loader) throws ReflectiveOperationException {
        this(name -> Class.forName(name, false, loader));
    }

    FtbQuestReflection(ClassLookup classes) throws ReflectiveOperationException {
        wrapperClass = classes.find(WRAPPER);
        treeClass = classes.find(TREE);
        itemTaskClass = classes.find(ITEM_TASK);
        Class<?> widget = classes.find("com.feed_the_beast.ftblib.lib.gui.Widget");
        Class<?> baseGui = classes.find("com.feed_the_beast.ftblib.lib.gui.GuiBase");
        Class<?> panel = classes.find("com.feed_the_beast.ftbquests.gui.tree.PanelViewQuest");
        Class<?> basePanel = classes.find("com.feed_the_beast.ftblib.lib.gui.Panel");
        Class<?> scrollbar = classes.find("com.feed_the_beast.ftblib.lib.gui.ScrollBar");
        Class<?> plane = classes.find("com.feed_the_beast.ftblib.lib.gui.ScrollBar$Plane");
        Class<?> quest = classes.find("com.feed_the_beast.ftbquests.quest.Quest");
        Class<?> file = classes.find("com.feed_the_beast.ftbquests.client.ClientQuestFile");
        Class<?> chapter = classes.find("com.feed_the_beast.ftbquests.quest.Chapter");
        Class<?> data = classes.find("com.feed_the_beast.ftbquests.quest.QuestData");
        getGui = wrapperClass.getMethod("getGui");
        getViewedQuest = treeClass.getMethod("getViewedQuest");
        getX = widget.getMethod("getX");
        getY = widget.getMethod("getY");
        setY = widget.getMethod("setY", int.class);
        setHeight = widget.getMethod("setHeight", int.class);
        getScrollX = basePanel.getMethod("getScrollX");
        getScrollY = basePanel.getMethod("getScrollY");
        setScrollX = basePanel.getMethod("setScrollX", double.class);
        setScrollY = basePanel.getMethod("setScrollY", double.class);
        getContentWidth = basePanel.getMethod("getContentWidth");
        getContentHeight = basePanel.getMethod("getContentHeight");
        setScrollbarValue = scrollbar.getMethod("setValue", int.class);
        currentFile = file.getField("INSTANCE");
        treeFile = treeClass.getField("file");
        currentTree = file.getField("questTreeGui");
        self = file.getField("self");
        disableGui = file.getField("disableGui");
        questChapter = quest.getField("chapter");
        panelQuest = panel.getField("quest");
        getQuest = file.getMethod("getQuest", int.class);
        getChapter = file.getMethod("getChapter", int.class);
        canEdit = file.getMethod("canEdit");
        questVisible = quest.getMethod("isVisible", data);
        chapterVisible = chapter.getMethod("isVisible", data);
        selectChapter = treeClass.getMethod("selectChapter", chapter);
        viewQuest = treeClass.getMethod("viewQuest", quest);
        closeQuest = treeClass.getMethod("closeQuest");
        getWrapper = treeClass.getMethod("getWrapper");
        getPrevScreen = baseGui.getMethod("getPrevScreen");
        previousScreen = baseGui.getDeclaredField("prevScreen");
        if (previousScreen.getType() != GuiScreen.class) {
            throw new NoSuchFieldException("GuiBase.prevScreen is not a GuiScreen");
        }
        try {
            previousScreen.setAccessible(true);
        } catch (RuntimeException exception) {
            throw new ReflectiveOperationException("Cannot access the verified GuiBase return-screen field", exception);
        }
        viewQuestPanel = treeClass.getField("viewQuestPanel");
        chapterPanel = treeClass.getField("chapterPanel");
        questPanel = treeClass.getField("questPanel");
        selectedChapter = treeClass.getField("selectedChapter");
        hidePanel = panel.getField("hidePanel");
        panelContent = panel.getField("panelContent");
        panelTasks = panel.getField("panelTasks");
        panelRewards = panel.getField("panelRewards");
        panelText = panel.getField("panelText");
        attachedScrollbar = basePanel.getField("attachedScrollbar");
        scrollbarPlane = scrollbar.getField("plane");
        scrollbarVertical = plane.getField("isVertical");
        contextMenu = treeClass.getField("contextMenu");
        width = widget.getField("width");
        height = widget.getField("height");
        posY = widget.getField("posY");
        id = quest.getField("id");
        invalid = quest.getField("invalid");
        icon = quest.getField("icon");
        title = quest.getField("title");
        tasks = quest.getField("tasks");
        items = itemTaskClass.getField("items");
    }

    ViewedQuest inspect(GuiScreen screen) throws ReflectiveOperationException {
        if (screen == null || !wrapperClass.isInstance(screen)) {
            return null;
        }
        Object tree = getGui.invoke(screen);
        if (!treeClass.isInstance(tree) || contextMenu.get(tree) != null) {
            return null;
        }
        Object quest = getViewedQuest.invoke(tree);
        Object panel = viewQuestPanel.get(tree);
        if (quest == null || invalid.getBoolean(quest) || panel == null || hidePanel.getBoolean(panel)) {
            return null;
        }
        Object content = panelContent.get(panel);
        if (content == null) {
            return null;
        }
        return new ViewedQuest(quest, panel, content, QuestSceneLinks.idFromInt(id.getInt(quest)),
            String.valueOf(title.get(quest)), ((Integer) getX.invoke(panel)).intValue(),
            ((Integer) getY.invoke(panel)).intValue(), width.getInt(panel), height.getInt(panel),
            posY.getInt(panel), posY.getInt(content), height.getInt(content));
    }

    List<TaskItems> taskItems(ViewedQuest view) throws ReflectiveOperationException {
        Object value = tasks.get(view.quest);
        if (!(value instanceof Iterable<?>)) {
            return Collections.emptyList();
        }
        List<TaskItems> result = new ArrayList<TaskItems>();
        int count = 0;
        for (Object task : (Iterable<?>) value) {
            if (++count > 4096) {
                break;
            }
            if (task == null || invalid.getBoolean(task)) {
                continue;
            }
            List<ItemStack> stacks = new ArrayList<ItemStack>();
            if (itemTaskClass.isInstance(task)) {
                Object taskItems = items.get(task);
                if (taskItems instanceof Iterable<?>) {
                    for (Object stack : (Iterable<?>) taskItems) {
                        if (stacks.size() >= 1024) {
                            break;
                        }
                        if (stack instanceof ItemStack && !((ItemStack) stack).isEmpty()) {
                            stacks.add(((ItemStack) stack).copy());
                        }
                    }
                }
            }
            result.add(new TaskItems(QuestSceneLinks.idFromInt(id.getInt(task)), stacks));
        }
        return result;
    }

    ItemStack questIcon(ViewedQuest view) throws ReflectiveOperationException {
        Object value = icon.get(view.quest);
        return value instanceof ItemStack ? ((ItemStack) value).copy() : ItemStack.EMPTY;
    }

    void setContentHeight(Object content, int value) throws ReflectiveOperationException {
        setHeight.invoke(content, Integer.valueOf(value));
    }

    int contentHeight(Object content) throws IllegalAccessException {
        return height.getInt(content);
    }

    FooterState captureFooter(ViewedQuest view) {
        return new FooterState(view);
    }

    QuestFooterLayout reserveFooter(ViewedQuest view, FooterState state, int screenWidth, int screenHeight)
        throws ReflectiveOperationException {
        if (view.panel != state.panel || view.quest != state.quest || view.content != state.content) {
            return null;
        }
        if (view.panelPosY != state.appliedPanelY) {
            state.originalPanelY = view.panelPosY;
        }
        if (view.height != state.appliedPanelHeight) {
            state.originalPanelHeight = view.height;
        }
        if (view.contentHeight != state.appliedContentHeight) {
            state.originalContentHeight = view.contentHeight;
        }
        int parentY = view.y - view.panelPosY;
        QuestFooterLayout layout = QuestFooterLayout.create(view.x, parentY + state.originalPanelY,
            view.width, state.originalPanelHeight, view.contentY, screenWidth, screenHeight);
        if (layout == null) {
            return null;
        }
        state.appliedPanelY = layout.panelY - parentY;
        state.appliedPanelHeight = layout.panelHeight;
        state.appliedContentHeight = layout.contentHeight;
        setY.invoke(state.panel, Integer.valueOf(state.appliedPanelY));
        setHeight.invoke(state.panel, Integer.valueOf(state.appliedPanelHeight));
        setContentHeight(state.content, state.appliedContentHeight);
        return layout;
    }

    void restoreFooter(FooterState state) throws ReflectiveOperationException {
        if (state == null) {
            return;
        }
        // The parent panel is reused when FTB replaces its children or switches quests.
        if (panelContent.get(state.panel) != state.content || panelQuest.get(state.panel) != state.quest) {
            return;
        }
        if (height.getInt(state.content) == state.appliedContentHeight) {
            setContentHeight(state.content, state.originalContentHeight);
        }
        if (posY.getInt(state.panel) == state.appliedPanelY) {
            setY.invoke(state.panel, Integer.valueOf(state.originalPanelY));
        }
        if (height.getInt(state.panel) == state.appliedPanelHeight) {
            setHeight.invoke(state.panel, Integer.valueOf(state.originalPanelHeight));
        }
    }

    ReturnView captureReturnView(GuiScreen screen) throws ReflectiveOperationException {
        ViewedQuest view = inspect(screen);
        if (view == null) {
            return null;
        }
        Object tree = getGui.invoke(screen);
        Object[] panels = scrollPanels(tree);
        ScrollPosition[] positions = new ScrollPosition[panels.length];
        for (int index = 0; index < panels.length; index++) {
            Object panel = panels[index];
            if (panel != null) {
                positions[index] = new ScrollPosition(((Double) getScrollX.invoke(panel)).doubleValue(),
                    ((Double) getScrollY.invoke(panel)).doubleValue());
            }
        }
        Object chapter = selectedChapter.get(tree);
        return new ReturnView(screen, tree, treeFile.get(tree), view.quest, chapter, id.getInt(view.quest),
            chapter == null ? 0 : id.getInt(chapter), (GuiScreen) getPrevScreen.invoke(tree), positions);
    }

    ReturnDestination prepareReturn(ReturnView state) throws ReflectiveOperationException {
        Object file = currentFile.get(null);
        if (file == null || invalid.getBoolean(file) || self.get(file) == null) {
            return new ReturnDestination(null, null);
        }
        boolean editing = ((Boolean) canEdit.invoke(file)).booleanValue();
        if (disableGui.getBoolean(file) && !editing) {
            return new ReturnDestination(null, null);
        }
        Object tree = currentTree.get(file);
        if (tree == null || treeFile.get(tree) != file) {
            return new ReturnDestination(null, null);
        }
        repairPreviousScreen(tree, state);
        Object data = self.get(file);
        Object currentlyViewed = getViewedQuest.invoke(tree);
        Object currentlySelected = selectedChapter.get(tree);
        if (currentlyViewed != null && currentlyViewed != state.quest
            && getQuest.invoke(file, Integer.valueOf(id.getInt(currentlyViewed))) == currentlyViewed
            && !invalid.getBoolean(currentlyViewed)
            && visibleChapter(questChapter.get(currentlyViewed), data, editing)
            && (editing || ((Boolean) questVisible.invoke(currentlyViewed, data)).booleanValue())) {
            return new ReturnDestination(tree == state.tree ? state.screen : (GuiScreen) getWrapper.invoke(tree), null);
        }
        // Native refreshGui carries the old chapter into a new tree without reopening its quest.
        boolean inheritedChapter = file == state.file && tree != state.tree && currentlySelected == state.chapter;
        if (currentlyViewed == null
            && ((file == state.file && tree == state.tree && currentlySelected == null)
                || (!inheritedChapter && visibleChapter(currentlySelected, data, editing)
                    && getChapter.invoke(file, Integer.valueOf(id.getInt(currentlySelected))) == currentlySelected))) {
            return new ReturnDestination(tree == state.tree ? state.screen : (GuiScreen) getWrapper.invoke(tree), null);
        }
        Object quest = getQuest.invoke(file, Integer.valueOf(state.questUid));
        Object chapter = quest == null ? null : questChapter.get(quest);
        boolean available = quest != null && !invalid.getBoolean(quest)
            && (file != state.file || quest == state.quest)
            && visibleChapter(chapter, data, editing)
            && (editing || ((Boolean) questVisible.invoke(quest, data)).booleanValue());
        if (available && file == state.file && tree == state.tree && quest == state.quest
            && getViewedQuest.invoke(tree) == quest && selectedChapter.get(tree) == chapter) {
            return new ReturnDestination(state.screen, state);
        }

        closeQuest.invoke(tree);
        if (available) {
            selectChapter.invoke(tree, chapter);
            hidePanel.setBoolean(viewQuestPanel.get(tree), false);
            viewQuest.invoke(tree, quest);
        } else {
            chapter = getChapter.invoke(file, Integer.valueOf(state.chapterUid));
            if (file == state.file && chapter != state.chapter) {
                chapter = null;
            }
            selectChapter.invoke(tree, visibleChapter(chapter, data, editing) ? chapter : null);
        }
        GuiScreen screen = (GuiScreen) getWrapper.invoke(tree);
        if (!available) {
            return new ReturnDestination(screen, null);
        }
        int chapterUid = id.getInt(chapter);
        ScrollPosition[] positions = chapterUid == state.chapterUid
            ? state.positions.clone() : new ScrollPosition[state.positions.length];
        return new ReturnDestination(screen,
            new ReturnView(screen, tree, file, quest, chapter, state.questUid, chapterUid,
                (GuiScreen) getPrevScreen.invoke(tree), positions));
    }

    private void repairPreviousScreen(Object tree, ReturnView state) throws ReflectiveOperationException {
        if (tree == state.tree) {
            return;
        }
        Object previous = previousScreen.get(tree);
        if ((state.viewerScreen != null && previous == state.viewerScreen) || previous == state.screen) {
            GuiScreen target = state.previousScreen;
            if (target == state.screen || target == state.viewerScreen) {
                target = null;
            }
            Object previousTree = wrapperClass.isInstance(target) ? getGui.invoke(target) : null;
            if (treeClass.isInstance(previousTree)) {
                Object previousFile = treeFile.get(previousTree);
                if (previousFile != currentFile.get(null) || previousTree == tree
                    || currentTree.get(previousFile) != previousTree) {
                    target = null;
                }
            }
            previousScreen.set(tree, target);
        }
    }

    private boolean visibleChapter(Object chapter, Object data, boolean editing)
        throws ReflectiveOperationException {
        return chapter != null && !invalid.getBoolean(chapter)
            && (editing || ((Boolean) chapterVisible.invoke(chapter, data)).booleanValue());
    }

    boolean restoreReturnView(GuiScreen screen, ReturnView state) throws ReflectiveOperationException {
        if (screen != state.screen || !wrapperClass.isInstance(screen)) {
            return false;
        }
        Object tree = getGui.invoke(screen);
        if (tree != state.tree || currentFile.get(null) != state.file || currentTree.get(state.file) != state.tree
            || getViewedQuest.invoke(tree) != state.quest
            || selectedChapter.get(tree) != state.chapter || invalid.getBoolean(state.quest)) {
            state.cancelled = true;
            return false;
        }
        Object[] panels = scrollPanels(tree);
        // PanelViewQuest replaces these children during both initGui and deferred refreshWidgets.
        if (panels[3] == null || width.getInt(panels[3]) <= 0 || height.getInt(panels[3]) <= 0) {
            return false;
        }
        boolean changed = false;
        for (int index = 0; index < panels.length; index++) {
            Object panel = panels[index];
            ScrollPosition saved = state.positions[index];
            if (panel == null || saved == null) {
                continue;
            }
            ScrollPosition target = index == 1 ? saved : saved.clamp(
                ((Integer) getContentWidth.invoke(panel)).intValue(), width.getInt(panel),
                ((Integer) getContentHeight.invoke(panel)).intValue(), height.getInt(panel));
            double currentX = ((Double) getScrollX.invoke(panel)).doubleValue();
            double currentY = ((Double) getScrollY.invoke(panel)).doubleValue();
            if (currentX != target.x || currentY != target.y || state.restoredPanels[index] != panel) {
                setScrollX.invoke(panel, Double.valueOf(target.x));
                setScrollY.invoke(panel, Double.valueOf(target.y));
                Object bar = attachedScrollbar.get(panel);
                if (bar != null) {
                    boolean vertical = scrollbarVertical.getBoolean(scrollbarPlane.get(bar));
                    setScrollbarValue.invoke(bar, Integer.valueOf((int) (vertical ? target.y : target.x)));
                }
                state.restoredPanels[index] = panel;
                changed = true;
            }
        }
        state.changedSinceDraw |= changed;
        state.restored = true;
        return true;
    }

    private Object[] scrollPanels(Object tree) throws IllegalAccessException {
        Object view = viewQuestPanel.get(tree);
        return new Object[] { chapterPanel.get(tree), questPanel.get(tree), view,
            view == null ? null : panelContent.get(view), view == null ? null : panelTasks.get(view),
            view == null ? null : panelRewards.get(view), view == null ? null : panelText.get(view) };
    }

    static final class ReturnView {
        final GuiScreen screen;
        final Object tree;
        final Object file;
        final Object quest;
        final Object chapter;
        final int questUid;
        final int chapterUid;
        final GuiScreen previousScreen;
        final ScrollPosition[] positions;
        final Object[] restoredPanels;
        boolean returning;
        GuiScreen viewerScreen;
        boolean cancelled;
        boolean restored;
        boolean changedSinceDraw;
        int remainingDraws = 8;
        int stableDraws;

        ReturnView(GuiScreen screen, Object tree, Object file, Object quest, Object chapter, int questUid,
            int chapterUid, GuiScreen previousScreen, ScrollPosition[] positions) {
            this.screen = screen;
            this.tree = tree;
            this.file = file;
            this.quest = quest;
            this.chapter = chapter;
            this.questUid = questUid;
            this.chapterUid = chapterUid;
            this.previousScreen = previousScreen;
            this.positions = positions;
            restoredPanels = new Object[positions.length];
        }

        boolean finishDraw() {
            if (restored && !changedSinceDraw) {
                stableDraws++;
            } else {
                stableDraws = 0;
            }
            changedSinceDraw = false;
            return --remainingDraws <= 0 || stableDraws >= 2 || cancelled;
        }
    }

    static final class ReturnDestination {
        final GuiScreen screen;
        final ReturnView view;

        ReturnDestination(GuiScreen screen, ReturnView view) {
            this.screen = screen;
            this.view = view;
        }
    }

    static final class FooterState {
        final Object quest;
        final Object panel;
        final Object content;
        int originalPanelY;
        int originalPanelHeight;
        int originalContentHeight;
        int appliedPanelY;
        int appliedPanelHeight;
        int appliedContentHeight;

        FooterState(ViewedQuest view) {
            quest = view.quest;
            panel = view.panel;
            content = view.content;
            originalPanelY = appliedPanelY = view.panelPosY;
            originalPanelHeight = appliedPanelHeight = view.height;
            originalContentHeight = appliedContentHeight = view.contentHeight;
        }
    }

    static final class ScrollPosition {
        final double x;
        final double y;

        ScrollPosition(double x, double y) {
            this.x = Double.isFinite(x) ? x : 0.0;
            this.y = Double.isFinite(y) ? y : 0.0;
        }

        ScrollPosition clamp(int contentWidth, int width, int contentHeight, int height) {
            return new ScrollPosition(clampAxis(x, contentWidth, width), clampAxis(y, contentHeight, height));
        }

        private static double clampAxis(double value, int content, int viewport) {
            return Math.max(0.0, Math.min(value, Math.max(0L, (long) content - Math.max(0, viewport))));
        }
    }

    static final class ViewedQuest {
        final Object quest;
        final Object panel;
        final Object content;
        final String id;
        final String title;
        final int x;
        final int y;
        final int width;
        final int height;
        final int panelPosY;
        final int contentY;
        final int contentHeight;

        ViewedQuest(Object quest, Object panel, Object content, String id, String title, int x, int y, int width,
            int height, int panelPosY, int contentY, int contentHeight) {
            this.quest = quest;
            this.panel = panel;
            this.content = content;
            this.id = id;
            this.title = title;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.panelPosY = panelPosY;
            this.contentY = contentY;
            this.contentHeight = contentHeight;
        }

        boolean sameGeometry(ViewedQuest other) {
            return other != null && quest == other.quest && panel == other.panel && content == other.content
                && x == other.x && y == other.y && width == other.width && height == other.height
                && panelPosY == other.panelPosY && contentY == other.contentY && contentHeight == other.contentHeight;
        }
    }

    static final class TaskItems {
        final String id;
        final List<ItemStack> items;

        TaskItems(String id, List<ItemStack> items) {
            this.id = id;
            this.items = items;
        }
    }

    interface ClassLookup {
        Class<?> find(String name) throws ClassNotFoundException;
    }
}
