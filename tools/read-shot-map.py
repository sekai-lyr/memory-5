"""把截图按【相对】亮度渲染成字符地图，用于在无视觉通道时读结构。
用法: python tools/read-shot-map.py "shot.png" [cols] [rows]
"""
import sys
from PIL import Image


def main():
    path = sys.argv[1]
    cols = int(sys.argv[2]) if len(sys.argv) > 2 else 100
    rows = int(sys.argv[3]) if len(sys.argv) > 3 else 34
    img = Image.open(path).convert('RGB')
    w, h = img.size
    px = img.load()

    lum = [[0.299 * px[x, y][0] + 0.587 * px[x, y][1] + 0.114 * px[x, y][2] for x in range(w)] for y in range(h)]
    flat = sorted(v for row in lum for v in row)
    lo = flat[int(len(flat) * 0.02)]
    hi = flat[int(len(flat) * 0.98)]
    print(f'{path}  {w}x{h}  luminance p2={lo:.1f} p98={hi:.1f}')
    glyphs = ' .:-=+*#%@'

    print(f'\nnormalized luminance map ({cols}x{rows}), "{glyphs[1]}"=darkest "{glyphs[-1]}"=brightest')
    header = '   ' + ''.join(str((i // 10) % 10) if i % 10 == 0 else ' ' for i in range(cols))
    print(header)
    for gy in range(rows):
        y0 = int(gy * h / rows)
        y1 = max(int((gy + 1) * h / rows), y0 + 1)
        line = []
        for gx in range(cols):
            x0 = int(gx * w / cols)
            x1 = max(int((gx + 1) * w / cols), x0 + 1)
            total = 0
            count = 0
            for y in range(y0, y1):
                for x in range(x0, x1):
                    total += lum[y][x]
                    count += 1
            value = total / max(count, 1)
            t = (value - lo) / max(hi - lo, 1e-6)
            t = min(max(t, 0.0), 1.0)
            line.append(glyphs[int(round(t * (len(glyphs) - 1)))])
        print(f'{gy:2d} ' + ''.join(line))

    # 每行横向亮度跳变位置 -> 竖直接缝 / 面板边界
    print('\nhorizontal luminance jumps per row (|dL/dx| >= threshold):')
    threshold = max((hi - lo) * 0.16, 6)
    for gy in range(1, rows):
        y = int(gy * h / rows)
        edges = []
        previous = lum[y][0]
        for x in range(1, w):
            value = lum[y][x]
            if abs(value - previous) >= threshold:
                edges.append(x)
            previous = value
        groups = []
        for x in edges:
            if groups and x - groups[-1][-1] <= 2:
                groups[-1].append(x)
            else:
                groups.append([x])
        spans = ','.join(f'{g[0]}' if len(g) == 1 else f'{g[0]}-{g[-1]}' for g in groups)
        print(f'  y={y:3d}: {spans if spans else "-"}')

    # 竖直亮度跳变位置 -> 水平边界（面板上下沿）
    print('\nvertical luminance jumps per column:')
    for gx in range(1, cols):
        x = int(gx * w / cols)
        edges = []
        previous = lum[0][x]
        for y in range(1, h):
            value = lum[y][x]
            if abs(value - previous) >= threshold:
                edges.append(y)
            previous = value
        groups = []
        for y in edges:
            if groups and y - groups[-1][-1] <= 2:
                groups[-1].append(y)
            else:
                groups.append([y])
        spans = ','.join(f'{g[0]}' if len(g) == 1 else f'{g[0]}-{g[-1]}' for g in groups)
        print(f'  x={x:3d}: {spans if spans else "-"}')


if __name__ == '__main__':
    main()
