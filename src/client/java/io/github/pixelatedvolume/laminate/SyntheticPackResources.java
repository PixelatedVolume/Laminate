package io.github.pixelatedvolume.laminate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Class representing an in-memory-only resource pack.
 */
public class SyntheticPackResources implements PackResources {

    /* Backing list of bytes representing the contents of resources that
     * otherwise would be read from a file */
    private Map<Identifier, byte[]> resources;
    
    public SyntheticPackResources(Map<Identifier, byte[]> r) {
        this.resources = r;
    }

    /**
     * Root resources found by paths relative to the pack root.  Since this
     * pack has no backing files there, there are no root resources.
     */
    @Override
    public IoSupplier<InputStream> getRootResource(String... sl) {
        return null;
    }

    /**
     * Returns an input stream providing the contents of a resource location.
     * Null if asking for non-client resources (impossible in this class)
     */
    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
        if (type != PackType.CLIENT_RESOURCES) return null;
        byte[] data = resources.get(id);
        return data != null? () -> new ByteArrayInputStream(data) : null;
    }

    /**
     * Puts the list of assets that match the given namespace and path prefix
     * into the given PackResources.ResourceOutput object.
     */
    @Override
    public void listResources(PackType type,
                              String namespace,
                              String prefix,
                              PackResources.ResourceOutput output)
    {
        if (type == PackType.CLIENT_RESOURCES) {
            Iterator<Identifier> iter = resources.keySet().iterator();
            while (iter.hasNext()) {
                Identifier id = iter.next();
                if (id.getNamespace().equals(namespace)
                    && id.getPath().startsWith(prefix)) {
                    byte[] data = resources.get(id);
                    if (data != null) {
                        ByteArrayInputStream bais
                            = new ByteArrayInputStream(data);
                        output.accept(id, () -> bais);
                    }
                }
            }
        }
    }

    /**
     * Returns a set of the namespaces of this pack's resource locations
     */
    @Override
    public Set<String> getNamespaces(PackType type) {
        TreeSet<String> namespaces = new TreeSet<String>();
        for (Identifier id : resources.keySet()) {
            namespaces.add(id.getNamespace());
        }
        return namespaces;
    }

    /**
     * Returns null because this doesn't have any metadata
     */
    @Override
    public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type)
        throws IOException {
        return null;
    }

    /**
     * Returns some manufactured information about the pack
     */
    @Override
    public PackLocationInfo location() {
        return new PackLocationInfo("laminate:synthesized",
                                    Component
                                    .literal("Laminate Synthetic Pack"),
                                    PackSource.BUILT_IN,
                                    Optional.empty());
    }
    /**
     * Does nothing because this pack has no backing files
     */
    @Override
    public void close() {
        return;
    }
}
