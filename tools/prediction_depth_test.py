from paths import artifact, VERSION
"""Compile production shader patch, then verify its actual depth/color output on the GPU."""
from pathlib import Path
import os,sys,subprocess,ctypes,struct
from paths import java_home
ROOT=Path(__file__).resolve().parents[1]
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
work=ROOT/'build/prediction-depth-test';work.mkdir(exist_ok=True)
jar=artifact()
cp=str(jar)+os.pathsep+(ROOT/'build/compile-classpath.txt').read_text(encoding='utf-8')
java=Path(java_home())/'bin'
source=ROOT/'src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag'
patched=work/'patched.frag'
for tool,args in [('javac',['-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(work),str(ROOT/'tests/PredictionDepthContractTest.java')]),('java',['-ea','-cp',str(work)+os.pathsep+cp,'PredictionDepthContractTest',str(source),str(patched)])]:
    af=work/(tool+'.args');af.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in args),encoding='utf-8' if tool=='javac' else 'gbk')
    result=subprocess.run([str(java/(tool+'.exe'))]+(['-J-Dfile.encoding=UTF-8'] if tool=='javac' else ['-Dfile.encoding=UTF-8'])+['@'+str(af)],capture_output=True)
    print((result.stdout+result.stderr).decode('utf-8',errors='replace'))
    if result.returncode:raise SystemExit(result.returncode)
sys.path.insert(0,str(ROOT.parent/'tools/python-libs'))
import glfw
from OpenGL import GL as gl
from OpenGL.GL.shaders import compileProgram,compileShader
assert glfw.init()
glfw.window_hint(glfw.VISIBLE,glfw.FALSE)
glfw.window_hint(glfw.CONTEXT_VERSION_MAJOR,4);glfw.window_hint(glfw.CONTEXT_VERSION_MINOR,5)
window=glfw.create_window(4,4,'Prediction depth unit test',None,None);assert window
glfw.make_context_current(window)
try:
    fragment=patched.read_text().replace('#version 450 core','#version 450 core\n#define EMIT_COLOUR')
    vertex='#version 450 core\nout vec2 UV;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);UV=p;gl_Position=vec4(p*2-1,0,1);}'
    program=compileProgram(compileShader(vertex,gl.GL_VERTEX_SHADER),compileShader(fragment,gl.GL_FRAGMENT_SHADER))
    gl.glUseProgram(program);gl.glBindVertexArray(gl.glGenVertexArrays(1))
    def texture(unit,values,channels=1,depth=False):
        tex=gl.glGenTextures(1);gl.glActiveTexture(gl.GL_TEXTURE0+unit);gl.glBindTexture(gl.GL_TEXTURE_2D,tex)
        gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MIN_FILTER,gl.GL_NEAREST);gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MAG_FILTER,gl.GL_NEAREST)
        gl.glTexImage2D(gl.GL_TEXTURE_2D,0,gl.GL_DEPTH_COMPONENT32F if depth else gl.GL_R32F if channels==1 else gl.GL_RGBA32F,4,4,0,gl.GL_DEPTH_COMPONENT if depth else gl.GL_RED if channels==1 else gl.GL_RGBA,gl.GL_FLOAT,(ctypes.c_float*(16*channels))(*(values*16)))
        return tex
    depth=texture(6,[1],depth=True);color=texture(7,[0,0,0,1],4)
    fbo=gl.glGenFramebuffers(1);gl.glBindFramebuffer(gl.GL_FRAMEBUFFER,fbo)
    gl.glFramebufferTexture2D(gl.GL_FRAMEBUFFER,gl.GL_DEPTH_ATTACHMENT,gl.GL_TEXTURE_2D,depth,0)
    gl.glFramebufferTexture2D(gl.GL_FRAMEBUFFER,gl.GL_COLOR_ATTACHMENT0,gl.GL_TEXTURE_2D,color,0)
    assert gl.glCheckFramebufferStatus(gl.GL_FRAMEBUFFER)==gl.GL_FRAMEBUFFER_COMPLETE
    texture(0,[.25]);texture(3,[0,1,0,1],4);texture(4,[2]);texture(5,[.8])
    gl.glUniform1i(gl.glGetUniformLocation(program,'VssPredictionOpaqueDepth'),4)
    gl.glUniform1i(gl.glGetUniformLocation(program,'VssPredictionSeedDepth'),5)
    identity=(ctypes.c_float*16)(1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1)
    for name in ('invProjMat','projMat'):gl.glUniformMatrix4fv(gl.glGetUniformLocation(program,name),1,False,identity)
    gl.glViewport(0,0,4,4);gl.glEnable(gl.GL_DEPTH_TEST);gl.glDepthFunc(gl.GL_ALWAYS)
    for enabled,scale,offset,protect,expected,label in [(0,2,-1,0,.25,'disabled retains original'),(1,2,-1,0,.25,'negative-one source'),(1,1,0,0,.625,'zero-to-one source'),(1,2,-1,1,1,'nearer prediction survives final water pass')]:
        gl.glUniform1i(gl.glGetUniformLocation(program,'VssNormalDepthBridge'),enabled)
        gl.glUniform2f(gl.glGetUniformLocation(program,'VssSourceDepthTransform'),scale,offset)
        gl.glUniform1i(gl.glGetUniformLocation(program,'VssPredictionBeforeWater'),protect)
        gl.glClearDepth(1);gl.glClearColor(1,0,0,1);gl.glClear(gl.GL_COLOR_BUFFER_BIT|gl.GL_DEPTH_BUFFER_BIT)
        gl.glDrawArrays(gl.GL_TRIANGLES,0,3);gl.glFinish()
        actual=struct.unpack('f',gl.glReadPixels(2,2,1,1,gl.GL_DEPTH_COMPONENT,gl.GL_FLOAT))[0]
        assert abs(actual-expected)<1e-6,(label,actual,expected)
        print('PASS GPU '+label+' depth='+str(actual))
finally:
    glfw.destroy_window(window);glfw.terminate()
