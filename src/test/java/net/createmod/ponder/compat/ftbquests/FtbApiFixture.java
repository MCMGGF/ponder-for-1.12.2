package net.createmod.ponder.compat.ftbquests;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

/** API shapes checked against the candidate FTB JARs, without loading their mod lifecycle. */
final class FtbApiFixture {

    final ClientQuestFile file = new ClientQuestFile();
    final Chapter chapter = new Chapter(100);
    final Quest quest = new Quest(200, chapter);
    final GuiQuestTree tree = file.questTreeGui;
    final GuiWrapper screen = tree.getWrapper();

    FtbApiFixture() {
        ClientQuestFile.INSTANCE = file;
        file.chapters.put(chapter.id, chapter);
        file.quests.put(quest.id, quest);
        tree.selectChapter(chapter);
        tree.viewQuest(quest);
        tree.rebuild(90, 60);
    }

    static FtbQuestReflection reflection() throws ReflectiveOperationException {
        return new FtbQuestReflection(name -> {
            switch (name) {
                case "com.feed_the_beast.ftblib.lib.gui.GuiWrapper": return GuiWrapper.class;
                case "com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree": return GuiQuestTree.class;
                case "com.feed_the_beast.ftbquests.quest.task.ItemTask": return ItemTask.class;
                case "com.feed_the_beast.ftblib.lib.gui.Widget": return Widget.class;
                case "com.feed_the_beast.ftblib.lib.gui.GuiBase": return GuiBase.class;
                case "com.feed_the_beast.ftbquests.gui.tree.PanelViewQuest": return PanelViewQuest.class;
                case "com.feed_the_beast.ftblib.lib.gui.Panel": return Panel.class;
                case "com.feed_the_beast.ftblib.lib.gui.ScrollBar": return ScrollBar.class;
                case "com.feed_the_beast.ftblib.lib.gui.ScrollBar$Plane": return Plane.class;
                case "com.feed_the_beast.ftbquests.quest.Quest": return Quest.class;
                case "com.feed_the_beast.ftbquests.client.ClientQuestFile": return ClientQuestFile.class;
                case "com.feed_the_beast.ftbquests.quest.Chapter": return Chapter.class;
                case "com.feed_the_beast.ftbquests.quest.QuestData": return QuestData.class;
                default: throw new ClassNotFoundException(name);
            }
        });
    }

    void invalidateFile() {
        file.invalid = true;
        chapter.invalid = true;
        quest.invalid = true;
        quest.tasks.clear();
        file.chapters.clear();
        file.quests.clear();
    }

    public static class Widget {
        public int width = 200;
        public int height = 100;
        public int posX;
        public int posY;
        public Widget parent;

        public int getX() {
            return posX + (parent == null ? 0 : parent.getX());
        }

        public int getY() {
            return posY + (parent == null ? 0 : parent.getY());
        }

        public void setY(int value) {
            posY = value;
        }

        public void setHeight(int value) {
            height = value;
        }
    }

    public static class Panel extends Widget {
        public ScrollBar attachedScrollbar;
        double scrollX;
        double scrollY;
        int contentWidth = 600;
        int contentHeight = 600;

        public double getScrollX() {
            return scrollX;
        }

        public double getScrollY() {
            return scrollY;
        }

        public void setScrollX(double value) {
            scrollX = value;
        }

        public void setScrollY(double value) {
            scrollY = value;
        }

        public int getContentWidth() {
            return contentWidth;
        }

        public int getContentHeight() {
            return contentHeight;
        }
    }

    public static final class Plane {
        public boolean isVertical;

        Plane(boolean vertical) {
            isVertical = vertical;
        }
    }

    public static final class ScrollBar {
        public final Plane plane;
        int value;

        ScrollBar(boolean vertical) {
            plane = new Plane(vertical);
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    public static class QuestObjectBase {
        public int id;
        public boolean invalid;
        public String title = "Fixture quest";
        public ItemStack icon = ItemStack.EMPTY;

        QuestObjectBase(int uid) {
            id = uid;
        }
    }

    public static final class Chapter extends QuestObjectBase {
        boolean visible = true;

        Chapter(int uid) {
            super(uid);
        }

        public boolean isVisible(QuestData data) {
            return visible;
        }
    }

    public static final class Quest extends QuestObjectBase {
        public Chapter chapter;
        public final List<QuestObjectBase> tasks = new ArrayList<>();
        boolean visible = true;

        Quest(int uid, Chapter chapter) {
            super(uid);
            this.chapter = chapter;
        }

        public boolean isVisible(QuestData data) {
            return visible;
        }
    }

    public static final class ItemTask extends QuestObjectBase {
        public final List<ItemStack> items = new ArrayList<>();

        ItemTask(int uid) {
            super(uid);
        }
    }

    public static final class QuestData {
        final Map<Integer, Long> progress = new LinkedHashMap<>();
        final Map<Integer, Boolean> rewards = new LinkedHashMap<>();
    }

    public static final class ClientQuestFile extends QuestObjectBase {
        public static ClientQuestFile INSTANCE;
        public QuestData self = new QuestData();
        public boolean disableGui;
        public GuiQuestTree questTreeGui;
        final Map<Integer, Quest> quests = new LinkedHashMap<>();
        final Map<Integer, Chapter> chapters = new LinkedHashMap<>();
        boolean editing;
        boolean failLookup;

        ClientQuestFile() {
            super(1);
            questTreeGui = new GuiQuestTree(this);
        }

        public Quest getQuest(int uid) {
            if (failLookup) {
                throw new IllegalStateException("Fixture API failure");
            }
            return quests.get(uid);
        }

        public Chapter getChapter(int uid) {
            return chapters.get(uid);
        }

        public boolean canEdit() {
            return editing;
        }
    }

    public static final class PanelViewQuest extends Panel {
        public Quest quest;
        public boolean hidePanel;
        public Panel panelContent;
        public Panel panelTasks;
        public Panel panelRewards;
        public Panel panelText;
    }

    public static class GuiBase extends Panel {
        private GuiScreen prevScreen;

        public GuiScreen getPrevScreen() {
            return prevScreen;
        }

        void previousScreen(GuiScreen screen) {
            prevScreen = screen;
        }
    }

    public static final class GuiQuestTree extends GuiBase {
        public final ClientQuestFile file;
        public final PanelViewQuest viewQuestPanel = new PanelViewQuest();
        public final Panel chapterPanel = new Panel();
        public final Panel questPanel = new Panel();
        public Chapter selectedChapter;
        public Object contextMenu;
        int selections;
        int views;
        int closes;

        GuiQuestTree(ClientQuestFile file) {
            this.file = file;
            viewQuestPanel.parent = this;
        }

        public Quest getViewedQuest() {
            return viewQuestPanel.quest;
        }

        public void selectChapter(Chapter chapter) {
            selections++;
            if (selectedChapter != chapter) {
                closeQuest();
                selectedChapter = chapter;
                questPanel.scrollX = questPanel.scrollY = 0;
            }
        }

        public void viewQuest(Quest quest) {
            views++;
            viewQuestPanel.quest = quest;
        }

        public void closeQuest() {
            closes++;
            if (viewQuestPanel.quest != null) {
                viewQuestPanel.quest = null;
                viewQuestPanel.hidePanel = false;
            }
        }

        public GuiWrapper getWrapper() {
            return new GuiWrapper(this);
        }

        public void openGui() {
            throw new AssertionError("Returning through GuiOpenEvent must not reenter displayGuiScreen");
        }

        void rebuild(int panelY, int panelHeight) {
            viewQuestPanel.posX = 60;
            viewQuestPanel.posY = panelY;
            viewQuestPanel.width = 201;
            viewQuestPanel.height = panelHeight;
            viewQuestPanel.scrollX = viewQuestPanel.scrollY = 0;
            viewQuestPanel.panelContent = new Panel();
            viewQuestPanel.panelContent.parent = viewQuestPanel;
            viewQuestPanel.panelContent.posY = 16;
            viewQuestPanel.panelContent.height = panelHeight - 17;
            viewQuestPanel.panelTasks = new Panel();
            viewQuestPanel.panelRewards = new Panel();
            viewQuestPanel.panelText = new Panel();
        }
    }

    public static final class GuiWrapper extends GuiScreen {
        final GuiQuestTree gui;

        GuiWrapper(GuiQuestTree gui) {
            this.gui = gui;
            width = 320;
            height = 240;
        }

        public GuiQuestTree getGui() {
            return gui;
        }
    }
}
