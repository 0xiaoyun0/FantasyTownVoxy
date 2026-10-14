/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.dimension.DimensionType
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package me.cortex.voxy.commonImpl;

import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.commonImpl.IWorldGetIdentifier;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.VoxyInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WorldIdentifier {
    private static final ResourceKey<DimensionType> NULL_DIM_KEY = ResourceKey.create((ResourceKey)Registries.DIMENSION_TYPE, (ResourceLocation)ResourceLocation.parse((String)"voxy:null_dimension_id"));
    public final ResourceKey<Level> key;
    public final long biomeSeed;
    public final String backendNamespace;
    public final ResourceKey<DimensionType> dimension;
    private final transient long hashCode;
    @Nullable
    transient WeakReference<WorldEngine> cachedEngineObject;

    public WorldIdentifier(@NotNull ResourceKey<Level> key, long biomeSeed, @Nullable ResourceKey<DimensionType> dimension) {
        this(key,biomeSeed,dimension, "");
    }
    public WorldIdentifier(ResourceKey<Level> key,long biomeSeed,ResourceKey<DimensionType> dimension,String namespace) {
        this.backendNamespace=namespace == null ? "" : namespace;
        if (key == null) {
            throw new IllegalStateException("Key cannot be null");
        }
        dimension = dimension == null ? NULL_DIM_KEY : dimension;
        this.key = key;
        this.biomeSeed = biomeSeed;
        this.dimension = dimension;
        this.hashCode = WorldIdentifier.mixStafford13(WorldIdentifier.registryKeyHashCode(key)) ^ WorldIdentifier.mixStafford13(WorldIdentifier.registryKeyHashCode(dimension)) ^ WorldIdentifier.mixStafford13(biomeSeed) ^ (backendNamespace.isEmpty()?0:WorldIdentifier.mixStafford13(backendNamespace.hashCode()));
    }

    public int hashCode() {
        return (int)this.hashCode;
    }

    public boolean equals(Object obj) {
        if (obj instanceof WorldIdentifier) {
            WorldIdentifier other = (WorldIdentifier)obj;
            return other.backendNamespace.equals(this.backendNamespace) && other.hashCode == this.hashCode && other.biomeSeed == this.biomeSeed && WorldIdentifier.equal(other.key, this.key) && WorldIdentifier.equal(other.dimension, this.dimension);
        }
        return false;
    }

    private static <T> boolean equal(ResourceKey<T> a, ResourceKey<T> b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.registry().equals((Object)b.registry()) && a.location().equals((Object)b.location());
    }

    public WorldEngine getOrCreateEngine() {
        VoxyInstance instance = VoxyCommon.getInstance();
        if (instance == null) {
            this.cachedEngineObject = null;
            return null;
        }
        WorldEngine engine = instance.getOrCreate(this);
        if (engine == null) {
            throw new IllegalStateException("Engine null on creation");
        }
        return engine;
    }

    public WorldEngine getNullable() {
        VoxyInstance instance = VoxyCommon.getInstance();
        if (instance == null) {
            this.cachedEngineObject = null;
            return null;
        }
        return instance.getNullable(this);
    }

    public static WorldIdentifier of(Level level) {
        if (level == null) {
            return null;
        }
        return ((IWorldGetIdentifier)level).voxy$getIdentifier();
    }

    public static WorldEngine ofEngine(Level level) {
        WorldIdentifier id = WorldIdentifier.of(level);
        if (id == null) {
            return null;
        }
        return id.getOrCreateEngine();
    }

    public static WorldEngine ofEngineNullable(Level level) {
        WorldIdentifier id = WorldIdentifier.of(level);
        if (id == null) {
            return null;
        }
        return id.getNullable();
    }

    public static long mixStafford13(long seed) {
        seed += 918759875987111L;
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }

    public long getLongHash() {
        return this.hashCode;
    }

    private static long registryKeyHashCode(ResourceKey<?> key) {
        ResourceLocation A2 = key.registry();
        ResourceLocation B2 = key.location();
        int a = A2 == null ? 0 : A2.hashCode();
        int b = B2 == null ? 0 : B2.hashCode();
        return Integer.toUnsignedLong(a) << 32 | Integer.toUnsignedLong(b);
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public String getWorldId() {
        return WorldIdentifier.getWorldId(this);
    }

    public static String getWorldId(WorldIdentifier identifier) {
        String data = identifier.biomeSeed + identifier.key.toString();
        if (!identifier.backendNamespace.isEmpty()) data += "|hxz-neovoxy-v1|" + identifier.backendNamespace + "|" + identifier.dimension;
        try {
            return WorldIdentifier.bytesToHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes())).substring(0, 32);
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

