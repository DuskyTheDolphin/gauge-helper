package com.forgeward.gaugehelper.mixin;

import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Field reads only. extra_gauges and fluidlogistics inject into this screen, so nothing here injects: the panel hangs off
 * NeoForge's ScreenEvents, and an accessor can't clash with their injectors.
 */
@Mixin(value = FactoryPanelScreen.class, remap = false)
public interface FactoryPanelScreenAccess {
    @Accessor("behaviour") FactoryPanelBehaviour fwgh$behaviour();
    @Accessor("restocker") boolean fwgh$restocker();
    @Accessor("craftingActive") boolean fwgh$craftingActive();
    @Accessor("outputConfig") BigItemStack fwgh$output();
    @Accessor("inputConfig") List<BigItemStack> fwgh$inputs();
    @Accessor("addressBox") AddressEditBox fwgh$address();
    @Accessor("promiseExpiration") ScrollInput fwgh$promise();
}
