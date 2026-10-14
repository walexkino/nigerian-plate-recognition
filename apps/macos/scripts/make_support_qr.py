"""Generate and verify the donation QR as a self-contained SVG asset."""
from pathlib import Path
import re
import cv2
import numpy as np

root = Path(__file__).resolve().parents[3]
address = re.search(r"const address='([^']+)'", (root/'apps/macos/ui/support.js').read_text()).group(1)
uri = 'bitcoin:'+address
matrix = cv2.QRCodeEncoder_create().encode(uri)
matrix = cv2.copyMakeBorder(matrix, 4, 4, 4, 4, cv2.BORDER_CONSTANT, value=255)
preview = cv2.resize(matrix, None, fx=10, fy=10, interpolation=cv2.INTER_NEAREST)
decoded, _, _ = cv2.QRCodeDetector().detectAndDecode(preview)
if decoded != uri:
    raise RuntimeError('Donation QR failed verification')
height, width = matrix.shape
cells = ' '.join(f'M{x},{y}h1v1h-1z' for y,x in np.argwhere(matrix==0))
svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" role="img" aria-label="Bitcoin donation QR code"><rect width="{width}" height="{height}" fill="white"/><path d="{cells}" fill="#101719"/></svg>\n'
(root/'apps/macos/ui/support-qr.svg').write_text(svg)
print('Verified QR:', uri)
