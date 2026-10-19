package net.minecraft.client.multiplayer;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** The decoder only needs these world inputs; avoid initializing game item registries. */
public final class ClientLevel {
    private final RegistryAccess registries;
    private final ResourceKey<Level> dimension;
    public ClientLevel(RegistryAccess registries, ResourceKey<Level> dimension) {
        this.registries = registries; this.dimension = dimension;
    }
    public RegistryAccess registryAccess() { return registries; }
    public ResourceKey<Level> dimension() { return dimension; }
}
