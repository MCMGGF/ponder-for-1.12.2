package net.createmod.ponder.foundation.ui.sandbox.worker;

import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition;
import net.createmod.ponder.foundation.ui.SandboxGuiScript.Action;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.MouseHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Entry point for the isolated Minecraft client. The host never registers these event listeners. */
public final class SandboxWorker {
    private enum Phase { MENU, LOGIN, SYNC, RECORD, FINAL_FRAME, DONE }
    private static final String WORLD = "ponder-worker";
    private static SandboxWorker active;
    private int pointerX = -1000;
    private int pointerY = -1000;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Path job;
    private Phase phase = Phase.MENU;
    private BlockGuiDemoDefinition definition;
    private SandboxFrames frames;
    private SandboxFixture fixture;
    private Future<?> preparation;
    private Future<Boolean> assertion;
    private int elapsed;
    private int tick = -1;
    private int index;
    private int actionStart = -1;
    private boolean activated;
    private Method pressed;
    private Method released;

    private SandboxWorker(Path job) { this.job = job; }

    public static void start(File jobDir) {
        SandboxWorker worker = new SandboxWorker(jobDir.toPath().toAbsolutePath().normalize());
        active = worker;
        MinecraftForge.EVENT_BUS.register(worker);
    }

    public static int mouseX(int hardware) { return active == null ? hardware : active.pointerX; }
    public static int mouseY(int hardware) { return active == null ? hardware : active.pointerY; }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase == Phase.DONE) return;
        try {
            if (++elapsed > 3600 && phase.ordinal() < Phase.RECORD.ordinal())
                throw new IOException("Timed out during sandbox startup phase " + phase);
            switch (phase) {
                case MENU -> {
                    if (mc.currentScreen instanceof GuiMainMenu) launch();
                }
                case LOGIN -> {
                    if (mc.world != null && mc.player != null && mc.currentScreen == null) prepare();
                }
                case SYNC -> {
                    if (preparation.isDone()) {
                        preparation.get();
                        if (fixture.clientReady(mc)) {
                            if (assertion == null) assertion = fixture.server().callFromMainThread(fixture::serverReady);
                            if (assertion.isDone()) {
                                if (assertion.get()) phase = Phase.RECORD;
                                assertion = null;
                            }
                        }
                    }
                }
                case RECORD -> advance();
                case FINAL_FRAME, DONE -> { }
            }
        } catch (Exception | LinkageError failure) {
            fail(failure);
        }
    }

    private void launch() throws IOException {
        Path isolated = job.toRealPath().resolve("game");
        if (!mc.gameDir.toPath().toRealPath().equals(isolated) || mc.world != null || mc.getIntegratedServer() != null)
            throw new IOException("Sandbox requires a fresh client using its job's private game directory");
        Path saves = Files.createDirectories(isolated.resolve("saves"));
        if (!saves.equals(saves.toRealPath()) || Files.exists(saves.resolve(WORLD), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Sandbox requires an unused local world directory");
        definition = BlockGuiDemoDefinition.parse(new JsonParser().parse(Files.readString(job.resolve("job.json"))).getAsJsonObject());
        if (definition.sandbox() == null) throw new IllegalArgumentException("Missing sandbox script");
        frames = new SandboxFrames(job);
        pressed = ReflectionHelper.findMethod(GuiScreen.class, "mouseClicked", "func_73864_a", int.class, int.class, int.class);
        released = ReflectionHelper.findMethod(GuiScreen.class, "mouseReleased", "func_146286_b", int.class, int.class, int.class);
        mc.gameSettings.pauseOnLostFocus = false;
        mc.gameSettings.renderDistanceChunks = 2;
        mc.gameSettings.limitFramerate = 30;
        mc.gameSettings.enableVsync = false;
        mc.gameSettings.touchscreen = false;
        // Joining/leaving a GUI in a second client must not grab or recenter the desktop cursor.
        mc.mouseHelper = new MouseHelper() {
            @Override public void grabMouseCursor() { }
            @Override public void ungrabMouseCursor() { }
            @Override public void mouseXYChange() { }
        };
        mc.getSession().setProperties(mc.getSession().getProfile().getProperties());
        phase = Phase.LOGIN;
        WorldSettings settings = new WorldSettings(1L, GameType.SURVIVAL, false, false, WorldType.FLAT)
            .setGeneratorOptions("3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;");
        mc.launchIntegratedServer(WORLD, WORLD, settings);
    }

    private void prepare() {
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null || mc.getConnection() == null || !mc.getConnection().getNetworkManager().isLocalChannel())
            throw new IllegalStateException("Sandbox requires its integrated server and loopback connection");
        BlockPos pos = new BlockPos(mc.player.posX, 12, mc.player.posZ);
        fixture = new SandboxFixture(server, mc.player.getUniqueID(), job.resolve("game/saves").resolve(WORLD), pos, definition);
        preparation = server.callFromMainThread(() -> { fixture.initialize(); return null; });
        phase = Phase.SYNC;
    }

    private void advance() throws Exception {
        if (++tick >= 24_000) throw new IllegalStateException("Recording exceeded 24000 ticks");
        if (mc.world == null || mc.player == null || mc.getIntegratedServer() != fixture.server() ||
            mc.getConnection() == null || !mc.getConnection().getNetworkManager().isLocalChannel())
            throw new IOException("Lost sandbox loopback session");
        List<Action> actions = definition.sandbox().actions();
        Action action = actions.get(index);
        if (tick < action.tick()) return;
        if (actionStart < 0) actionStart = tick;
        if (tick - actionStart > action.timeout()) {
            String state = fixture.server().callFromMainThread(() -> fixture.describe(action)).get(2, TimeUnit.SECONDS);
            throw new IOException("Timed out waiting for action " + index + " " + action + "; " + state);
        }
        if (!perform(action)) return;
        actionStart = -1;
        assertion = null;
        activated = false;
        if (++index == actions.size()) phase = Phase.FINAL_FRAME;
    }

    private boolean perform(Action action) throws Exception {
        GuiScreen gui = mc.currentScreen;
        switch (action.type()) {
            case "activate" -> {
                if (!activated) {
                    if (gui != null) throw new IOException("Close the current GUI before activating the block");
                    BlockPos pos = fixture.pos();
                    EnumActionResult result = mc.playerController.processRightClickBlock(mc.player, mc.world, pos,
                        EnumFacing.NORTH, new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ()), EnumHand.MAIN_HAND);
                    if (result == EnumActionResult.FAIL) throw new IOException("Sandbox block activation was rejected");
                    activated = true;
                }
                return mc.currentScreen != null;
            }
            case "click" -> {
                if (gui == null) throw new IOException("Click requires an open GUI");
                int x = action.x();
                int y = action.y();
                if (gui instanceof GuiContainer container) {
                    if (action.slot() != null) {
                        if (action.slot() >= container.inventorySlots.inventorySlots.size())
                            throw new IOException("Click slot is outside the active container");
                        Slot slot = container.inventorySlots.getSlot(action.slot());
                        if (!slot.isEnabled()) throw new IOException("Click targets a disabled slot");
                        x = slot.xPos + 8;
                        y = slot.yPos + 8;
                    }
                    x += container.getGuiLeft();
                    y += container.getGuiTop();
                } else if (action.slot() != null) throw new IOException("Slot click requires a container GUI");
                if (x < 0 || y < 0 || x >= gui.width || y >= gui.height)
                    throw new IOException("Click falls outside the active GUI");
                pointerX = x;
                pointerY = y;
                pressed.invoke(gui, x, y, action.button());
                if (mc.currentScreen == gui) released.invoke(gui, x, y, action.button());
                return true;
            }
            case "close" -> {
                if (gui == null) throw new IOException("Close requires an open GUI");
                mc.player.closeScreen();
                return true;
            }
            case "wait_screen" -> {
                return gui != null && (gui.getClass().getSimpleName().equals(action.screen()) ||
                    gui.getClass().getName().equals(action.screen()));
            }
            case "wait_slot", "wait_nbt" -> {
                int window = -1;
                if ("wait_slot".equals(action.type())) {
                    if (!(gui instanceof GuiContainer container) || container.inventorySlots != mc.player.openContainer ||
                        !SandboxFixture.matchesSlot(container.inventorySlots, action)) {
                        assertion = null;
                        return false;
                    }
                    window = container.inventorySlots.windowId;
                }
                if (assertion == null) {
                    int expectedWindow = window;
                    assertion = fixture.server().callFromMainThread(() -> fixture.matches(action, expectedWindow));
                }
                if (!assertion.isDone()) return false;
                boolean matches = assertion.get();
                assertion = null;
                return matches;
            }
            default -> throw new IllegalArgumentException("Unknown sandbox action " + action.type());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.getGui() == mc.currentScreen) sample(event.getGui());
    }

    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END && mc.currentScreen == null) sample(null);
    }

    private void sample(GuiScreen gui) {
        if (tick < 0 || phase != Phase.RECORD && phase != Phase.FINAL_FRAME) return;
        try {
            frames.capture(mc, gui, definition.capture(), tick);
            if (phase == Phase.FINAL_FRAME) {
                frames.finish(tick + 1);
                stop();
            }
        } catch (Exception | LinkageError failure) {
            fail(failure);
        }
    }

    private void fail(Throwable failure) {
        try {
            Ponder.LOGGER.error("GUI sandbox failed in {} at tick {}, action {}", phase, tick, index, failure);
            StringWriter trace = new StringWriter();
            failure.printStackTrace(new PrintWriter(trace));
            Files.writeString(job.resolve("error.txt"), phase + " tick=" + tick + " action=" + index + "\n" + trace);
        } catch (IOException error) {
            Ponder.LOGGER.error("Could not save sandbox failure", error);
        } finally {
            stop();
        }
    }

    private void stop() {
        phase = Phase.DONE;
        active = null;
        MinecraftForge.EVENT_BUS.unregister(this);
        mc.shutdown();
    }
}
