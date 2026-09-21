"""从截图里找出主要直线（Hough），用于在没有视觉通道时判断折页的边与夹角。
用法: python tools/hough-lines.py "shot.png" [topN]
"""
import sys
import math
from PIL import Image

try:
    import numpy as np
except Exception:  # noqa: BLE001
    np = None


def sobel_edges(gray):
    h = len(gray)
    w = len(gray[0])
    edges = []
    for y in range(1, h - 1):
        row = gray[y]
        for x in range(1, w - 1):
            gx = (gray[y - 1][x + 1] + 2 * gray[y][x + 1] + gray[y + 1][x + 1]
                  - gray[y - 1][x - 1] - 2 * gray[y][x - 1] - gray[y + 1][x - 1])
            gy = (gray[y + 1][x - 1] + 2 * gray[y + 1][x] + gray[y + 1][x + 1]
                  - gray[y - 1][x - 1] - 2 * gray[y - 1][x] - gray[y - 1][x + 1])
            edges.append((x, y, math.hypot(gx, gy)))
    return edges


def sobel_edges_np(gray):
    a = np.asarray(gray, dtype=float)
    gx = np.zeros_like(a)
    gy = np.zeros_like(a)
    gx[1:-1, 1:-1] = (a[:-2, 2:] + 2 * a[1:-1, 2:] + a[2:, 2:]
                      - a[:-2, :-2] - 2 * a[1:-1, :-2] - a[2:, :-2])
    gy[1:-1, 1:-1] = (a[2:, :-2] + 2 * a[2:, 1:-1] + a[2:, 2:]
                      - a[:-2, :-2] - 2 * a[:-2, 1:-1] - a[:-2, 2:])
    mag = np.hypot(gx, gy)
    threshold = np.percentile(mag, 92)
    ys, xs = np.where(mag >= threshold)
    return [(int(x), int(y), float(mag[y, x])) for x, y in zip(xs, ys)]


def main():
    path = sys.argv[1]
    top_n = int(sys.argv[2]) if len(sys.argv) > 2 else 12
    img = Image.open(path).convert('L')
    w, h = img.size
    gray = [[img.getpixel((x, y)) for x in range(w)] for y in range(h)]
    edges = sobel_edges_np(gray) if np is not None else sobel_edges(gray)
    print(f'{path}  {w}x{h}  edge pixels kept: {len(edges)}')
    if not edges:
        return

    diag = math.hypot(w, h)
    steps_theta = 180
    steps_rho = int(diag / 2)
    acc = {}
    for (x, y, _) in edges:
        for ti in range(steps_theta):
            theta = (ti * 180.0 / steps_theta) - 90.0
            rad = math.radians(theta)
            rho = x * math.cos(rad) + y * math.sin(rad)
            key = (ti, int(round(rho / 2)))
            acc[key] = acc.get(key, 0) + 1

    peaks = sorted(acc.items(), key=lambda item: -item[1])[:top_n * 6]
    print('\ntop Hough peaks (theta = normal direction; line angle = theta+90):')
    seen = []
    printed = 0
    for (ti, rho_bin), votes in peaks:
        theta = (ti * 180.0 / steps_theta) - 90.0
        rho = rho_bin * 2
        # 去掉邻近重复
        if any(abs(theta - t) < 6 and abs(rho - r) < 12 for t, r in seen):
            continue
        seen.append((theta, rho))
        rad = math.radians(theta)
        support = []
        for (x, y, _) in edges:
            if abs(x * math.cos(rad) + y * math.sin(rad) - rho) <= 1.6:
                support.append((x, y))
        if len(support) < 12:
            continue
        xs = [p[0] for p in support]
        ys = [p[1] for p in support]
        line_angle = theta + 90
        print(f'  votes={votes:5d}  normal={theta:+7.1f}deg  line={line_angle:+7.1f}deg  rho={rho:6.1f}  '
              f'span x {min(xs)}..{max(xs)}  y {min(ys)}..{max(ys)}  len={math.hypot(max(xs) - min(xs), max(ys) - min(ys)):.0f}')
        printed += 1
        if printed >= top_n:
            break


if __name__ == '__main__':
    main()
