"""Build a self-contained native .app. No Python runtime or neural weights bundled."""
import json
import base64
from pathlib import Path
import plistlib
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
app = root/'dist/PlateLab.app/Contents'
resources = app/'Resources'; binary = app/'MacOS'
resources.mkdir(parents=True, exist_ok=True); binary.mkdir(parents=True, exist_ok=True)
subprocess.run(['swiftc','-O',str(root/'app/native/PlateLab.swift'),'-o',str(binary/'PlateLab'),'-module-cache-path',str(root/'build/module-cache')],check=True)
for filename in ['index.html','style.css','live.css','core.js','app.js']:
    shutil.copy2(root/'app'/filename,resources/filename)
samples=[]
manifest=root/'outputs/audit/download_manifest.json'
if manifest.exists():
    entries=[x for x in json.loads(manifest.read_text()) if x['status']=='downloaded']
    entries.sort(key=lambda x:x['path'])
    (resources/'samples').mkdir(exist_ok=True)
    for entry in entries[::max(1,len(entries)//12)][:12]:
        filename=entry['file_id']+'.jpg'
        shutil.copy2(root/entry['local_path'],resources/'samples'/filename)
        samples.append(dict(name=entry['path'],path='data:image/jpeg;base64,'+base64.b64encode((root/entry['local_path']).read_bytes()).decode()))
(resources/'samples.json').write_text(json.dumps(samples))
(resources/'samples.js').write_text('window.DATASET_SAMPLES='+json.dumps(samples)+';')
html=(resources/'index.html').read_text().replace('<script src="core.js">','<script src="samples.js"></script><script src="core.js">')
(resources/'index.html').write_text(html)
with (app/'Info.plist').open('wb') as f:
    plistlib.dump(dict(CFBundleName='PlateLab',CFBundleDisplayName='PlateLab',CFBundleIdentifier='com.akinwale.platelab',CFBundleVersion='1',CFBundleShortVersionString='0.1.0',CFBundleExecutable='PlateLab',CFBundlePackageType='APPL',LSMinimumSystemVersion='13.0',NSHighResolutionCapable=True),f)
subprocess.run(['codesign','--force','--deep','--sign','-',str(app.parent)],check=True)
print(app.parent)
