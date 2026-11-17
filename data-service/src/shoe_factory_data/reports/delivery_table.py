import os
from dataclasses import dataclass
from datetime import date
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from .schemas import DeliveryOrderGroup, DeliveryRenderRequest

WIDTH = 820
MAX_HEIGHT = 8192
TABLE_TOP = 83
HEADER_HEIGHT = 53
ROW_HEIGHT = 53
LINE_HEIGHT = 29
CELL_PADDING = 14
DELIVERY_PADDING = 6
COLUMNS = (26, 254, 504, 665, 794)
INK = "#193a31"
MUTED = "#556960"
GREEN = "#1d463b"
RED = "#a5392f"
GRID = "#d5e1da"
FILL_OVERDUE = "#fff1ed"
FILL_THREE_DAYS = "#fce9d6"
FILL_FIVE_DAYS = "#fbf6e2"
TEXT_THREE_DAYS = "#a4521c"
TEXT_FIVE_DAYS = "#7c5f12"
MIN_GROUP_HEIGHT = 70
TOP_PADDING = 20


class DeliveryTableTooLarge(ValueError):
    pass


@dataclass(frozen=True)
class DeliveryFonts:
    title: ImageFont.FreeTypeFont
    body: ImageFont.FreeTypeFont
    status: ImageFont.FreeTypeFont


def load_delivery_fonts() -> DeliveryFonts:
    noto_path = Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc")
    if font_path := os.getenv("DATA_SERVICE_FONT_PATH"):
        path, index = Path(font_path), 0
    elif noto_path.is_file():
        path, index = noto_path, 2  # Simplified Chinese face in the Debian Noto collection.
    else:
        path, index = Path("/System/Library/Fonts/STHeiti Medium.ttc"), 0

    try:
        index = int(os.getenv("DATA_SERVICE_FONT_INDEX", str(index)))
        return DeliveryFonts(*(ImageFont.truetype(path, size, index=index) for size in (34, 23, 19)))
    except (OSError, ValueError) as exc:
        raise RuntimeError(f"无法加载中文字体 {path}。请检查 DATA_SERVICE_FONT_PATH 和 DATA_SERVICE_FONT_INDEX。") from exc


def wrap_cell(value: str, font: ImageFont.FreeTypeFont, width: int) -> list[str]:
    lines = [""]
    for char in " ".join(value.split()):
        if lines[-1] and font.getlength(lines[-1] + char) > width:
            lines.append(char)
        else:
            lines[-1] += char
    return lines


def cell_text(
    draw: ImageDraw.ImageDraw,
    lines: list[str],
    column: int,
    middle: int,
    font: ImageFont.FreeTypeFont,
    color: str = INK,
    align: str = "l",
) -> None:
    left, right = COLUMNS[column : column + 2]
    x = {"l": left + CELL_PADDING, "m": (left + right) // 2, "r": right - CELL_PADDING}[align]
    top = middle - (len(lines) - 1) * LINE_HEIGHT // 2
    for i, line in enumerate(lines):
        draw.text((x, top + i * LINE_HEIGHT), line, font=font, fill=color, anchor=f"{align}m")


def delivery_date_text(group: DeliveryOrderGroup, report_date: date) -> str:
    if group.delivery_date is None:
        return "交期待确认"
    due = group.delivery_date
    return f"{due.month}/{due.day}" if due.year == report_date.year else due.isoformat()


def delivery_status_text(group: DeliveryOrderGroup, report_date: date) -> str | None:
    if group.delivery_date is None:
        return None
    if group.overdue_days:
        return f"逾期 {group.overdue_days} 天"
    remaining = (group.delivery_date - report_date).days
    if remaining <= 0:
        return "今日到期"
    if remaining <= 7:
        return f"剩余 {remaining} 天"
    return None


@dataclass(frozen=True)
class TableRow:
    style: list[str]
    pairs: list[str]
    height: int


@dataclass(frozen=True)
class TableGroup:
    order: list[str]
    delivery_date: list[str]
    delivery_status: list[str] | None
    fill: str | None
    text_color: str
    rows: list[TableRow]
    height: int


def severity_style(remaining_days: int | None, overdue: bool) -> tuple[str | None, str]:
    if overdue:
        return FILL_OVERDUE, RED
    if remaining_days is None or remaining_days > 5:
        return None, INK
    if remaining_days > 3:
        return FILL_FIVE_DAYS, TEXT_FIVE_DAYS
    return FILL_THREE_DAYS, TEXT_THREE_DAYS


def layout_groups(report: DeliveryRenderRequest, fonts: DeliveryFonts) -> list[TableGroup]:
    def wrap(value: str, column: int, font: ImageFont.FreeTypeFont) -> list[str]:
        padding = DELIVERY_PADDING if column == 2 else CELL_PADDING
        return wrap_cell(value, font, COLUMNS[column + 1] - COLUMNS[column] - 2 * padding)

    groups: list[TableGroup] = []
    total_height = TABLE_TOP + HEADER_HEIGHT + 26
    for group in report.order_groups:
        if total_height + len(group.styles) * ROW_HEIGHT > MAX_HEIGHT:
            raise DeliveryTableTooLarge("日报超出单张图片高度，请分批提交 orderGroups")
        overdue = group.overdue_days > 0
        remaining = None if group.delivery_date is None else (group.delivery_date - report.report_date).days
        fill, text_color = severity_style(remaining, overdue)
        order = wrap(group.order_no, 0, fonts.body)
        delivery_date = wrap(delivery_date_text(group, report.report_date), 2, fonts.body)
        status_text = delivery_status_text(group, report.report_date)
        delivery_status = wrap(status_text, 2, fonts.status) if status_text else None
        rows = []
        for item in group.styles:
            style = wrap(item.style_no, 1, fonts.body)
            pairs = wrap(f"{item.pending_pairs:,} 双", 3, fonts.body)
            rows.append(TableRow(style, pairs, max(ROW_HEIGHT, LINE_HEIGHT * max(len(style), len(pairs)) + 18)))
        text_lines = len(delivery_date) + (len(delivery_status) if delivery_status else 0)
        height = max(sum(row.height for row in rows),
                     LINE_HEIGHT * max(len(order), text_lines) + 12,
                     MIN_GROUP_HEIGHT)
        total_height += height
        if total_height > MAX_HEIGHT:
            raise DeliveryTableTooLarge("日报超出单张图片高度，请分批提交 orderGroups")
        groups.append(TableGroup(order, delivery_date, delivery_status, fill, text_color, rows, height))
    return groups


def render_delivery_table(report: DeliveryRenderRequest, fonts: DeliveryFonts) -> bytes:
    groups = layout_groups(report, fonts)
    table_height = sum(group.height for group in groups) if groups else ROW_HEIGHT
    header_bottom = TABLE_TOP + HEADER_HEIGHT
    table_bottom = header_bottom + table_height

    with Image.new("RGB", (WIDTH, table_bottom + 26), "white") as image:
        draw = ImageDraw.Draw(image)
        draw.text((26, 44), "清化鞋业 · 货期预警", font=fonts.title, fill=INK, anchor="lm")
        draw.text((794, 46), f"{report.report_date.month} 月 {report.report_date.day} 日",
                  font=fonts.body, fill=MUTED, anchor="rm")
        draw.rectangle((COLUMNS[0], TABLE_TOP, COLUMNS[-1], header_bottom), fill=GREEN)
        for column, name in enumerate(("订单号", "款号", "交期", "待出货")):
            cell_text(draw, [name], column, TABLE_TOP + HEADER_HEIGHT // 2, fonts.body,
                      "white", align="l" if column < 2 else "m")

        if not groups:
            draw.rectangle((COLUMNS[0], header_bottom, COLUMNS[-1], table_bottom), outline=GRID)
            draw.text((WIDTH // 2, header_bottom + ROW_HEIGHT // 2), "暂无预警订单",
                      font=fonts.body, fill=MUTED, anchor="mm")
        else:
            y = header_bottom
            separators: list[int] = []
            bounds: list[int] = []
            for group in groups:
                bottom = y + group.height
                if group.fill:
                    draw.rectangle((COLUMNS[0], y, COLUMNS[-1], bottom), fill=group.fill)
                for i, line in enumerate(group.order):
                    draw.text((COLUMNS[0] + CELL_PADDING, y + TOP_PADDING + i * LINE_HEIGHT),
                              line, font=fonts.body, fill=INK, anchor="lm")
                center_x = (COLUMNS[2] + COLUMNS[3]) // 2
                for i, line in enumerate(group.delivery_date):
                    draw.text((center_x, y + TOP_PADDING + i * LINE_HEIGHT),
                              line, font=fonts.body, fill=group.text_color, anchor="mm")
                if group.delivery_status:
                    status_top = y + TOP_PADDING + len(group.delivery_date) * LINE_HEIGHT
                    for i, line in enumerate(group.delivery_status):
                        draw.text((center_x, status_top + i * LINE_HEIGHT),
                                  line, font=fonts.status, fill=group.text_color, anchor="mm")

                row_top = y
                extra_height = group.height - sum(row.height for row in group.rows)
                for i, row in enumerate(group.rows):
                    if i:
                        separators.append(row_top)
                    row_height = row.height + (extra_height if i == len(group.rows) - 1 else 0)
                    middle = row_top + row_height // 2
                    cell_text(draw, row.style, 1, middle, fonts.body)
                    cell_text(draw, row.pairs, 3, middle, fonts.body, group.text_color, align="r")
                    row_top += row_height
                bounds.append(bottom)
                y = bottom

            for x in COLUMNS:
                draw.line((x, header_bottom, x, table_bottom), fill=GRID)
            for row_top in separators:
                draw.line((COLUMNS[1], row_top, COLUMNS[2], row_top), fill=GRID)
                draw.line((COLUMNS[3], row_top, COLUMNS[4], row_top), fill=GRID)
            for bottom in bounds:
                draw.line((COLUMNS[0], bottom, COLUMNS[-1], bottom), fill=GREEN, width=2)
            draw.rectangle((COLUMNS[0], header_bottom, COLUMNS[-1], table_bottom), outline=GREEN)

        with BytesIO() as output:
            image.save(output, format="PNG")
            return output.getvalue()
