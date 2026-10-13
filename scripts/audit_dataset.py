"""Inspect images incrementally; write hashes, duplicate groups, and a contact sheet."""
import collections
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps

root = Path('outputs/audit'); root.mkdir(parents=True, exist_ok=True)
inventory = json.loads((root/'drive_inventory.json').read_text())
manifest = json.loads((root/'download_manifest.json').read_text())
records = []; byte_groups = collections.defaultdict(list); pixel_groups = collections.defaultdict(list)
for entry in sorted(manifest, key=lambda x: x['path']):
    record = dict(entry)
    if entry['status'] == 'downloaded':
        path = Path(entry['local_path'])
        try:
            record['sha256'] = hashlib.file_digest(path.open('rb'), 'sha256').hexdigest()
            with Image.open(path) as im:
                im.load(); record.update(width=im.width, height=im.height, bytes=path.stat().st_size)
                rgb = im.convert('RGB')
                record['pixel_sha256'] = hashlib.sha256(f'{rgb.size}:'.encode()+rgb.tobytes()).hexdigest()
            byte_groups[record['sha256']].append(entry['path'])
            pixel_groups[record['pixel_sha256']].append(entry['path'])
        except Exception as exc:
            record.update(status='invalid', error=str(exc))
    records.append(record)
names = collections.defaultdict(list)
for entry in inventory: names[entry['path'].casefold()].append(entry['path'])
valid = [x for x in records if x['status']=='downloaded']
summary = dict(inventory_count=len(inventory), processed=len(records), valid_images=len(valid),
    errors=[x for x in records if x['status']!='downloaded'],
    total_bytes=sum(x['bytes'] for x in valid),
    resolutions=dict(collections.Counter(f'{x["width"]}x{x["height"]}' for x in valid)),
    case_collisions=[x for x in names.values() if len(x)>1],
    exact_duplicate_groups=[x for x in byte_groups.values() if len(x)>1],
    pixel_duplicate_groups=[x for x in pixel_groups.values() if len(x)>1],
    annotation_files=0, author='Akinwale Awoyele',
    provenance='Author confirmed ownership in conversation on 2026-10-13; redistribution licence not specified.')
(root/'image_manifest.json').write_text(json.dumps(records, indent=2))
(root/'summary.json').write_text(json.dumps(summary, indent=2))
if valid:
    samples=valid[::max(1,len(valid)//24)][:24]
    sheet=Image.new('RGB',(1200, len(range(0,len(samples),4))*220),'#151b27'); draw=ImageDraw.Draw(sheet)
    for i, entry in enumerate(samples):
        with Image.open(entry['local_path']) as im:
            thumb=ImageOps.contain(ImageOps.exif_transpose(im).convert('RGB'),(290,188))
            x=(i%4)*300; y=(i//4)*220; sheet.paste(thumb,(x,y))
            draw.text((x+4,y+191),entry['path'],fill='white')
    sheet.save(root/'contact_sheet.jpg')
print(json.dumps({k:v for k,v in summary.items() if k not in ('resolutions',)},indent=2))
