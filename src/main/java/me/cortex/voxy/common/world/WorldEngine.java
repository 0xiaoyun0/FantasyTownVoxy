/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.Nullable
 */
package me.cortex.voxy.common.world;

import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.util.TrackedObject;
import me.cortex.voxy.common.world.ActiveSectionTracker;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.VoxyInstance;
import org.jetbrains.annotations.Nullable;

public class WorldEngine {
    public static final int MAX_LOD_LAYER = 4;
    public static final int UPDATE_TYPE_BLOCK_BIT = 1;
    public static final int UPDATE_TYPE_CHILD_EXISTENCE_BIT = 2;
    public static final int UPDATE_TYPE_DONT_SAVE = 4;
    public static final int DEFAULT_UPDATE_FLAGS = 3;
    private final TrackedObject thisTracker = TrackedObject.createTrackedObject(this);
    public final SectionStorage storage;
    private final Mapper mapper;
    private final ActiveSectionTracker sectionTracker;
    private ISectionChangeCallback dirtyCallback;
    private ISectionSaveCallback saveCallback;
    volatile boolean isLive = true;
    @Nullable
    public final VoxyInstance instanceIn;
    private final AtomicInteger refCount = new AtomicInteger();
    volatile long lastActiveTime = System.currentTimeMillis();
    public static final int POS_FORMAT_VERSION = 1;
    private static final long TIMEOUT_MILLIS = 10000L;

    public void setDirtyCallback(ISectionChangeCallback callback) {
        this.dirtyCallback = callback;
    }

    public void setSaveCallback(ISectionSaveCallback callback) {
        this.saveCallback = callback;
    }

    public Mapper getMapper() {
        return this.mapper;
    }

    public boolean isLive() {
        return this.isLive;
    }

    public WorldEngine(SectionStorage storage) {
        this(storage, null);
    }

    public WorldEngine(SectionStorage storage, @Nullable VoxyInstance instance) {
        this.instanceIn = instance;
        int cacheSize = 1024;
        if (Runtime.getRuntime().maxMemory() >= 4085252096L) {
            cacheSize = 2048;
        }
        var hxz=me.cortex.voxy.hxz.HxzSettings.CONFIG;
        if(hxz.scheduling() && hxz.limitMemory) cacheSize=Math.min(cacheSize,hxz.sectionCacheMiB*4);
        this.storage = storage;
        this.mapper = new Mapper(this.storage);
        this.sectionTracker = new ActiveSectionTracker(6, storage::loadSection, cacheSize, this);
    }

    public WorldSection acquireIfExists(int lvl, int x, int y, int z) {
        if (!this.isLive) {
            throw new IllegalStateException("World is not live");
        }
        return this.sectionTracker.acquire(lvl, x, y, z, true);
    }

    public WorldSection acquire(int lvl, int x, int y, int z) {
        if (!this.isLive) {
            throw new IllegalStateException("World is not live");
        }
        return this.sectionTracker.acquire(lvl, x, y, z, false);
    }

    public WorldSection acquire(long pos) {
        if (!this.isLive) {
            throw new IllegalStateException("World is not live");
        }
        return this.sectionTracker.acquire(pos, false);
    }

    public WorldSection acquireIfExists(long pos) {
        if (!this.isLive) {
            throw new IllegalStateException("World is not live");
        }
        return this.sectionTracker.acquire(pos, true);
    }

    public static long getWorldSectionId(int lvl, int x, int y, int z) {
        return (long)lvl << 60 | (long)(y & 0xFF) << 52 | (long)(z & 0xFFFFFF) << 28 | (long)(x & 0xFFFFFF) << 4;
    }

    public static int getLevel(long id) {
        return (int)(id >> 60 & 0xFL);
    }

    public static int getX(long id) {
        return (int)(id << 36 >> 40);
    }

    public static int getY(long id) {
        return (int)(id << 4 >> 56);
    }

    public static int getZ(long id) {
        return (int)(id << 12 >> 40);
    }

    public static String pprintPos(long pos) {
        return WorldEngine.getLevel(pos) + "@[" + WorldEngine.getX(pos) + ", " + WorldEngine.getY(pos) + ", " + WorldEngine.getZ(pos) + "]";
    }

    public void markDirty(WorldSection section) {
        this.markDirty(section, 3, 0);
    }

    public void markDirty(WorldSection section, int changeState, int neighborMsk) {
        if (!this.isLive) {
            throw new IllegalStateException("World is not live");
        }
        if (section.tracker != this.sectionTracker) {
            throw new IllegalStateException("Section is not from here");
        }
        if (this.dirtyCallback != null) {
            this.dirtyCallback.accept(section, changeState, neighborMsk);
        }
        if (!section.inSaveQueue && (changeState & 4) == 0) {
            section.markDirty();
        }
    }

    public void addDebugData(List<String> debug) {
        debug.add("ACC/SCC: " + this.sectionTracker.getLoadedCacheCount() + "/" + this.sectionTracker.getSecondaryCacheSize());
    }

    public int getActiveSectionCount() {
        return this.sectionTracker.getLoadedCacheCount();
    }

    public void free() {
        if (!this.isLive) {
            throw new IllegalStateException();
        }
        this.isLive = false;
        VarHandle.fullFence();
        if (this.sectionTracker.getLoadedCacheCount() != 0) {
            throw new IllegalStateException();
        }
        this.thisTracker.free();
        try {
            this.mapper.close();
        }
        catch (Exception e) {
            Logger.error(e);
        }
        try {
            this.storage.flush();
        }
        catch (Exception e) {
            Logger.error(e);
        }
        try {
            this.storage.close();
        }
        catch (Exception e) {
            Logger.error(e);
        }
    }

    public boolean isWorldUsed() {
        if (!this.isLive) {
            throw new IllegalStateException();
        }
        return this.refCount.get() != 0 || this.sectionTracker.getLoadedCacheCount() != 0;
    }

    public boolean isWorldIdle() {
        if (this.isWorldUsed()) {
            this.lastActiveTime = System.currentTimeMillis();
            VarHandle.fullFence();
            return false;
        }
        return 10000L < System.currentTimeMillis() - this.lastActiveTime;
    }

    public void markActive() {
        if (!this.isLive) {
            throw new IllegalStateException();
        }
        this.lastActiveTime = System.currentTimeMillis();
    }

    public void keepAlive(long additionalMillis) {
        if (!this.isLive) {
            throw new IllegalStateException();
        }
        this.lastActiveTime = System.currentTimeMillis() + additionalMillis - 10000L;
    }

    public void acquireRef() {
        if (!this.isLive) {
            throw new IllegalStateException();
        }
        this.refCount.incrementAndGet();
        this.lastActiveTime = System.currentTimeMillis();
    }

    public void releaseRef() {
        if (!this.isLive) {
            throw new IllegalStateException();
        }
        if (this.refCount.decrementAndGet() < 0) {
            throw new IllegalStateException("ref count less than 0");
        }
        this.lastActiveTime = System.currentTimeMillis();
    }

    public int getRefCount() {
        return this.refCount.get();
    }

    public void saveSection(WorldSection section) {
        section.setNotDirty();
        if (this.saveCallback != null) {
            this.saveCallback.save(this, section);
        }
    }

    public static interface ISectionChangeCallback {
        public void accept(WorldSection var1, int var2, int var3);
    }

    public static interface ISectionSaveCallback {
        public void save(WorldEngine var1, WorldSection var2);
    }
}

