"""把目标截图与一批候选截图做尺度/位置搜索比对，判断它到底是什么画面。
用法: python tools/compare-shots.py target.png candidate1.png candidate2.png ...
"""
import sys
import os
from PIL import Image, ImageChops, ImageStat


def normalized(image, size):
    return image.convert('RGB').resize(size, Image.BILINEAR)


def diff_score(a, b):
    diff = ImageChops.difference(a, b)
    stat = ImageStat.Stat(diff)
    return sum(stat.mean) / 3


def main():
    target_path = sys.argv[1]
    candidates = sys.argv[2:]
    target = Image.open(target_path).convert('RGB')
    tw, th = target.size
    aspect = tw / th
    print(f'target: {os.path.basename(target_path)} {tw}x{th} aspect={aspect:.3f}')
    stat = ImageStat.Stat(target)
    print(f'target mean RGB: {[round(v) for v in stat.mean]}')

    results = []
    for path in candidates:
        try:
            image = Image.open(path).convert('RGB')
        except Exception as error:  # noqa: BLE001
            print(f'skip {path}: {error}')
            continue
        iw, ih = image.size
        # 整图缩放比对
        results.append((diff_score(normalized(image, (tw, th)), target), os.path.basename(path), 'full', (0, 0, iw, ih)))
        # 滑窗裁剪比对（按目标宽高比，多种尺度）
        for width in (int(iw * 0.62), int(iw * 0.5), int(iw * 0.42), int(iw * 0.32), int(iw * 0.24)):
            height = int(width / aspect)
            if height > ih or width > iw:
                continue
            for x in range(0, iw - width + 1, max(12, width // 8)):
                for y in range(0, ih - height + 1, max(12, height // 6)):
                    crop = image.crop((x, y, x + width, y + height))
                    results.append((diff_score(normalized(crop, (tw, th)), target),
                                    os.path.basename(path), f'{width}x{height}', (x, y, x + width, y + height)))

    results.sort(key=lambda item: item[0])
    print('\nbest matches (lower = closer):')
    for score, name, size, box in results[:12]:
        print(f'  {score:6.2f}  {name}  {size}  box={box}')


if __name__ == '__main__':
    main()
