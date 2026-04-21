package io.github.pixelatedvolume.laminate.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.pixelatedvolume.laminate.SyntheticPackResources;
import io.github.pixelatedvolume.laminate.LaminateMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.PlaceholderLookupProvider;
import net.minecraft.util.StrictJsonParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Class that provides operations used as mixins to ModelManager.
 * Carries out the merge between upper-level fall-through definitions
 * and the definitions below them.
 */
@Mixin(ModelManager.class)
public class ModelManagerMixin {

    // Various constants needed to load definitions
    private static RegistryAccess.Frozen registryAccess
        = ClientRegistryLayer.createRegistryAccess().compositeAccess();
    private static PlaceholderLookupProvider lookupProvider
        = new PlaceholderLookupProvider(registryAccess);
    private static RegistryOps ops
        = lookupProvider.createSerializationContext(JsonOps.INSTANCE);

    /**
     * Before vanilla ModelManager.reload() is run, this takes all item
     * definitions from discovered resource packs, merges them into one
     * definition if they allow falling back to lower definitions, makes
     * a synthesized pack out of these definitions, and places it at the
     * top of the pack hierarchy.  ModelManager.reload() then proceeds as
     * normal.
     */
    @Inject(method = "reload", at = @At("HEAD"))
    private void onReload(PreparableReloadListener.SharedState state,
                          Executor e1,
                          PreparableReloadListener.PreparationBarrier b,
                          Executor e2,
                          CallbackInfoReturnable<?> ci)
    {
        MultiPackResourceManagerAccessor rm
            = (MultiPackResourceManagerAccessor) state.resourceManager();
        Map<Identifier, List<Resource>> resources
            = rm.listResourceStacks("items", id -> true);
        Map<Identifier, byte[]> mergedResources = new TreeMap<>();
        for (Entry<Identifier, List<Resource>> pair : resources.entrySet()) {
            Identifier id = pair.getKey();
            JsonObject merged = mergeStack(id, pair.getValue());
            if (merged != null) {
                byte[] data
                    = merged.toString().getBytes(StandardCharsets.UTF_8);
                mergedResources.put(id, data);
            }
        }
        // Per-namespace resource managers
        Map<String, FallbackResourceManager> managers
            = rm.getNamespacedManagers();
        SyntheticPackResources synth
            = new SyntheticPackResources(mergedResources);
        // Add new pack to all per-namespace managers
        for (String namespace
                 : synth.getNamespaces(PackType.CLIENT_RESOURCES)) {
            FallbackResourceManager frm = managers.get(namespace);
            if (frm != null) {
                // Synth pack goes atop all others
                frm.push(synth);
            }
        }
    }

    /**
     * Creates a merged item definition out of a list of definition resource
     * locations.
     *
     * The presence of a "definition_fall_through" field on a definition will
     * cause its sister "fallback" field to be replaced by the definition from
     * the next-highest-priority pack.  Definitions that cannot be loaded or
     * parsed are not included, and invalid definitions cannot begin a merge
     * chain, even if they have "definition_fall_through" set.
     * 
     * Null is returned if the top-priority does not allow fallthroughs or if
     * there are no definitions below it to fall through to.
     */
    private static JsonObject mergeStack(Identifier id,
                                         List<Resource> stack) {
        JsonObject merged = null;
        JsonObject savedTarget = null;
        for (Resource resource : stack.reversed()) {
            String packId = resource.sourcePackId();
            JsonObject current;
            try {
                current
                    = StrictJsonParser.parse(resource.openAsReader())
                                      .getAsJsonObject();
            } catch (JsonParseException | IOException e) {
                // Return if this is the first iteration because the top-level
                // definition is invalid, skip to next resource otherwise
                if (savedTarget == null) return merged;
                LaminateMod.LOGGER.error("Failed to open item model '{}' from"
                                         + " pack '{}', skipping fallback"
                                         + " merge: ",
                                         id, packId, e);
                continue;
            }
            DataResult<ClientItem> loadResult
                = ClientItem.CODEC.parse(ops, current);
            // Check current is loadable before setting anything
            if (loadResult.result().isPresent()) {
                if (savedTarget != null) {
                    savedTarget.add("fallback", current.get("model"));
                } else {
                    merged = current;
                }
                savedTarget = findFallThrough(current);
            }
            // Return if there was no new target in the current definition
            // (end of the chain)
            if (savedTarget == null) return merged;
            // Only run if loadResult is an error
            loadResult
            .error()
            .ifPresent(error ->
                       LaminateMod
                       .LOGGER
                       .error("Couldn't parse item model '{}' from"
                              + " pack '{}', skipping fallback merge: {}",
                              id, packId, error.message()));
        }
        return merged;
    }

    /**
     * BFS to find a JsonObject with a field named "definition_fall_through"
     * set to TRUE.
     */
    private static JsonObject findFallThrough(JsonObject target) {
        ArrayDeque<JsonElement> nodes
            = new ArrayDeque<JsonElement>(List.of(target));
        while (!nodes.isEmpty()) {
            JsonElement element = nodes.poll();
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                JsonElement flag = obj.get("definition_fall_through");
                if (flag != null
                    && flag.isJsonPrimitive()
                    && flag.getAsJsonPrimitive().isBoolean()
                    && flag.getAsBoolean())
                {
                    return obj;
                }
                for (Entry<String, JsonElement> entry : obj.entrySet()) {
                    nodes.add(entry.getValue());
                }
            } else if (element.isJsonArray()) {
                for (JsonElement child : element.getAsJsonArray()) {
                    nodes.add(child);
                }
            }
        }
        return null;
    }
}
