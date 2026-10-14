"""Measure cold-process OCR latency; predictions are NOT ground-truth labels."""
import json
from pathlib import Path
import resource
import statistics
import subprocess
import time

root=Path(__file__).resolve().parents[2]
entries=[x for x in json.loads((root/'outputs/audit/download_manifest.json').read_text()) if x['status']=='downloaded']
entries.sort(key=lambda x:x['path'])
binary=root/'dist/macos/PlateLab.app/Contents/MacOS/PlateLab'
results=[]
for i,e in enumerate(entries):
    start=time.perf_counter()
    run=subprocess.run([str(binary),'--ocr',str(root/e['local_path'])],capture_output=True,text=True,timeout=60)
    results.append(dict(file_id=e['file_id'],name=e['path'],elapsed_ms=1000*(time.perf_counter()-start),observations=json.loads(run.stdout) if run.returncode==0 else [],error=run.stderr.strip() if run.returncode else None))
    if (i+1)%25==0: print(f'{i+1}/{len(entries)} measured',flush=True)
valid=[r['elapsed_ms'] for r in results if not r['error']]
summary=dict(images=len(entries),successful=len(valid),failed=len(entries)-len(valid),median_ms=statistics.median(valid),p95_ms=sorted(valid)[min(len(valid)-1,int(.95*len(valid)))],max_child_rss_mib=resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss/(1024*1024),notes='macOS ru_maxrss is bytes. Peak child RSS covers the CLI OCR process, not desktop WebKit processes. Each invocation launches a fresh process; includes process startup. No accuracy calculated because ground-truth labels are absent.')
(root/'outputs/audit/baseline_predictions.json').write_text(json.dumps(results,indent=2))
(root/'outputs/audit/benchmark_summary.json').write_text(json.dumps(summary,indent=2))
print(json.dumps(summary,indent=2))
