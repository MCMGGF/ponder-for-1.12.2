package net.createmod.ponder.api.client;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

/** A client-side practice screen opened from the viewer for an exact scene ID. */
public final class PonderLessonPractice {
    private static final Map<ResourceLocation, PonderLessonPractice> PRACTICES = new ConcurrentHashMap<>();

    private final String title;
    private final String unavailableNotice;
    private final Function<GuiScreen, GuiScreen> screenFactory;

    public PonderLessonPractice(String title, String unavailableNotice, Function<GuiScreen, GuiScreen> screenFactory) {
        this.title = Objects.requireNonNull(title);
        this.unavailableNotice = Objects.requireNonNull(unavailableNotice);
        this.screenFactory = Objects.requireNonNull(screenFactory);
    }

    /** Register during client mod initialization. Each scene may expose one exercise. */
    public void register(ResourceLocation sceneId) {
        if (PRACTICES.putIfAbsent(sceneId, this) != null) {
            throw new IllegalStateException("Practice already registered for " + sceneId);
        }
    }

    @Nullable
    public static PonderLessonPractice find(@Nullable ResourceLocation sceneId) {
        return sceneId == null ? null : PRACTICES.get(sceneId);
    }

    public String getTitle() {
        return title;
    }

    public String getUnavailableNotice() {
        return unavailableNotice;
    }

    /** Called in a single-player world. Throw IllegalStateException if the exercise cannot open. */
    public GuiScreen createScreen(GuiScreen parent) {
        return Objects.requireNonNull(screenFactory.apply(parent));
    }
}
