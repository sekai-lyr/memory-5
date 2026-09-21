"""量参考图里的主要直线段（面板竖边 / 斜边），用于判断折页朝向与角度。
用法: python tools/measure-panels.py <image> [scale] [topN]
"""
import math
import sys

import cv2
import numpy as np


def main():
    path = sys.argv[1]
    scale = float(sys.argv[2]) if len(sys.argv) > 2 else 3.0
    top_n = int(sys.argv[3]) if len(sys.argv) > 3 else 18

    img = cv2.imread(path)
    if img is None:
        # OpenCV 在 Windows 上读不了非 ASCII 路径，改用 imdecode
        try:
            with open(path, 'rb') as handle:
                img = cv2.imdecode(np.frombuffer(handle.read(), dtype=np.uint8), cv2.IMREAD_COLOR)
        except OSError as error:
            print('cannot read', path, error)
            return 1
    if img is None:
        print('cannot read', path)
        return 1
    h, w = img.shape[:2]
    big = cv2.resize(img, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    gray = cv2.cvtColor(big, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (3, 3), 0)
    bright = cv2.convertScaleAbs(gray, alpha=2.2, beta=-90)   # 拉开暗部对比
    edges = cv2.Canny(bright, 40, 120, apertureSize=3)
    print(f'{path}  {w}x{h}  scale={scale}  edge px={int(edges.sum() / 255)}')

    min_len = int(min(big.shape[:2]) * 0.16)
    lines = cv2.HoughLinesP(edges, 1, np.pi / 360, threshold=40, minLineLength=min_len, maxLineGap=6)
    if lines is None:
        print('no lines')
        return 1

    segments = []
    for line in lines.reshape(-1, 4):
        x1, y1, x2, y2 = [float(v) / scale for v in line]
        length = math.hypot(x2 - x1, y2 - y1)
        angle = math.degrees(math.atan2(y2 - y1, x2 - x1))
        # 归一化到 [-90, 90)
        while angle >= 90:
            angle -= 180
        while angle < -90:
            angle += 180
        segments.append((length, angle, (round(x1), round(y1)), (round(x2), round(y2))))
    segments.sort(reverse=True)

    print('\nlongest segments (angle 0 = horizontal, ±90 = vertical):')
    kept = []
    for length, angle, p1, p2 in segments:
        mid = ((p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2)
        if any(abs(angle - a) < 4 and math.hypot(mid[0] - m[0], mid[1] - m[1]) < 18 for a, m in kept):
            continue
        kept.append((angle, mid))
        print(f'  len={length:6.1f}  angle={angle:+7.1f}deg  {p1} -> {p2}   mid=({mid[0]:.0f},{mid[1]:.0f})')
        if len(kept) >= top_n:
            break

    vertical = [s for s in segments if abs(abs(s[1]) - 90) < 12]
    slanted = [s for s in segments if 8 < abs(s[1]) < 75]
    print(f'\nvertical-ish segments: {len(vertical)}  (top 6 x positions)')
    for length, angle, p1, p2 in vertical[:6]:
        print(f'  len={length:6.1f} angle={angle:+6.1f} x={p1[0]}..{p2[0]} y={p1[1]}..{p2[1]}')
    print(f'slanted segments: {len(slanted)}  (top 8 - these are the receding panel edges)')
    for length, angle, p1, p2 in slanted[:8]:
        direction = 'right-down' if angle > 0 else 'right-up'
        print(f'  len={length:6.1f} angle={angle:+6.1f} ({direction}) {p1} -> {p2}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
