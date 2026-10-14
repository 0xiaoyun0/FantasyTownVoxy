"""Compile and execute the real traversal shader in a hidden OpenGL context."""
from pathlib import Path
import ctypes, re, struct, sys, zipfile
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT.parent/'tools/python-libs'))
import glfw
from OpenGL import GL as gl

baseline = '--baseline' in sys.argv
original_mode = '--original' in sys.argv or '--scheduled-original' in sys.argv
scheduled_original = '--scheduled-original' in sys.argv
jar_arg = Path(sys.argv[sys.argv.index('--jar')+1]) if '--jar' in sys.argv else None
original = zipfile.ZipFile(jar_arg or ROOT/'reference/neovoxy-original.jar') if baseline or jar_arg else None
def read(name):
    path = 'assets/voxy/shaders/' + name
    data = original.read(path).decode() if original else (ROOT/'src/main/resources'/path).read_text(encoding='utf-8')
    return re.sub(r'#import <voxy:([^>]+)>',lambda m:read(m[1]),data)

defs = {'LOCAL_SIZE_BITS':0,'MAX_ITERATIONS':5,'MAX_REQUEST_QUEUE_SIZE':50,
        'HIZ_BINDING':0,'SCENE_UNIFORM_BINDING':1,'REQUEST_QUEUE_BINDING':2,
        'RENDER_QUEUE_BINDING':3,'NODE_DATA_BINDING':4,'NODE_QUEUE_INDEX_BINDING':5,
        'NODE_QUEUE_META_BINDING':6,'NODE_QUEUE_SOURCE_BINDING':7,'NODE_QUEUE_SINK_BINDING':8,
        'RENDER_TRACKER_BINDING':9,'STATISTICS_BUFFER_BINDING':10}
source = read('lod/hierarchical/traversal_dev.comp')
# Voxy's PRINTF_processor removes these disabled debug calls in normal builds.
source = re.sub(r'\bprintf\([^;]*\);',';',source)
source = source.replace('#version 460 core','#version 460 core\n'+'\n'.join('#define %s %s'%item for item in defs.items()),1)

def program(src):
    shader = gl.glCreateShader(gl.GL_COMPUTE_SHADER)
    gl.glShaderSource(shader,src)
    gl.glCompileShader(shader)
    if not gl.glGetShaderiv(shader,gl.GL_COMPILE_STATUS):
        raise RuntimeError(gl.glGetShaderInfoLog(shader).decode())
    p = gl.glCreateProgram()
    gl.glAttachShader(p,shader)
    gl.glLinkProgram(p)
    if not gl.glGetProgramiv(p,gl.GL_LINK_STATUS):
        raise RuntimeError(gl.glGetProgramInfoLog(p).decode())
    gl.glDeleteShader(shader)
    return p

def buffer(binding,data,target=gl.GL_SHADER_STORAGE_BUFFER):
    b = gl.glGenBuffers(1)
    gl.glBindBuffer(target,b)
    gl.glBufferData(target,len(data),data,gl.GL_DYNAMIC_DRAW)
    gl.glBindBufferBase(target,binding,b)
    return b

if not glfw.init(): raise RuntimeError('No GLFW display available')
glfw.window_hint(glfw.VISIBLE,glfw.FALSE)
glfw.window_hint(glfw.CONTEXT_VERSION_MAJOR,4)
glfw.window_hint(glfw.CONTEXT_VERSION_MINOR,6)
glfw.window_hint(glfw.OPENGL_PROFILE,glfw.OPENGL_CORE_PROFILE)
window = glfw.create_window(32,32,'HXZ shader regression',None,None)
if not window: raise RuntimeError('OpenGL 4.6 unavailable')
glfw.make_context_current(window)
print('GPU:',gl.glGetString(gl.GL_RENDERER).decode())
failed = 0
try:
    for reverse in (False,True):
        variant = source.replace('#version 460 core','#version 460 core\n#define USE_REVERSE_Z',1) if reverse else source
        p = program(variant)
        gl.glDeleteProgram(p)
    print('PASS real traversal shader compiles with standard and reverse depth')
    probe = source.replace('void main()', 'void originalMain()') + '''
layout(binding=11,std430) buffer TestResult {uint result[];};
void main() {
    UnpackedNode n;
    n.nodeId=0u; n.rawPos=uvec2(0); n.pos=ivec3(0); n.lodLevel=0u;
    n.flags=0u; n.meshPtr=NULL_MESH; n.childPtr=NULL_NODE;
    enqueueSelfForRender(n);
    enqueueChildren(n);
    setupScreenspace(n);
    result[0] = shouldDecend() ? 1u : 0u;
    result[1] = nodeQueueMetadata[1].w;
}
'''
    p = program(probe)
    uniform = bytearray(224)
    # Perspective matrix: the test box touches the camera plane (w=0).
    matrix = [1.,0,0,0, 0,1.,0,0, 0,0,-1.,-1., 0,0,-0.1,0]
    struct.pack_into('16f',uniform,0,*matrix)
    struct.pack_into('3f',uniform,80,16,16,0)
    struct.pack_into('f',uniform,92,0.01)
    struct.pack_into('4I',uniform,192,10,1,0,16 if scheduled_original else 0 if original_mode else 31)
    struct.pack_into('f',uniform,212,-1)
    ids=[buffer(1,bytes(uniform),gl.GL_UNIFORM_BUFFER),buffer(9,bytes(16))]
    rendered=buffer(3,bytes(64)); ids.append(rendered)
    result=buffer(11,bytes(16)); ids.append(result)
    ids.extend([buffer(6,bytes(80)),buffer(8,bytes(64))])
    gl.glUseProgram(p)
    gl.glDispatchCompute(1,1,1)
    gl.glMemoryBarrier(gl.GL_BUFFER_UPDATE_BARRIER_BIT)
    gl.glFinish()
    for b,offset,label,expected in ((rendered,0,'mesh sentinel branch',1 if original_mode else 0),
                              (result,0,'camera-plane refinement branch',0 if original_mode else 1),
                              (result,4,'child sentinel branch',1 if original_mode else 0)):
        gl.glBindBuffer(gl.GL_SHADER_STORAGE_BUFFER,b)
        value=struct.unpack('I',bytes(gl.glGetBufferSubData(gl.GL_SHADER_STORAGE_BUFFER,offset,4)))[0]
        print(('PASS ' if value==expected else 'FAIL ')+label+' (got %s)'%value)
        failed += value != expected
    gl.glDeleteBuffers(len(ids),ids)
    gl.glDeleteProgram(p)
    # Loaded parents must not flash as huge cubes while their children are pending.
    # Exercise the actual submission path, including a small acceptable coarse mesh.
    fallback = source.replace('void main()', 'void originalMain()') + '''
layout(binding=11,std430) buffer TestResult {uint result[];};
void main() {
    UnpackedNode n;
    n.nodeId=0u; n.rawPos=uvec2(0); n.pos=ivec3(0); n.lodLevel=4u;
    n.flags=0u; n.meshPtr=7u; n.childPtr=NULL_NODE;
    setupScreenspace(n);
    enqueueSelfForRender(n);
    result[0]=renderQueueIndex;
    renderQueueIndex=0u;
    n.pos=ivec3(0,0,-4); n.lodLevel=1u;
    setupScreenspace(n);
    screenSize=0.02f;
    enqueueSelfForRender(n);
    result[1]=renderQueueIndex;
    renderQueueIndex=0u;
    n.pos=ivec3(0); n.lodLevel=0u;
    setupScreenspace(n);
    enqueueSelfForRender(n);
    result[2]=renderQueueIndex;
}
'''
    p=program(fallback)
    struct.pack_into('f',uniform,208,0.04)
    ids=[buffer(1,bytes(uniform),gl.GL_UNIFORM_BUFFER),buffer(9,bytes(16)),buffer(3,bytes(64))]
    result=buffer(11,bytes(16)); ids.append(result)
    gl.glUseProgram(p); gl.glDispatchCompute(1,1,1)
    gl.glMemoryBarrier(gl.GL_BUFFER_UPDATE_BARRIER_BIT); gl.glFinish()
    gl.glBindBuffer(gl.GL_SHADER_STORAGE_BUFFER,result)
    counts=struct.unpack('3I',bytes(gl.glGetBufferSubData(gl.GL_SHADER_STORAGE_BUFFER,0,12)))
    for value,expected,label in zip(counts,((1,1,1) if original_mode else (0,1,1)),('coarse-parent limit branch','acceptable low-detail parent remains drawable','original one-block geometry remains drawable')):
        print(('PASS ' if value==expected else 'FAIL ')+label+f' (got {value})'); failed+=value!=expected
    gl.glDeleteBuffers(len(ids),ids); gl.glDeleteProgram(p)
    # All lanes contend for a tiny per-frame budget. A soft check followed by
    # atomicAdd can overshoot it and expose stale queue slots to the CPU.
    concurrent = source.replace('#define LOCAL_SIZE_BITS 0','#define LOCAL_SIZE_BITS 6')
    concurrent = concurrent.replace('void main()', 'void originalMain()') + '''
void main() {
    UnpackedNode n;
    n.nodeId=gl_GlobalInvocationID.x; n.rawPos=uvec2(n.nodeId,0);
    n.pos=ivec3(0); n.lodLevel=0u; n.flags=0u;
    n.meshPtr=NULL_MESH; n.childPtr=NULL_NODE;
    barrier();
    addRequest(n);
}
'''
    p = program(concurrent)
    struct.pack_into('I',uniform,200,2)
    ids=[buffer(1,bytes(uniform),gl.GL_UNIFORM_BUFFER),buffer(4,bytes(64*16))]
    requests=buffer(2,bytes(8+50*8)); ids.append(requests)
    gl.glUseProgram(p)
    gl.glDispatchCompute(1,1,1)
    gl.glMemoryBarrier(gl.GL_BUFFER_UPDATE_BARRIER_BIT)
    gl.glFinish()
    gl.glBindBuffer(gl.GL_SHADER_STORAGE_BUFFER,requests)
    count=struct.unpack('I',bytes(gl.glGetBufferSubData(gl.GL_SHADER_STORAGE_BUFFER,0,4)))[0]
    correct=(2<=count<=64) if original_mode and not scheduled_original else count==2
    print(('PASS ' if correct else 'FAIL ')+f'GPU request branch, original={original_mode} (got {count})')
    failed += not correct
    gl.glDeleteBuffers(len(ids),ids)
    gl.glDeleteProgram(p)
    if not baseline:
        hidden=source.replace('#define LOCAL_SIZE_BITS 0','#define LOCAL_SIZE_BITS 6')
        hidden=hidden.replace('void main()', 'void originalMain()')+'''
void main() {
    UnpackedNode n;
    n.nodeId=gl_GlobalInvocationID.x; n.rawPos=uvec2(n.nodeId,0);
    n.pos=ivec3(0); n.lodLevel=4u; n.flags=0u;
    n.meshPtr=NULL_MESH; n.childPtr=NULL_NODE;
    barrier(); traverse(n);
}
'''
        p=program(hidden)
        for mode,expected in ((0,0),(1,2),(2,2)):
            struct.pack_into('4I',uniform,192,10,1,8,31)
            struct.pack_into('f',uniform,212,-1)
            struct.pack_into('2I',uniform,216,2,mode)
            for plane in range(6):struct.pack_into('4f',uniform,96+plane*16,0,0,0,-1)
            ids=[buffer(1,bytes(uniform),gl.GL_UNIFORM_BUFFER),buffer(4,bytes(64*16)),buffer(9,bytes(64*4)),buffer(3,bytes(64))]
            requests=buffer(2,bytes(8+50*8));ids.append(requests)
            gl.glUseProgram(p);gl.glDispatchCompute(1,1,1);gl.glMemoryBarrier(gl.GL_BUFFER_UPDATE_BARRIER_BIT);gl.glFinish()
            gl.glBindBuffer(gl.GL_SHADER_STORAGE_BUFFER,requests)
            count=struct.unpack('I',bytes(gl.glGetBufferSubData(gl.GL_SHADER_STORAGE_BUFFER,0,4)))[0]
            print(('PASS ' if count==expected else 'FAIL ')+f'hidden mode {mode} budget (got {count}, expected {expected})');failed+=count!=expected
            gl.glBindBuffer(gl.GL_SHADER_STORAGE_BUFFER,ids[3]);draws=struct.unpack('I',bytes(gl.glGetBufferSubData(gl.GL_SHADER_STORAGE_BUFFER,0,4)))[0]
            assert draws==0,'Hidden prefetch must never submit geometry'
            gl.glDeleteBuffers(len(ids),ids)
        gl.glDeleteProgram(p)
finally:
    glfw.destroy_window(window)
    glfw.terminate()
sys.exit(1 if failed else 0)
