"""Local-only development preview. Native .app needs no server."""
import base64
import binascii
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
from pathlib import Path
import subprocess
import tempfile
import time
import mimetypes

ROOT=Path(__file__).resolve().parents[3]
ASSETS=ROOT/'dist/macos/PlateLab.app/Contents/Resources'
BINARY=ROOT/'dist/macos/PlateLab.app/Contents/MacOS/PlateLab'
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path=(ASSETS/self.path.split('?',1)[0].lstrip('/')).resolve()
        if not path.is_relative_to(ASSETS):self.send_error(403);return
        if path.is_dir():path=path/'index.html'
        if not path.is_file():self.send_error(404);return
        self.send_response(200);self.send_header('Content-Type',mimetypes.guess_type(path)[0] or 'application/octet-stream');self.end_headers();self.wfile.write(path.read_bytes())
    def do_POST(self):
        if self.path!='/api/recognize':self.send_error(404);return
        if self.headers.get('Origin') not in (None,'http://127.0.0.1:58070','http://localhost:58070'):self.send_error(403);return
        try:
            size=int(self.headers.get('Content-Length','0'))
            if not 0<size<12000000:raise ValueError('Invalid request size')
            body=json.loads(self.rfile.read(size));data=base64.b64decode(body['image'],validate=True)
            with tempfile.NamedTemporaryFile(suffix='.jpg') as f:
                f.write(data);f.flush();start=time.perf_counter()
                result=subprocess.run([str(BINARY),'--ocr',f.name],capture_output=True,text=True,timeout=60)
                if result.returncode:raise ValueError(result.stderr.strip())
                response=dict(observations=json.loads(result.stdout),elapsedMs=(time.perf_counter()-start)*1000)
            self.send_response(200)
        except (ValueError,KeyError,binascii.Error,subprocess.TimeoutExpired) as exc:
            response={'error':str(exc)};self.send_response(400)
        self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(json.dumps(response).encode())
if __name__=='__main__':
    print('PlateLab preview: http://127.0.0.1:58070',flush=True)
    HTTPServer(('127.0.0.1',58070),Handler).serve_forever()
