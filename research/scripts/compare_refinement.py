"""Run paired baseline/refined OCR on local images. Does not infer ground truth."""
import argparse
import json
from pathlib import Path
import subprocess
import time

parser=argparse.ArgumentParser();parser.add_argument('--limit',type=int,default=30);args=parser.parse_args()
manifest=json.loads(Path('outputs/audit/download_manifest.json').read_text())
entries=sorted([e for e in manifest if e['status']=='downloaded'],key=lambda e:e['path'])[:args.limit]
baseline={e['file_id']:e for e in json.loads(Path('outputs/audit/baseline_predictions.json').read_text())}
results=[]
for e in entries:
 start=time.perf_counter()
 run=subprocess.run(['build/research/PlateLab-refined','--ocr',e['local_path']],capture_output=True,text=True,timeout=60)
 if run.returncode:raise RuntimeError(run.stderr)
 record=dict(file_id=e['file_id'],name=e['path'],baseline=baseline[e['file_id']]['observations'],refined=json.loads(run.stdout),elapsed_ms=(time.perf_counter()-start)*1000)
 results.append(record)
 Path('outputs/refinement/comparison.json').write_text(json.dumps(results,indent=2))
 print(e['path'],[(x['pass'],x['alternatives'][0]['text']) for x in record['refined']],flush=True)
