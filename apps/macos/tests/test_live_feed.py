"""Exercise the native live decoder against a synthetic loopback RTSP feed."""
import argparse
from pathlib import Path
import socket
import subprocess
import tempfile
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--server', required=True, type=Path, help='MediaMTX executable')
    parser.add_argument('--decoder', required=True, type=Path, help='FFmpeg executable')
    parser.add_argument('--image', required=True, type=Path, help='Synthetic image with ABC123DE plate')
    parser.add_argument('--test-binary', required=True, type=Path)
    parser.add_argument('--hold-seconds', type=int, default=0, help='Keep the synthetic feed available for a manual UI check')
    args = parser.parse_args()
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    processes = []
    with tempfile.TemporaryDirectory(prefix='platelab-rtsp-') as directory:
        config = Path(directory)/'mediamtx.yml'
        config.write_text(f'rtspAddress: 127.0.0.1:{port}\nrtspTransports: [tcp]\nrtmp: no\nhls: no\nwebrtc: no\nsrt: no\nmoq: no\npaths:\n  platelab:\n')
        url = f'rtsp://127.0.0.1:{port}/platelab'
        try:
            with (Path(directory)/'server.log').open('wb') as log:
                server = subprocess.Popen([str(args.server.resolve()), str(config)], cwd=directory, stdout=log, stderr=log)
                processes.append(server)
                deadline = time.monotonic()+10
                while True:
                    if server.poll() is not None: raise RuntimeError('Local RTSP server failed to start: '+(Path(directory)/'server.log').read_text())
                    try:
                        with socket.create_connection(('127.0.0.1', port), timeout=.2): break
                    except OSError:
                        if time.monotonic()>deadline: raise RuntimeError('Local RTSP server timed out')
                        time.sleep(.1)
                publisher = subprocess.Popen([str(args.decoder.resolve()), '-nostdin', '-hide_banner', '-loglevel', 'error',
                    '-loop', '1', '-re', '-i', str(args.image.resolve()), '-vf', 'scale=1280:1280:force_original_aspect_ratio=decrease',
                    '-c:v', 'libx264', '-preset', 'ultrafast', '-tune', 'zerolatency', '-g', '10', '-r', '10', '-an',
                    '-rtsp_transport', 'tcp', '-f', 'rtsp', url], stdout=log, stderr=log)
                processes.append(publisher)
                time.sleep(1)
                if publisher.poll() is not None: raise RuntimeError('Synthetic RTSP publisher failed: '+(Path(directory)/'server.log').read_text())
                try:
                    subprocess.run([str(args.test_binary.resolve()), str(args.decoder.resolve()), url], check=True, timeout=40)
                except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
                    print((Path(directory)/'server.log').read_text(), flush=True)
                    raise
                if args.hold_seconds > 0:
                    print('Synthetic UI test feed: '+url, flush=True)
                    time.sleep(args.hold_seconds)
        finally:
            for process in reversed(processes):
                if process.poll() is None:
                    process.terminate()
                    try: process.wait(timeout=3)
                    except subprocess.TimeoutExpired: process.kill(); process.wait()


if __name__ == '__main__':
    try: main()
    except KeyboardInterrupt: pass
