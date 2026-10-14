"""Compile native Mojmap overlays and merge the user's exact NeoVoxy/VSS binaries."""
from pathlib import Path
import os,sys,subprocess,zipfile,json,hashlib,re,shutil
from paths import setting,java_home
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
R=Path(__file__).resolve().parents[1];P=R.parent
JAVA=Path(java_home())/'bin'
VERSION='0.0.1';NAME='fantasy-town-voxy-'+VERSION+'-neoforge-mc1.21.1.jar'
BUILD='20261014-release1'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):
 with zipfile.ZipFile(p) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def write(p,data):
 with zipfile.ZipFile(p,'w',zipfile.ZIP_DEFLATED) as z:
  for n,d in sorted(data.items()):
   info=zipfile.ZipInfo(n,(2026,10,11,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,d)
(R/'build').mkdir(exist_ok=True)
base=Path(setting('HXZ_NEOVOXY',R/'reference/neovoxy-original.jar'));vss=Path(setting('HXZ_VSS',R/'reference/vss-original.jar'))
assert sha(base)=='1ba68d6b19c8020d16f93f2ddfe184f23edb7fc138d692d732ee13ecd1e0071c'
assert sha(vss)=='6abc84e84cc40030d49f974f3e40055020b5121bcc1a72193cef7a69677a7559'
mc=Path(setting('HXZ_MINECRAFT_JAR'))
sodium=Path(setting('HXZ_SODIUM_JAR'))
nested=[]
with zipfile.ZipFile(sodium) as z:
 for n in z.namelist():
  if n.endswith('.jar'):
   p=R/'build'/Path(n).name;p.write_bytes(z.read(n));nested.append(str(p))
cp=os.pathsep.join([str(mc),str(sodium),str(base),str(vss)]+nested+[p.strip() for p in Path(setting('HXZ_CLASSPATH_FILE')).read_text(encoding='utf-8').split(os.pathsep) if p.strip() and 'voxy-original' not in p and 'sodium-compile' not in p])
(R/'build/compile-classpath.txt').write_text(cp,encoding='utf-8')
out=R/'build/classes';out.mkdir(exist_ok=True)
assert out.resolve().parent==(R/'build').resolve()
for p in out.rglob('*.class'):p.unlink()
args=['-encoding','UTF-8','--release','21','-proc:none','-cp',cp,'-d',str(out)]+[str(p) for p in (R/'src/main/java').rglob('*.java')]
def runjava(tool,args,name):
 argfile=R/'build'/name;argfile.write_text('\n'.join('"'+a.replace('\\','/').replace('"','\\"')+'"' for a in args),encoding='utf-8' if tool=='javac' else 'gbk')
 command=[str(JAVA/(tool+'.exe'))]+(['-J-Duser.language=en','-J-Dfile.encoding=UTF-8'] if tool=='javac' else [])+['@'+str(argfile)]
 result=subprocess.run(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
 log=result.stdout.decode('utf-8',errors='replace');(R/'build'/(name+'.log')).write_text(log,encoding='utf-8')
 if log:print(log)
 if result.returncode:raise SystemExit('Failed '+name)
runjava('javac',args,'compile.args')
content=read(base);other=read(vss)
for n,d in other.items():
 if n in ('META-INF/MANIFEST.MF','META-INF/neoforge.mods.toml','META-INF/accesstransformer.cfg','icon.png','LICENSE','LICENSE.txt','dev/xantha/vss/VSSMod.class'):continue
 if n in ('assets/vss/lang/en_us.json','assets/vss/lang/zh_cn.json') and n in content:
  language=json.loads(content[n]);language.update(json.loads(d));content[n]=json.dumps(language,ensure_ascii=False,indent=2).encode();continue
 if n in content and content[n]!=d:raise AssertionError('Unreviewed collision '+n)
 content[n]=d
# Access transformers from both components are additive.
content['META-INF/accesstransformer.cfg']=content.get('META-INF/accesstransformer.cfg',b'')+b'\n'+other.get('META-INF/accesstransformer.cfg',b'')
for p in out.rglob('*.class'):content[p.relative_to(out).as_posix()]=p.read_bytes()
for p in (R/'src/main/resources').rglob('*'):
 if p.is_file():content[p.relative_to(R/'src/main/resources').as_posix()]=p.read_bytes()
for n in ['traversal_dev.comp','screenspace.glsl']:
 content['assets/neovoxy/shaders/lod/hierarchical/'+n]=content['assets/voxy/shaders/lod/hierarchical/'+n]
for n in ['vss.compat.mixins.json','vss.strict.mixins.json']:
 c=json.loads(content[n]);c.pop('refmap',None);content[n]=json.dumps(c,indent=2).encode()
runjava('javac',['-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(R/'build'),str(R/'tools/Adapt.java')],'adapter.args')
temp=R/'build/merged.jar';write(temp,content)
adapt=R/'build/adapted';adapt.mkdir(exist_ok=True)
assert adapt.resolve().parent==(R/'build').resolve()
for p in adapt.rglob('*.class'):p.unlink()
runjava('java',['-cp',str(R/'build')+os.pathsep+cp,'Adapt',str(temp),str(adapt)],'adapt.args')
for p in adapt.rglob('*.class'):content[p.relative_to(adapt).as_posix()]=p.read_bytes()
metadata=f'''modLoader="javafml"
loaderVersion="[4,)"
license="NeoVoxy/Voxy: All Rights Reserved; VSS: MIT; see licenses"
[[mods]]
modId="voxy"
version="{VERSION}"
displayName="Fantasy Town Voxy"
authors="Voxy/NeoVoxy contributors; VSS contributors; HXZ integration"
description="NeoVoxy rendering with switchable HXZ optimization and optional integrated VSS server sharing."
displayTest="IGNORE_SERVER_VERSION"
[modproperties.voxy]
"sodium:config_api_user"="me.cortex.voxy.client.config.VoxyConfigScreenPages"
'''
for mod,versions,side,kind in [('neoforge','[21.1.0,)','BOTH','required'),('minecraft','[1.21.1,1.21.2)','BOTH','required'),('sodium','[0.8.12,)','CLIENT','required'),('neovoxy','[0,)','BOTH','incompatible'),('vss','[0,)','BOTH','incompatible')]:
 metadata+=f'[[dependencies.voxy]]\nmodId="{mod}"\ntype="{kind}"\nversionRange="{versions}"\nordering="NONE"\nside="{side}"\n'
for n in ['voxy.mixins.json','voxy-common.mixins.json','hxz.voxy.mixins.json','vss.compat.mixins.json','vss.strict.mixins.json','vss.lostcities.mixins.json']:metadata+=f'[[mixins]]\nconfig="{n}"\n'
content['META-INF/neoforge.mods.toml']=metadata.encode()
content['licenses/VSS-MIT.txt']=(R/'licenses/VSS-MIT.txt').read_bytes()
content['licenses/NeoVoxy-original-metadata.toml']=read(base)['META-INF/neoforge.mods.toml']
content['META-INF/MANIFEST.MF']=f'Manifest-Version: 1.0\r\nImplementation-Title: Fantasy Town Voxy\r\nImplementation-Version: {VERSION}\r\nHXZ-Build: {BUILD}\r\n\r\n'.encode()
(R/'dist').mkdir(exist_ok=True);target=R/'dist'/NAME;write(target,content)
manifest={'version':VERSION,'build':BUILD,'baseline':str(base.name),'baselineSha256':sha(base),'vssSha256':sha(vss),'jarSha256':sha(target),'overlayClasses':[p.relative_to(out).as_posix() for p in out.rglob('*.class')],'bytecodeAdaptedClasses':[p.relative_to(adapt).as_posix() for p in adapt.rglob('*.class')]}
(R/'dist/build-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
(R/'dist/SHA256SUMS.txt').write_text(sha(target)+'  '+NAME+'\n')
print('BUILT '+str(target)+' '+str(target.stat().st_size)+' bytes')
