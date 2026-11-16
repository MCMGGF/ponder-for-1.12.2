package net.createmod.ponder.api.element;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

public interface TextElementBuilder {

    TextElementBuilder colored(PonderPalette color);

    TextElementBuilder pointAt(Vec3d vec);

    default TextElementBuilder pointAt(Selection selection) {
        return pointAt(selection == null || !selection.iterator().hasNext() ? (Vec3d) null : selection.getCenter());
    }

    TextElementBuilder independent(int y);

    default TextElementBuilder independent() {
        return independent(0);
    }

    TextElementBuilder text(String defaultText);

    TextElementBuilder text(String defaultText, Object... params);

    TextElementBuilder sharedText(ResourceLocation key);

    TextElementBuilder sharedText(ResourceLocation key, Object... params);

    TextElementBuilder sharedText(String key);

    TextElementBuilder sharedText(String key, Object... params);

    TextElementBuilder placeNearTarget();

    TextElementBuilder attachKeyFrame();
}
