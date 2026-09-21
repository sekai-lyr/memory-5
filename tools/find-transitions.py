"""在录屏里自动找出"画面变化最剧烈"的时刻（翻页瞬间），并抽帧。
用法: python tools/find-transitions.py <video> <outdir> [peaks]
"""
import os
import sys

import cv2
import numpy as np


def main():
    video = sys.argv[1]
    outdir = sys.argv[2]
    peak_count = int(sys.argv[3]) if len(sys.argv) > 3 else 3
    os.makedirs(outdir, exist_ok=True)

    cap = cv2.VideoCapture(video)
    if not cap.isOpened():
        print('cannot open', video)
        return 1
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    print(f'{os.path.basename(video)}: frames={total} fps={fps:.2f}')

    previous = None
    diffs = []
    index = 0
    while True:
        ok, frame = cap.read()
        if not ok:
            break
        gray = cv2.cvtColor(cv2.resize(frame, (160, 93)), cv2.COLOR_BGR2GRAY).astype(np.float32)
        if previous is not None:
            diffs.append((float(np.mean(np.abs(gray - previous))), index))
        previous = gray
        index += 1
    cap.release()

    diffs_sorted = sorted(diffs, reverse=True)
    chosen = []
    for value, frame_index in diffs_sorted:
        if all(abs(frame_index - other) > int(fps * 1.2) for other in chosen):
            chosen.append(frame_index)
        if len(chosen) >= peak_count:
            break
    chosen.sort()
    print('strongest change moments:', [(f, round(f / fps, 2)) for f in chosen])

    cap = cv2.VideoCapture(video)
    written = []
    for peak in chosen:
        for offset in (-9, -5, -2, 0, 2, 5, 9):
            frame_index = max(0, min(total - 1, peak + offset))
            cap.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
            ok, frame = cap.read()
            if not ok:
                continue
            path = os.path.join(outdir, f't{frame_index / fps:06.2f}-f{frame_index:04d}.png')
            cv2.imwrite(path, frame)
            written.append((frame_index / fps, path))
    cap.release()
    print(f'wrote {len(written)} frames into {outdir}')
    for stamp, path in written[:6]:
        print(f'  t={stamp:6.2f}s  {os.path.basename(path)}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
