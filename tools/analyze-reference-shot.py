"""把一张参考截图转成可读的结构：色板、字符地图、面板轮廓与角度。
用法: python tools/analyze-reference-shot.py "E:\\path\\shot.png" [cols] [rows]
"""
import sys
from collections import Counter
from PIL import Image


def luminance(px):
    r, g, b = px[:3]
    return 0.299 * r + 0.587 * g + 0.114 * b


def classify(px):
    r, g, b = px[:3]
    lum = luminance(px)
    if lum < 26:
        return ' '                      # 纯黑背景
    if g > 110 and b > 120 and (g - r) > 25 and (b - r) > 25:
        return '#'                      # 青色描边 / 高光
    if lum > 195:
        return '*'                      # 亮白文字
    if lum > 120:
        return '+'                      # 中亮
    if b - r > 18 and b - g > 8:
        return 'b'                      # 蓝底面板
    if r - b > 18:
        return 'w'                      # 暖色（金/粉）
    if (max(r, g, b) - min(r, g, b)) < 14:
        return '.'                      # 灰
    return ':'


def main():
    path = sys.argv[1]
    cols = int(sys.argv[2]) if len(sys.argv) > 2 else 92
    rows = int(sys.argv[3]) if len(sys.argv) > 3 else 31
    img = Image.open(path).convert('RGB')
    w, h = img.size
    print(f'file: {path}')
    print(f'size: {w}x{h}  aspect={w / h:.3f}')

    counts = Counter(img.getdata())
    print('top colors:')
    for color, count in counts.most_common(14):
        print(f'  #{color[0]:02x}{color[1]:02x}{color[2]:02x}  {count / (w * h) * 100:5.2f}%')

    print(f'\nclassification map ({cols}x{rows}):')
    print('  ' + ''.join(str((i // 10) % 10) if i % 10 == 0 else ' ' for i in range(cols)))
    for gy in range(rows):
        y0, y1 = int(gy * h / rows), max(int((gy + 1) * h / rows), int(gy * h / rows) + 1)
        line = []
        for gx in range(cols):
            x0, x1 = int(gx * w / cols), max(int((gx + 1) * w / cols), int(gx * w / cols) + 1)
            samples = Counter()
            for y in range(y0, y1):
                for x in range(x0, x1):
                    samples[classify(img.getpixel((x, y)))] += 1
            line.append(samples.most_common(1)[0][0])
        print(f'{gy:2d}' + ''.join(line))

    # 面板轮廓：青色描边像素在每一列的最高/最低位置
    mask = []
    for x in range(w):
        ys = [y for y in range(h) if classify(img.getpixel((x, y))) == '#']
        mask.append(ys)
    columns = [x for x in range(w) if mask[x]]
    if columns:
        print('\ncyan outline columns:')
        print(f'  x range: {columns[0]}..{columns[-1]}  ({len(columns)} columns with cyan)')
        for label, pick in (('top', min), ('bottom', max)):
            pts = [(x, pick(mask[x])) for x in columns]
            left = [p for p in pts if p[0] < w * 0.5]
            right = [p for p in pts if p[0] >= w * 0.5]
            for name, group in (('left half', left), ('right half', right)):
                if len(group) < 8:
                    continue
                n = len(group)
                mx = sum(p[0] for p in group) / n
                my = sum(p[1] for p in group) / n
                sxx = sum((p[0] - mx) ** 2 for p in group)
                sxy = sum((p[0] - mx) * (p[1] - my) for p in group)
                slope = sxy / sxx if sxx else 0
                intercept = my - slope * mx
                resid = max(abs(p[1] - (slope * p[0] + intercept)) for p in group)
                print(f'  {label} edge {name}: slope={slope:+.3f} ({slope * 180 / 3.14159:+.1f} deg), '
                      f'y({group[0][0]})={slope * group[0][0] + intercept:.0f}, '
                      f'y({group[-1][0]})={slope * group[-1][0] + intercept:.0f}, max-resid={resid:.1f}')

    # 每一行里青色描边的横向位置（找接缝/边界）
    print('\ncyan x-positions on sampled rows:')
    for yy in range(int(h * 0.1), int(h * 0.95), max(1, h // 14)):
        xs = [x for x in range(w) if classify(img.getpixel((x, yy))) == '#']
        groups = []
        for x in xs:
            if groups and x - groups[-1][-1] <= 3:
                groups[-1].append(x)
            else:
                groups.append([x])
        spans = [f'{g[0]}-{g[-1]}' for g in groups]
        print(f'  y={yy:3d}: ' + (', '.join(spans) if spans else '-'))


if __name__ == '__main__':
    main()
