/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
 *  it.unimi.dsi.fastutil.longs.LongOpenHashSet
 *  net.minecraft.client.Minecraft
 *  org.joml.Matrix4f
 *  org.joml.Vector3f
 *  org.joml.Vector3fc
 *  org.joml.Vector3i
 *  org.lwjgl.opengl.ARBDirectStateAccess
 *  org.lwjgl.opengl.GL15
 *  org.lwjgl.opengl.GL30
 *  org.lwjgl.opengl.GL30C
 *  org.lwjgl.opengl.GL31
 *  org.lwjgl.opengl.GL42
 *  org.lwjgl.system.MemoryUtil
 */
package me.cortex.voxy.client.core.rendering;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.client.core.AbstractRenderPipeline;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlVertexArray;
import me.cortex.voxy.client.core.gl.shader.AutoBindingShader;
import me.cortex.voxy.client.core.gl.shader.IShaderProcessor;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.util.SharedIndexBuffer;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.Logger;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL42;
import org.lwjgl.system.MemoryUtil;

public class ChunkBoundRenderer {
    private static final int INIT_MAX_CHUNK_COUNT = 4096;
    private GlBuffer chunkPosBuffer = new GlBuffer(32768L);
    private final GlBuffer uniformBuffer = new GlBuffer(128L);
    private final Long2IntOpenHashMap chunk2idx = new Long2IntOpenHashMap(4096);
    private long[] idx2chunk = new long[4096];
    private final Shader rasterShader;
    private final LongOpenHashSet addQueue = new LongOpenHashSet();
    private final LongOpenHashSet remQueue = new LongOpenHashSet();
    private final AbstractRenderPipeline pipeline;

    public ChunkBoundRenderer(AbstractRenderPipeline pipeline) {
        this.chunk2idx.defaultReturnValue(-1);
        this.pipeline = pipeline;
        Object vert = ShaderLoader.parse("voxy:chunkoutline/outline.vsh");
        String taa = pipeline.taaFunction("getTAA");
        if (taa != null) {
            vert = (String)vert + "\n\n\n" + taa;
        }
        this.rasterShader = Shader.makeAuto(new IShaderProcessor[0]).addSource(ShaderType.VERTEX, (String)vert).defineIf("TAA", taa != null).defineIf("USE_SODIUM_EXTRA_CULLING", false).add(ShaderType.FRAGMENT, "voxy:chunkoutline/outline.fsh").compile().ubo(0, this.uniformBuffer).ssbo(1, this.chunkPosBuffer);
    }

    public void addSection(long pos) {
        this.remQueue.remove(pos);
        if (!this.chunk2idx.containsKey(pos)) {
            this.addQueue.add(pos);
        }
    }

    public void removeSection(long pos) {
        this.addQueue.remove(pos);
        if (this.chunk2idx.containsKey(pos)) {
            this.remQueue.add(pos);
        }
    }

    public void render(Viewport<?> viewport) {
        long ptr;
        // The viewport survives reloads and may have an old world's depth mask.
        // Clear even when no visible sections remain after reset or unloading.
        viewport.depthBoundingBuffer.clear(0.0f);
        if (!this.remQueue.isEmpty()) {
            this.remQueue.forEach(this::_remPos);
            this.remQueue.clear();
        }
        if (this.chunk2idx.isEmpty() && this.addQueue.isEmpty()) {
            return;
        }
        long matPtr = ptr = UploadStream.INSTANCE.upload(this.uniformBuffer, 0L, 128L);
        float renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
        int sx = (int)viewport.cameraX;
        int sy = (int)viewport.cameraY;
        int sz = (int)viewport.cameraZ;
        new Vector3i(sx, sy, sz).getToAddress(ptr += 64L);
        Vector3f negInnerSec = new Vector3f((float)(viewport.cameraX - (double)sx), (float)(viewport.cameraY - (double)sy), (float)(viewport.cameraZ - (double)sz));
        negInnerSec.getToAddress(ptr += 16L);
        viewport.MVP.translate((Vector3fc)negInnerSec.negate(), new Matrix4f()).getToAddress(matPtr);
        MemoryUtil.memPutFloat((long)(ptr += 12L), (float)renderDistance);
        ptr += 4L;
        UploadStream.INSTANCE.commit();
        GL30C.glFrontFace((int)2304);
        GL30C.glEnable((int)2884);
        GL30C.glEnable((int)2929);
        GL30C.glDepthFunc((int)516);
        GL30.glBindVertexArray((int)GlVertexArray.STATIC_VAO);
        viewport.depthBoundingBuffer.bind();
        this.rasterShader.bind();
        GL15.glBindBuffer((int)34963, (int)SharedIndexBuffer.INSTANCE_BB_BYTE.id());
        this.pipeline.bindUniforms();
        int count = this.chunk2idx.size();
        if (count > 32) {
            GL31.glDrawElementsInstanced((int)4, (int)1152, (int)5121, (long)0L, (int)(count / 32));
        }
        if (count % 32 != 0) {
            GL42.glDrawElementsInstancedBaseInstance((int)4, (int)(36 * (count % 32)), (int)5121, (long)0L, (int)1, (int)(count / 32 * 32));
        }
        GL30C.glFrontFace((int)2305);
        GL30C.glDepthFunc((int)515);
        GL30C.glEnable((int)2884);
        GL30C.glEnable((int)2929);
        if (!this.addQueue.isEmpty()) {
            this.addQueue.forEach(this::_addPos);
            this.addQueue.clear();
            UploadStream.INSTANCE.commit();
        }
    }

    private void _remPos(long pos) {
        int idx = this.chunk2idx.remove(pos);
        if (idx == -1) {
            Logger.warn("Chunk not in map: " + pos);
            return;
        }
        if (idx == this.chunk2idx.size()) {
            return;
        }
        if (this.idx2chunk[idx] != pos) {
            throw new IllegalStateException();
        }
        long ePos = this.idx2chunk[this.chunk2idx.size()];
        if (this.chunk2idx.put(ePos, idx) == -1) {
            throw new IllegalStateException();
        }
        this.idx2chunk[idx] = ePos;
        this.put(idx, ePos);
    }

    private void _addPos(long pos) {
        if (this.chunk2idx.containsKey(pos)) {
            Logger.warn("Chunk already in map: " + pos);
            return;
        }
        this.ensureSize1();
        int idx = this.chunk2idx.size();
        this.chunk2idx.put(pos, idx);
        this.idx2chunk[idx] = pos;
        this.put(idx, pos);
    }

    private void ensureSize1() {
        if (this.chunk2idx.size() < this.idx2chunk.length) {
            return;
        }
        UploadStream.INSTANCE.commit();
        int size = (int)((double)this.idx2chunk.length * 1.5);
        Logger.info("Resizing chunk position buffer to: " + size);
        GlBuffer old = this.chunkPosBuffer;
        this.chunkPosBuffer = new GlBuffer((long)size * 8L);
        ARBDirectStateAccess.glCopyNamedBufferSubData((int)old.id, (int)this.chunkPosBuffer.id, (long)0L, (long)0L, (long)old.size());
        old.free();
        long[] old2 = this.idx2chunk;
        this.idx2chunk = new long[size];
        System.arraycopy(old2, 0, this.idx2chunk, 0, old2.length);
        ((AutoBindingShader)this.rasterShader).ssbo(1, this.chunkPosBuffer);
    }

    private void put(int idx, long pos) {
        long ptr2 = UploadStream.INSTANCE.upload(this.chunkPosBuffer, 8L * (long)idx, 8L);
        MemoryUtil.memPutInt((long)ptr2, (int)((int)(pos & 0xFFFFFFFFL)));
        MemoryUtil.memPutInt((long)(ptr2 += 4L), (int)((int)(pos >>> 32 & 0xFFFFFFFFL)));
    }

    public void reset() {
        this.chunk2idx.clear();
        this.addQueue.clear();
        this.remQueue.clear();
    }

    public void free() {
        this.rasterShader.free();
        this.uniformBuffer.free();
        this.chunkPosBuffer.free();
    }
}
