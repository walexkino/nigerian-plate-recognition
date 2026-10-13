"""Create labelled synthetic road motion, NOT a highway evaluation dataset."""
import json
import math
from pathlib import Path
import cv2
import numpy as np

manifest=json.loads(Path('outputs/audit/download_manifest.json').read_text())
entry=next(e for e in manifest if e['path']=='_MG_2702.JPG' and e['status']=='downloaded')
source=cv2.imread(entry['local_path'])
if source is None:raise SystemExit('Source image missing')
out=Path('outputs/demo');out.mkdir(parents=True,exist_ok=True)
video=out/'synthetic-road-demonstration.mp4'
w,h,fps,seconds=960,640,20,12
writer=cv2.VideoWriter(str(video),cv2.VideoWriter_fourcc(*'avc1'),fps,(w,h))
if not writer.isOpened():raise SystemExit('H.264 encoder unavailable')
# Deliberately preserve the source photograph as an inset rather than inventing vehicle pixels.
for frame_no in range(fps*seconds):
    t=frame_no/fps
    frame=np.full((h,w,3),(37,48,43),np.uint8)
    cv2.fillConvexPoly(frame,np.array([[350,130],[610,130],[910,640],[50,640]]),(52,54,56))
    for offset in range(-4,7):
        y=int(130+((offset*95+t*110)%510))
        spread=(y-130)*.26
        for x in [480-spread,480+spread]:
            cv2.line(frame,(int(x),y),(int(x),min(y+38,h)),(160,164,157),4)
    scale=1.0+.035*math.sin(t*.6)
    photo=cv2.resize(source,None,fx=scale,fy=scale)
    ph,pw=photo.shape[:2];x=int((w-pw)/2+26*math.sin(t));y=int(135+7*math.cos(t*.7))
    frame[y:y+ph,x:x+pw]=photo
    # One artificial strip over part of the plate, with an explicit on-screen label.
    occluded=4<=t<8
    if occluded:
        left=x+int(.477*pw);top=y+int(.573*ph)
        cv2.rectangle(frame,(left,top),(left+max(4,int(.020*pw)),top+int(.055*ph)),(28,29,29),-1)
    cv2.rectangle(frame,(0,0),(w,115),(20,26,24),-1)
    cv2.putText(frame,'SYNTHETIC ROAD DEMONSTRATION',(26,35),cv2.FONT_HERSHEY_SIMPLEX,.8,(156,225,184),2,cv2.LINE_AA)
    cv2.putText(frame,'Dataset photo inset + simulated motion; not real highway footage',(26,67),cv2.FONT_HERSHEY_SIMPLEX,.5,(220,223,218),1,cv2.LINE_AA)
    cv2.putText(frame,'Artificial plate occluder active' if occluded else 'Source photograph visible',(26,94),cv2.FONT_HERSHEY_SIMPLEX,.5,(106,192,242) if occluded else (184,208,191),1,cv2.LINE_AA)
    cv2.putText(frame,f'{t:04.1f}s | Akinwale Awoyele | Demonstration only',(26,620),cv2.FONT_HERSHEY_SIMPLEX,.5,(220,223,218),1,cv2.LINE_AA)
    writer.write(frame)
writer.release()
cap=cv2.VideoCapture(str(video));count=int(cap.get(cv2.CAP_PROP_FRAME_COUNT));cap.release()
if count!=fps*seconds:raise SystemExit(f'Expected {fps*seconds} frames, got {count}')
(out/'video_manifest.json').write_text(json.dumps(dict(file=video.name,source_file_id=entry['file_id'],source_name=entry['path'],fps=fps,frames=count,duration_seconds=seconds,synthetic=True,transforms=['translation','scaling','road illustration','artificial opaque strip from 4 to 8 seconds'],valid_for='Video input and uncertainty demonstration only; not highway validation'),indent=2))
print(f'{video}: {count} frames, {video.stat().st_size} bytes')
