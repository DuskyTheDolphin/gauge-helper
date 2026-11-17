package com.forgeward.gaugehelper.mixin;

import net.createmod.catnip.gui.AbstractSimiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractSimiScreen.class, remap = false)
public interface SimiScreenAccess {
    @Accessor("guiLeft") int fwgh$left();
    @Accessor("guiTop") int fwgh$top();
    @Accessor("windowWidth") int fwgh$width();
    @Accessor("windowHeight") int fwgh$height();
}
