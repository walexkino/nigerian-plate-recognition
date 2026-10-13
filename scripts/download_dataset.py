"""Download public Drive inventory without case-insensitive filename collisions."""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
from pathlib import Path
from urllib.parse import parse_qs, urlparse
import gdown


def download(entry, destination):
    file_id = parse_qs(urlparse(entry['url']).query)['id'][0]
    suffix = Path(entry['path']).suffix.lower()
    target = destination / (file_id + suffix)
    record = dict(entry, file_id=file_id, local_path=str(target))
    try:
        if not target.exists():
            temporary = target.with_suffix(suffix + '.part')
            gdown.download(entry['url'], str(temporary), quiet=True,
                           use_cookies=False, timeout=30, retries=2)
            temporary.replace(target)
        record['status'] = 'downloaded'
    except Exception as exc:
        record.update(status='failed', error=str(exc))
    return record


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--inventory', default='outputs/audit/drive_inventory.json')
    parser.add_argument('--limit', type=int)
    args = parser.parse_args()
    entries = json.loads(Path(args.inventory).read_text())
    if args.limit:
        entries = entries[:args.limit]
    destination = Path('data/raw'); destination.mkdir(parents=True, exist_ok=True)
    records = []
    Path('outputs/audit').mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=3) as pool:
        for start in range(0, len(entries), 25):
            jobs = [pool.submit(download, entry, destination) for entry in entries[start:start+25]]
            batch_failures = 0
            for job in as_completed(jobs):
                result = job.result(); records.append(result)
                batch_failures += result['status'] == 'failed'
                Path('outputs/audit/download_manifest.json').write_text(json.dumps(records, indent=2))
            print(f'{len(records)}/{len(entries)} processed; {sum(r["status"] == "failed" for r in records)} failed', flush=True)
            if batch_failures >= 8:
                print('Stopping after repeated download failures. Resume later by rerunning this command.', flush=True)
                break
    if any(r['status'] == 'failed' for r in records):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
