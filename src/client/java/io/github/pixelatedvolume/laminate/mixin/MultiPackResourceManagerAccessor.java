package io.github.pixelatedvolume.laminate.mixin;

import java.util.Map;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MultiPackResourceManager.class)
public interface MultiPackResourceManagerAccessor extends ResourceManager {
    @Accessor("namespacedManagers")
    Map<String, FallbackResourceManager> getNamespacedManagers();
}
