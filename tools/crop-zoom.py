"""裁剪并放大截图区域，便于看清细节。
用法: python tools/crop-zoom.py <image> <out.png> <left> <top> <right> <bottom> [scale]
"""
import sys

from PIL import Image


def main():
    src, dst = sys.argv[1], sys.argv[2]
    left, top, right, bottom = (int(v) for v in sys.argv[3:7])
    scale = float(sys.argv[7]) if len(sys.argv) > 7 else 3.0
    img = Image.open(src).convert('RGB')
    crop = img.crop((left, top, right, bottom))
    zoomed = crop.resize((max(1, int(crop.width * scale)), max(1, int(crop.height * scale))), Image.LANCZOS)
    zoomed.save(dst)
    print(f'{src} -> {dst}  crop={crop.size} zoom={zoomed.size}')


if __name__ == '__main__':
    main()
