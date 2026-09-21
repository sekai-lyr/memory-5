"""从参考录屏里抽帧，便于逐帧读动画。
用法: python tools/extract-video-frames.py <video> <outdir> [count]
"""
import os
import sys

import cv2


def main():
    video = sys.argv[1]
    outdir = sys.argv[2]
    count = int(sys.argv[3]) if len(sys.argv) > 3 else 10
    os.makedirs(outdir, exist_ok=True)

    cap = cv2.VideoCapture(video)
    if not cap.isOpened():
        print('cannot open', video)
        return 1
    fps = cap.get(cv2.CAP_PROP_FPS) or 0
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    print(f'{os.path.basename(video)}: {width}x{height} fps={fps:.3f} frames={total} '
          f'duration={total / fps if fps else 0:.2f}s')

    written = []
    for index in range(count):
        position = int(total * index / max(count - 1, 1))
        position = min(max(position, 0), max(total - 1, 0))
        cap.set(cv2.CAP_PROP_POS_FRAMES, position)
        ok, frame = cap.read()
        if not ok:
            print(f'  frame {position}: read failed')
            continue
        path = os.path.join(outdir, f'frame-{index:02d}-f{position:04d}.png')
        cv2.imwrite(path, frame)
        written.append((index, position, position / fps if fps else 0, path))
        print(f'  wrote {path}  (t={position / fps if fps else 0:.2f}s)')
    cap.release()
    print(f'total written: {len(written)}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
