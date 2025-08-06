package com.leclowndu93150.better_locator_bar.mixin;

import com.leclowndu93150.better_locator_bar.ContextualBarRendererInterface;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ContextualBarRenderer.class)
public interface ContextualBarRendererAccessor extends ContextualBarRendererInterface {
    
    @Invoker("left")
    int callLeft(Window window);
    
    @Invoker("top") 
    int callTop(Window window);
}