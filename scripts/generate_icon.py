#!/usr/bin/env python3
"""
生成应用图标：知识库/知识图谱主题，蓝色系，扁平化现代风格
"""
import math
import os
from PIL import Image, ImageDraw

SIZE = 1024
ICON_DIR = os.path.join(os.path.dirname(__file__), "..", "src-tauri", "icons")


def draw_rounded_rect(draw, xy, radius, fill):
    """绘制圆角矩形"""
    x0, y0, x1, y1 = xy
    r = radius
    # 四个角
    draw.ellipse([x0, y0, x0 + 2 * r, y0 + 2 * r], fill=fill)
    draw.ellipse([x1 - 2 * r, y0, x1, y0 + 2 * r], fill=fill)
    draw.ellipse([x0, y1 - 2 * r, x0 + 2 * r, y1], fill=fill)
    draw.ellipse([x1 - 2 * r, y1 - 2 * r, x1, y1], fill=fill)
    # 填充中间
    draw.rectangle([x0 + r, y0, x1 - r, y1], fill=fill)
    draw.rectangle([x0, y0 + r, x1, y1 - r], fill=fill)


def create_gradient(size, color_top, color_bottom):
    """创建线性渐变背景"""
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        ratio = y / size
        r = int(color_top[0] * (1 - ratio) + color_bottom[0] * ratio)
        g = int(color_top[1] * (1 - ratio) + color_bottom[1] * ratio)
        b = int(color_top[2] * (1 - ratio) + color_bottom[2] * ratio)
        for x in range(size):
            img.putpixel((x, y), (r, g, b, 255))
    return img


def create_gradient_fast(size, color_top, color_bottom):
    """快速创建线性渐变背景"""
    import numpy as np
    arr = np.zeros((size, size, 4), dtype=np.uint8)
    for y in range(size):
        ratio = y / size
        r = int(color_top[0] * (1 - ratio) + color_bottom[0] * ratio)
        g = int(color_top[1] * (1 - ratio) + color_bottom[1] * ratio)
        b = int(color_top[2] * (1 - ratio) + color_bottom[2] * ratio)
        arr[y, :, 0] = r
        arr[y, :, 1] = g
        arr[y, :, 2] = b
        arr[y, :, 3] = 255
    return Image.fromarray(arr, "RGBA")


def create_gradient_pure(size, color_top, color_bottom):
    """纯 Pillow 创建渐变（不依赖 numpy）"""
    img = Image.new("RGBA", (size, size))
    draw = ImageDraw.Draw(img)
    for y in range(size):
        ratio = y / size
        r = int(color_top[0] * (1 - ratio) + color_bottom[0] * ratio)
        g = int(color_top[1] * (1 - ratio) + color_bottom[1] * ratio)
        b = int(color_top[2] * (1 - ratio) + color_bottom[2] * ratio)
        draw.line([(0, y), (size - 1, y)], fill=(r, g, b, 255))
    return img


def generate_icon():
    """生成知识图谱主题图标"""
    S = SIZE
    center = S // 2

    # -- 1. 渐变背景 --
    # 深蓝到更深的蓝色渐变
    top_color = (30, 100, 210)       # 亮蓝
    bottom_color = (15, 50, 130)     # 深蓝
    try:
        img = create_gradient_fast(S, top_color, bottom_color)
    except ImportError:
        img = create_gradient_pure(S, top_color, bottom_color)

    draw = ImageDraw.Draw(img)

    # -- 2. 绘制圆角矩形底板（带轻微透明度效果的内部区域）--
    margin = int(S * 0.06)
    corner_r = int(S * 0.18)
    # 内部稍亮区域
    inner_color = (35, 110, 220, 255)
    draw_rounded_rect(draw, (margin, margin, S - margin, S - margin), corner_r, inner_color)

    # 重新应用渐变到圆角矩形内
    # 为了保持圆角，我们创建一个 mask
    mask = Image.new("L", (S, S), 0)
    mask_draw = ImageDraw.Draw(mask)
    draw_rounded_rect(mask_draw, (margin, margin, S - margin, S - margin), corner_r, 255)

    # 创建渐变层
    gradient = create_gradient_pure(S, (40, 120, 230), (18, 55, 145))
    # 应用 mask
    img = Image.composite(gradient, Image.new("RGBA", (S, S), (0, 0, 0, 0)), mask)
    draw = ImageDraw.Draw(img)

    # -- 3. 知识图谱网络 --
    # 节点位置（相对于中心，然后偏移）
    # 设计一个中心大节点 + 周围多个小节点的图谱

    # 定义节点：(cx, cy, radius, color)
    nodes = [
        # 中心大节点
        (center, center, int(S * 0.085), (255, 255, 255)),
        # 第一圈 - 6 个节点
        (center + int(S * 0.22), center - int(S * 0.08), int(S * 0.05), (180, 220, 255)),
        (center + int(S * 0.12), center + int(S * 0.20), int(S * 0.045), (150, 200, 255)),
        (center - int(S * 0.15), center + int(S * 0.18), int(S * 0.048), (180, 220, 255)),
        (center - int(S * 0.24), center + int(S * 0.02), int(S * 0.042), (140, 195, 255)),
        (center - int(S * 0.14), center - int(S * 0.20), int(S * 0.045), (160, 210, 255)),
        (center + int(S * 0.10), center - int(S * 0.22), int(S * 0.04), (150, 200, 255)),
        # 第二圈 - 外围小节点
        (center + int(S * 0.35), center + int(S * 0.05), int(S * 0.03), (120, 180, 255)),
        (center + int(S * 0.28), center - int(S * 0.25), int(S * 0.028), (130, 185, 255)),
        (center - int(S * 0.32), center - int(S * 0.18), int(S * 0.032), (120, 175, 255)),
        (center - int(S * 0.30), center + int(S * 0.22), int(S * 0.025), (110, 170, 255)),
        (center + int(S * 0.05), center + int(S * 0.35), int(S * 0.028), (130, 185, 255)),
        (center - int(S * 0.08), center - int(S * 0.35), int(S * 0.025), (110, 170, 255)),
    ]

    # 定义连线（节点索引对）
    edges = [
        # 中心到第一圈
        (0, 1), (0, 2), (0, 3), (0, 4), (0, 5), (0, 6),
        # 第一圈内部连接
        (1, 2), (2, 3), (3, 4), (4, 5), (5, 6), (6, 1),
        # 第一圈到第二圈
        (1, 7), (1, 8), (5, 8), (6, 8),
        (4, 9), (5, 9),
        (3, 10), (4, 10),
        (2, 11), (3, 11),
        (5, 12), (6, 12),
    ]

    # 绘制连线
    for i, j in edges:
        x0, y0, r0, _ = nodes[i]
        x1, y1, r1, _ = nodes[j]
        # 线条颜色 - 半透明白色
        line_color = (200, 225, 255, 140)
        line_width = max(2, int(S * 0.003))
        draw.line([(x0, y0), (x1, y1)], fill=line_color, width=line_width)

    # 绘制节点（带发光效果）
    for idx, (cx, cy, r, color) in enumerate(nodes):
        # 外发光
        glow_r = int(r * 1.6)
        glow_color = (color[0], color[1], color[2], 40)
        # 用半透明叠加模拟发光
        overlay = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        overlay_draw = ImageDraw.Draw(overlay)
        overlay_draw.ellipse(
            [cx - glow_r, cy - glow_r, cx + glow_r, cy + glow_r],
            fill=(color[0], color[1], color[2], 30)
        )
        img = Image.alpha_composite(img, overlay)
        draw = ImageDraw.Draw(img)

        # 主节点圆形
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)

        # 中心节点特殊处理：绘制内部图标（简化的灯泡/大脑符号）
        if idx == 0:
            # 在中心节点内画一个简单的 "K" (Knowledge) 符号
            inner_r = int(r * 0.55)
            inner_color = (35, 110, 220)  # 与背景相近的蓝色
            # 画一个小圆点
            dot_r = int(r * 0.18)
            draw.ellipse([cx - dot_r, cy - dot_r, cx + dot_r, cy + dot_r], fill=inner_color)
            # 画放射线
            for angle_deg in range(0, 360, 45):
                angle = math.radians(angle_deg)
                x_start = cx + int(dot_r * 1.8 * math.cos(angle))
                y_start = cy + int(dot_r * 1.8 * math.sin(angle))
                x_end = cx + int(inner_r * 0.9 * math.cos(angle))
                y_end = cy + int(inner_r * 0.9 * math.sin(angle))
                draw.line(
                    [(x_start, y_start), (x_end, y_end)],
                    fill=inner_color,
                    width=max(2, int(S * 0.004))
                )

    # -- 4. 最终输出 --
    os.makedirs(ICON_DIR, exist_ok=True)

    # 保存 1024x1024 原图
    master_path = os.path.join(ICON_DIR, "icon_master.png")
    img.save(master_path, "PNG")
    print(f"✓ 已生成主图标: {master_path}")

    # 生成各尺寸
    sizes = {
        "32x32.png": 32,
        "128x128.png": 128,
        "128x128@2x.png": 256,
        "icon.png": 512,
    }
    for filename, size in sizes.items():
        resized = img.resize((size, size), Image.LANCZOS)
        path = os.path.join(ICON_DIR, filename)
        resized.save(path, "PNG")
        print(f"✓ 已生成 {filename} ({size}x{size})")

    # 生成 .ico (Windows)
    ico_path = os.path.join(ICON_DIR, "icon.ico")
    # ICO 支持多种尺寸嵌入
    ico_sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    ico_images = []
    for s in ico_sizes:
        ico_images.append(img.resize(s, Image.LANCZOS))
    img_for_ico = img.resize((256, 256), Image.LANCZOS)
    img_for_ico.save(ico_path, format="ICO", sizes=[(s[0], s[1]) for s in ico_sizes])
    print(f"✓ 已生成 icon.ico")

    # 生成 .icns (macOS) - 使用 iconutil
    iconset_dir = os.path.join(ICON_DIR, "icon.iconset")
    os.makedirs(iconset_dir, exist_ok=True)

    icns_specs = [
        ("icon_16x16.png", 16),
        ("icon_16x16@2x.png", 32),
        ("icon_32x32.png", 32),
        ("icon_32x32@2x.png", 64),
        ("icon_128x128.png", 128),
        ("icon_128x128@2x.png", 256),
        ("icon_256x256.png", 256),
        ("icon_256x256@2x.png", 512),
        ("icon_512x512.png", 512),
        ("icon_512x512@2x.png", 1024),
    ]
    for filename, size in icns_specs:
        resized = img.resize((size, size), Image.LANCZOS)
        resized.save(os.path.join(iconset_dir, filename), "PNG")

    icns_path = os.path.join(ICON_DIR, "icon.icns")
    ret = os.system(f'iconutil -c icns "{iconset_dir}" -o "{icns_path}"')
    if ret == 0:
        print(f"✓ 已生成 icon.icns")
    else:
        print(f"✗ icon.icns 生成失败 (exit code: {ret})")

    # 清理 iconset 目录
    import shutil
    shutil.rmtree(iconset_dir, ignore_errors=True)

    print(f"\n所有图标已生成到: {os.path.abspath(ICON_DIR)}")


if __name__ == "__main__":
    generate_icon()
