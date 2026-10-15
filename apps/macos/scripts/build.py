"""Build a self-contained native .app. No Python runtime or neural weights bundled."""
import json
import base64
from pathlib import Path
import plistlib
import platform
import shutil
import subprocess
import argparse
import os
import re

root = Path(__file__).resolve().parents[3]
release = json.loads(re.search(r'const release = (\{[^;]+\});', (root/'apps/macos/ui/release.js').read_text()).group(1))
if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', release['version']):
    raise ValueError('Release version must use MAJOR.MINOR.PATCH')
if type(release['build']) is not int or release['build'] < 1:
    raise ValueError('Release build must be a positive integer')
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=root/'dist/macos/PlateLab.app')
parser.add_argument('--without-samples', action='store_true')
parser.add_argument('--bundle-id', default='com.akinwale.platelab')
args = parser.parse_args()
app = args.output.resolve()/'Contents'
resources = app/'Resources'; binary = app/'MacOS'
resources.mkdir(parents=True, exist_ok=True); binary.mkdir(parents=True, exist_ok=True)
target = f'{platform.machine()}-apple-macosx13.0'
subprocess.run(['swiftc','-O','-target',target,str(root/'apps/macos/native/main.swift'),str(root/'apps/macos/native/Recognition.swift'),str(root/'apps/macos/native/LiveFeed.swift'),str(root/'apps/macos/native/Weather.swift'),'-o',str(binary/'PlateLab'),'-module-cache-path',str(root/'build/macos/module-cache')],check=True)
for filename in ['index.html','style.css','live.css','core.js','release.js','support.js','support-qr.svg','theme.js','app.js','weather.js','weather.css']:
    shutil.copy2(root/'apps/macos/ui'/filename,resources/filename)
# Finder and the Dock need a native multi-resolution icon, including Retina sizes.
icon_source = root/'shared/branding/platelab-icon.png'
iconset = root/'build/macos/PlateLab.iconset'
iconset.mkdir(parents=True, exist_ok=True)
for points in [16, 32, 128, 256, 512]:
    for scale in [1, 2]:
        filename = f'icon_{points}x{points}' + ('@2x' if scale == 2 else '') + '.png'
        subprocess.run(['sips', '-z', str(points*scale), str(points*scale), str(icon_source), '--out', str(iconset/filename)], check=True, capture_output=True)
subprocess.run(['iconutil', '--convert', 'icns', str(iconset), '--output', str(resources/'PlateLab.icns')], check=True)
shutil.copy2(icon_source, resources/'platelab-icon.png')
samples=[]
shutil.rmtree(resources/'samples', ignore_errors=True)
manifest=root/'outputs/audit/download_manifest.json'
if manifest.exists() and not args.without_samples:
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
decoder = os.environ.get('PLATELAB_FFMPEG') or shutil.which('ffmpeg')
if not decoder:
    try:
        import imageio_ffmpeg
        decoder = imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        pass
for filename in ['ffmpeg', 'FFmpeg-LICENSE.txt']:
    (resources/filename).unlink(missing_ok=True)
if decoder:
    shutil.copy2(decoder, resources/'ffmpeg')
    (resources/'ffmpeg').chmod(0o755)
    license_info = subprocess.run([decoder, '-L'], capture_output=True, text=True, check=True)
    (resources/'FFmpeg-LICENSE.txt').write_text(license_info.stdout + '\n' + license_info.stderr + '\nSource and licensing: https://ffmpeg.org/legal.html\n')
    subprocess.run(['codesign', '--force', '--sign', '-', str(resources/'ffmpeg')], check=True)
else:
    print('Live feeds disabled: install apps/macos/requirements-live.txt and rebuild to bundle FFmpeg.')
with (app/'Info.plist').open('wb') as f:
    plistlib.dump(dict(CFBundleName='PlateLab',CFBundleDisplayName='PlateLab',CFBundleIdentifier=args.bundle_id,CFBundleVersion=str(release['build']),CFBundleShortVersionString=release['version'],CFBundleExecutable='PlateLab',CFBundleIconFile='PlateLab.icns',CFBundlePackageType='APPL',LSMinimumSystemVersion='13.0',NSHighResolutionCapable=True,NSLocationUsageDescription='PlateLab uses your approximate location only when you request a local weather forecast.',NSLocationWhenInUseUsageDescription='Show local weather using your approximate location when you choose Use my location.'),f)
subprocess.run(['codesign','--force','--deep','--sign','-',str(app.parent)],check=True)
print(app.parent)
