import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  EyeOutlined,
  PrinterOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import { App, Button, Cascader, Modal, Pagination, Radio, Select, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { Key, MouseEvent } from "react";
import { fetchOrderDetails, fetchOrderPackingDetails, fetchOrders } from "../api/orderApi";
import { fetchStyleConfigs } from "../api/styleConfigApi";
import type { DevelopmentNoOption, OrderPackingDetail, OrderRecord, OrderRecordDetail } from "../types/order";
import type { StyleConfig } from "../types/styleConfig";
import { formatEmpty } from "../utils/format";
import { getMatchingPackingDetails, hasMatchingPackingDetails } from "../utils/orderMatching";

interface PrintSelectionPageProps {
  title: string;
}

type PrintSelectionItem = OrderRecordDetail & {
  printOrderNo?: string;
  packingDetails?: OrderPackingDetail[];
  styleConfig?: StyleConfig;
};

const templatePrintItems: PrintSelectionItem[] = [];

type PrintFormatTarget = "outer-carton" | "inner-box";
type CartonPrintFormatKey = "outer-carton-a4" | "outer-carton-dual-size";
type InnerBoxPrintFormatKey = "inner-box-a4";
type PrintFormatKey = CartonPrintFormatKey | InnerBoxPrintFormatKey;
type PreviewMode = "print" | "template";

interface CartonLabelTemplateData {
  customerName: string;
  storeLine: string;
  cartonNumber: string;
  factoryOrderNo: string;
  style: string;
  material: string;
  color: string;
  number: string;
  po: string;
  orderNumber: string;
  sizeColumns: string[];
  sizeValues: string[];
  grossWeight: string;
  netWeight: string;
}

interface InnerBoxLabelData {
  styleName: string;
  material: string;
  colour: string;
  size: string;
}

const defaultCartonLabelData: CartonLabelTemplateData = {
  customerName: "客人",
  storeLine: "仓库号/店铺号",
  cartonNumber: "开始箱号",
  factoryOrderNo: "订单流水号",
  style: "STYLE/客人款号",
  material: "MATERIAL/面料材质",
  color: "COLOR/客人颜色",
  number: "总对数",
  po: "PO号",
  orderNumber: "客人订单号",
  sizeColumns: ["WIDTH", "5", "5.5", "6", "6.5", "7", "7.5", "8", "8.5", "9", "9.5", "10", "10.5", "11", "12", "TOTAL"],
  sizeValues: ["M", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""],
  grossWeight: "毛重",
  netWeight: "净重",
};

const printFormatTemplates: Record<
  PrintFormatKey,
  { label: string; target: PrintFormatTarget; data?: CartonLabelTemplateData }
> = {
  "outer-carton-a4": {
    label: "外箱贴标-单码-通用版",
    target: "outer-carton",
    data: defaultCartonLabelData,
  },
  "outer-carton-dual-size": {
    label: "外箱贴标-双码-通用版",
    target: "outer-carton",
    data: {
      ...defaultCartonLabelData,
      sizeColumns: ["WIDTH", "", "", "", "35", "36", "37", "38", "39", "40", "41", "", "", "", "", "TOTAL"],
      sizeValues: ["M", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""],
    },
  },
  "inner-box-a4": {
    label: "内盒贴标-通用版",
    target: "inner-box",
  },
};

const printFormatOptions = Object.entries(printFormatTemplates).map(([value, item]) => ({
  label: item.label,
  value: value as PrintFormatKey,
  target: item.target,
}));

const defaultInnerBoxLabel: InnerBoxLabelData = {
  styleName: "SCHEDULE",
  material: "KID SKIN/COW LEATHER",
  colour: "WHITE BLACK PATENT",
  size: "6",
};

const defaultInnerBoxLabels: InnerBoxLabelData[] = [defaultInnerBoxLabel];

const CARTON_LABEL_COPIES_PER_CARTON = 2;
const INNER_BOX_LABELS_PER_PAGE = 18;
const CSS_PX_PER_MM = 96 / 25.4;
const CARTON_LABEL_VALUE_WIDTH_PX = (138.536 - (14.94 + 7.974 * 4)) * CSS_PX_PER_MM;
const CARTON_LABEL_INFO_ROW_HEIGHT_PX = 15.31 * CSS_PX_PER_MM;
const CARTON_LABEL_DEFAULT_VALUE_FONT_SIZE = 24;
const CARTON_LABEL_WRAPPED_VALUE_MAX_FONT_SIZE = 20;
const CARTON_LABEL_WRAPPED_VALUE_MIN_FONT_SIZE = 12;
const CARTON_LABEL_WRAPPED_VALUE_LINE_HEIGHT = 1.05;

function renderSizeQuantities(value?: Record<string, number>) {
  const entries = Object.entries(value || {})
    .filter(([, count]) => Number(count) > 0)
    .sort(([left], [right]) => left.localeCompare(right, "zh-CN", { numeric: true }));
  if (entries.length === 0) {
    return "-";
  }
  return (
    <div className="size-grid">
      {entries.map(([size, count]) => (
        <span key={size}>
          {size}: {count}
        </span>
      ))}
    </div>
  );
}

function getCartonLabelAdaptiveFontSize(element: HTMLElement, value: string) {
  const text = value.trim();
  if (!text) {
    return undefined;
  }

  const computedStyle = window.getComputedStyle(element);
  const width = element.clientWidth || CARTON_LABEL_VALUE_WIDTH_PX;
  const height = element.clientHeight || CARTON_LABEL_INFO_ROW_HEIGHT_PX;
  if (width <= 0 || height <= 0) {
    return undefined;
  }

  const measuringElement = document.createElement("div");
  measuringElement.textContent = text;
  measuringElement.style.position = "fixed";
  measuringElement.style.left = "-9999px";
  measuringElement.style.top = "0";
  measuringElement.style.visibility = "hidden";
  measuringElement.style.pointerEvents = "none";
  measuringElement.style.boxSizing = "border-box";
  measuringElement.style.width = `${width}px`;
  measuringElement.style.fontFamily = computedStyle.fontFamily || '"宋体", SimSun, Arial, sans-serif';
  measuringElement.style.fontWeight = computedStyle.fontWeight || "900";
  measuringElement.style.fontStyle = computedStyle.fontStyle;
  measuringElement.style.letterSpacing = computedStyle.letterSpacing;
  measuringElement.style.textAlign = "center";
  measuringElement.style.whiteSpace = "nowrap";
  measuringElement.style.fontSize = `${CARTON_LABEL_DEFAULT_VALUE_FONT_SIZE}px`;
  measuringElement.style.lineHeight = String(CARTON_LABEL_WRAPPED_VALUE_LINE_HEIGHT);
  document.body.appendChild(measuringElement);

  const oneLineFits = measuringElement.scrollWidth <= width + 0.5;
  if (oneLineFits) {
    measuringElement.remove();
    return undefined;
  }

  measuringElement.style.whiteSpace = "normal";
  measuringElement.style.wordBreak = "normal";
  measuringElement.style.overflowWrap = "anywhere";

  for (
    let fontSize = CARTON_LABEL_WRAPPED_VALUE_MAX_FONT_SIZE;
    fontSize >= CARTON_LABEL_WRAPPED_VALUE_MIN_FONT_SIZE;
    fontSize -= 0.5
  ) {
    measuringElement.style.fontSize = `${fontSize}px`;
    if (measuringElement.scrollHeight <= height + 0.5 && measuringElement.scrollWidth <= width + 0.5) {
      measuringElement.remove();
      return Number(fontSize.toFixed(1));
    }
  }

  measuringElement.remove();
  return CARTON_LABEL_WRAPPED_VALUE_MIN_FONT_SIZE;
}

function CartonAdaptiveInfoValue({ value }: { value: string }) {
  const valueRef = useRef<HTMLDivElement>(null);

  const updateFontSize = useCallback(() => {
    const element = valueRef.current;
    if (!element) {
      return;
    }

    const fontSize = getCartonLabelAdaptiveFontSize(element, value);
    if (fontSize === undefined) {
      element.style.removeProperty("--carton-label-info-value-font-size");
    } else {
      element.style.setProperty("--carton-label-info-value-font-size", `${fontSize}px`);
    }
  }, [value]);

  useLayoutEffect(() => {
    updateFontSize();
    const animationFrame = window.requestAnimationFrame(updateFontSize);
    const element = valueRef.current;
    const resizeObserver = element && "ResizeObserver" in window ? new ResizeObserver(updateFontSize) : null;
    if (element && resizeObserver) {
      resizeObserver.observe(element);
    }
    window.addEventListener("resize", updateFontSize);
    window.addEventListener("beforeprint", updateFontSize);
    return () => {
      window.cancelAnimationFrame(animationFrame);
      resizeObserver?.disconnect();
      window.removeEventListener("resize", updateFontSize);
      window.removeEventListener("beforeprint", updateFontSize);
    };
  }, [updateFontSize]);

  return (
    <div ref={valueRef} className="carton-label-info-value carton-label-info-value-adaptive">
      {value}
    </div>
  );
}

function buildOrderLabel(order: OrderRecord) {
  return order.orderNo || `订单 ${order.id}`;
}

function includesKeyword(value: unknown, keyword: string) {
  return String(value || "")
    .toLocaleLowerCase()
    .includes(keyword.toLocaleLowerCase());
}

function renderCountText(filteredCount: number, totalCount: number, suffix: string, isFiltered: boolean) {
  if (!isFiltered) {
    return `${totalCount} 条${suffix}`;
  }
  return `${filteredCount} / ${totalCount} 条${suffix}`;
}

function parseDevelopmentNoParts(value: string) {
  const parts = value
    .trim()
    .split("-")
    .map((part) => part.trim())
    .filter(Boolean);
  if (parts.length >= 3) {
    return parts.slice(-3);
  }
  return parts;
}

function sortDevelopmentNoOptions(left: DevelopmentNoOption, right: DevelopmentNoOption) {
  return left.label.localeCompare(right.label, "zh-CN", { numeric: true });
}

function appendDevelopmentNoOption(nodes: DevelopmentNoOption[], parts: string[], path: string[] = []) {
  if (parts.length === 0) {
    return;
  }
  const [part, ...rest] = parts;
  const nextPath = [...path, part];
  let node = nodes.find((item) => item.label === part);
  if (!node) {
    node = {
      value: nextPath.join("-"),
      label: part,
      children: rest.length > 0 ? [] : undefined,
    };
    nodes.push(node);
    nodes.sort(sortDevelopmentNoOptions);
  }
  if (rest.length > 0) {
    if (!node.children) {
      node.children = [];
    }
    appendDevelopmentNoOption(node.children, rest, nextPath);
  }
}

function buildDevelopmentNoOptions(details: OrderRecordDetail[]) {
  const options: DevelopmentNoOption[] = [];
  const seen = new Set<string>();
  details.forEach((item) => {
    const developmentNo = item.developmentNo?.trim();
    if (!developmentNo || seen.has(developmentNo)) {
      return;
    }
    seen.add(developmentNo);
    appendDevelopmentNoOption(options, parseDevelopmentNoParts(developmentNo));
  });
  return options;
}

function getPrintFormatTarget(title: string): PrintFormatTarget {
  return title.includes("内盒") ? "inner-box" : "outer-carton";
}

function getDefaultPrintFormat(title: string): PrintFormatKey {
  return getPrintFormatTarget(title) === "inner-box" ? "inner-box-a4" : "outer-carton-a4";
}

function isCartonPrintFormat(format: PrintFormatKey): format is CartonPrintFormatKey {
  return printFormatTemplates[format].target === "outer-carton";
}

function pickFirstText(...values: unknown[]) {
  for (const value of values) {
    const text = String(value ?? "").trim();
    if (text) {
      return text;
    }
  }
  return "";
}

function isDateLikeText(value: string) {
  return /^\d{1,4}[-/]\d{1,2}[-/]\d{1,4}$/.test(value.trim());
}

function pickPoText(...values: unknown[]) {
  for (const value of values) {
    const text = String(value ?? "").trim();
    if (text && !isDateLikeText(text)) {
      return text;
    }
  }
  return "";
}

function getCartonLabelPo(item: PrintSelectionItem, packingDetail?: OrderPackingDetail) {
  if (packingDetail) {
    return pickPoText(packingDetail.poNo);
  }
  return pickPoText(item.poNo);
}

function normalizeKey(value?: string | number | null) {
  return String(value ?? "").trim().toUpperCase();
}

function getSortedSizeEntries(value?: Record<string, number>) {
  return Object.entries(value || {})
    .map(([size, count]) => [size.trim(), Number(count)] as const)
    .filter(([size, count]) => size && Number.isFinite(count) && count > 0)
    .sort(([left], [right]) => left.localeCompare(right, "zh-CN", { numeric: true }));
}

function buildStyleConfigMap(configs: StyleConfig[]) {
  return new Map(configs.map((config) => [normalizeKey(config.developmentNo), config]));
}

function findStyleConfig(
  configsByDevelopmentNo: Map<string, StyleConfig>,
  item: OrderRecordDetail,
  packingDetail?: OrderPackingDetail,
) {
  return (
    configsByDevelopmentNo.get(normalizeKey(packingDetail?.companyStyleNo)) ||
    configsByDevelopmentNo.get(normalizeKey(item.developmentNo))
  );
}

function hasWeightConfig(config?: StyleConfig) {
  return Number(config?.netWeightPerPair) > 0 && Number(config?.grossWeightPerPair) > 0;
}

function formatWeightKgs(value?: number) {
  if (!Number.isFinite(value) || Number(value) <= 0) {
    return "";
  }
  return `${Number(value).toFixed(3).replace(/\.?0+$/, "")}`;
}

function calculateLabelWeight(weightPerPair?: number, pairCount?: number) {
  const weight = Number(weightPerPair);
  const pairs = Number(pairCount);
  if (!Number.isFinite(weight) || weight <= 0 || !Number.isFinite(pairs) || pairs <= 0) {
    return "";
  }
  return formatWeightKgs(weight * pairs);
}

function hasSizeQuantities(value?: Record<string, number>) {
  return getSortedSizeEntries(value).length > 0;
}

function sumSizeQuantities(value?: Record<string, number>) {
  return getSortedSizeEntries(value).reduce((total, [, count]) => total + count, 0);
}

function getPrintItemPackingSources(item: PrintSelectionItem) {
  return item.packingDetails && item.packingDetails.length > 0 ? item.packingDetails : [undefined];
}

function getSourceSizeQuantities(item: PrintSelectionItem, packingDetail?: OrderPackingDetail) {
  return hasSizeQuantities(packingDetail?.sizeQuantities) ? packingDetail?.sizeQuantities : item.sizeQuantities;
}

function formatCartonNumber(start?: string, end?: string) {
  const startText = pickFirstText(start);
  const endText = pickFirstText(end);
  if (startText && endText && startText !== endText) {
    return `${startText}-${endText}`;
  }
  return startText || endText;
}

function parseCartonNumber(value?: string) {
  const text = pickFirstText(value);
  const match = text.match(/^(.*?)(\d+)(.*?)$/);
  if (!match) {
    return null;
  }
  return {
    prefix: match[1],
    number: Number(match[2]),
    suffix: match[3],
    width: match[2].length,
  };
}

function formatParsedCartonNumber(carton: NonNullable<ReturnType<typeof parseCartonNumber>>, offset = 0) {
  return `${carton.prefix}${String(carton.number + offset).padStart(carton.width, "0")}${carton.suffix}`;
}

function expandCartonNumbers(start?: string, end?: string, cartonCount?: number) {
  const startText = pickFirstText(start);
  const endText = pickFirstText(end);
  const count = Math.trunc(Number(cartonCount));
  const startCarton = parseCartonNumber(startText);
  const endCarton = parseCartonNumber(endText);

  if (
    startCarton &&
    endCarton &&
    startCarton.prefix === endCarton.prefix &&
    startCarton.suffix === endCarton.suffix &&
    endCarton.number >= startCarton.number
  ) {
    const rangeCount = endCarton.number - startCarton.number + 1;
    return Array.from({ length: rangeCount }, (_, index) => formatParsedCartonNumber(startCarton, index));
  }

  if (startCarton && Number.isFinite(count) && count > 1) {
    return Array.from({ length: count }, (_, index) => formatParsedCartonNumber(startCarton, index));
  }

  const fallback = formatCartonNumber(startText, endText);
  return fallback ? [fallback] : [""];
}

function sizeKeyParts(value: string) {
  return value
    .split("/")
    .map((part) => part.trim())
    .filter(Boolean);
}

function findSizeCount(sizeQuantities: Record<string, number> | undefined, column: string) {
  const normalizedColumn = column.trim();
  if (!normalizedColumn) {
    return "";
  }
  const entry = getSortedSizeEntries(sizeQuantities).find(([size]) => {
    const parts = sizeKeyParts(size);
    return size === normalizedColumn || parts.includes(normalizedColumn);
  });
  return entry ? String(entry[1]) : "";
}

function buildCartonSizeValues(
  sizeColumns: string[],
  sizeQuantities: Record<string, number> | undefined,
  pairCount?: number,
) {
  const sizeTotal = sumSizeQuantities(sizeQuantities);
  const total = Number(pairCount) > 0 ? Number(pairCount) : sizeTotal;
  return sizeColumns.map((column, index) => {
    if (index === 0) {
      return "M";
    }
    if (column === "TOTAL") {
      return total > 0 ? String(total) : "";
    }
    return findSizeCount(sizeQuantities, column);
  });
}

function getPerCartonPairCount(
  item: PrintSelectionItem,
  packingDetail: OrderPackingDetail | undefined,
  sizeQuantities: Record<string, number> | undefined,
) {
  const sizeTotal = sumSizeQuantities(sizeQuantities);
  if (sizeTotal > 0) {
    return sizeTotal;
  }

  const totalPairs = Number(packingDetail?.totalPairs ?? item.quantity);
  const cartonCount = Number(packingDetail?.cartonCount ?? item.cartonCount);
  if (!Number.isFinite(totalPairs) || totalPairs <= 0) {
    return undefined;
  }
  if (Number.isFinite(cartonCount) && cartonCount > 1) {
    return Math.round(totalPairs / cartonCount);
  }
  return totalPairs;
}

function buildCartonLabelData(format: CartonPrintFormatKey, items: PrintSelectionItem[]) {
  const templateData = printFormatTemplates[format].data || defaultCartonLabelData;
  if (items.length === 0) {
    return [templateData, templateData];
  }

  const labels = items.flatMap((item) =>
    getPrintItemPackingSources(item).flatMap((packingDetail) => {
      const sizeQuantities = getSourceSizeQuantities(item, packingDetail);
      const pairCount = getPerCartonPairCount(item, packingDetail, sizeQuantities);
      const baseLabel = {
        customerName: pickFirstText(packingDetail?.customerName, item.customerName),
        storeLine: pickFirstText(packingDetail?.warehouseStoreNo, item.warehouseStoreNo),
        factoryOrderNo: pickFirstText(item.printOrderNo),
        style: pickFirstText(packingDetail?.customerStyleNo, item.customerStyleNo, packingDetail?.companyStyleNo, item.developmentNo),
        material: pickFirstText(packingDetail?.material, packingDetail?.itemNumber, item.englishMaterial, item.upperMaterial),
        color: pickFirstText(packingDetail?.customerColor, item.englishColor),
        number: pickFirstText(pairCount),
        po: getCartonLabelPo(item, packingDetail),
        orderNumber: pickFirstText(packingDetail?.customerOrderNo, item.customerOrderNo),
        sizeColumns: templateData.sizeColumns,
        sizeValues: buildCartonSizeValues(templateData.sizeColumns, sizeQuantities, pairCount),
        grossWeight: calculateLabelWeight(item.styleConfig?.grossWeightPerPair, pairCount),
        netWeight: calculateLabelWeight(item.styleConfig?.netWeightPerPair, pairCount),
      };
      return expandCartonNumbers(
        pickFirstText(packingDetail?.cartonStart, item.cartonStart),
        pickFirstText(packingDetail?.cartonEnd, item.cartonEnd),
        packingDetail?.cartonCount ?? item.cartonCount,
      ).flatMap((cartonNumber) =>
        Array.from({ length: CARTON_LABEL_COPIES_PER_CARTON }, () => ({
          ...baseLabel,
          cartonNumber,
        })),
      );
    }),
  );

  return labels.length > 0 ? labels : [templateData, templateData];
}

function chunkCartonLabelData(labels: CartonLabelTemplateData[]) {
  const pages: CartonLabelTemplateData[][] = [];
  for (let index = 0; index < labels.length; index += 2) {
    pages.push(labels.slice(index, index + 2));
  }
  return pages.length > 0 ? pages : [[defaultCartonLabelData, defaultCartonLabelData]];
}

function getCartonPageCount(format: CartonPrintFormatKey, printItems: PrintSelectionItem[]) {
  return chunkCartonLabelData(buildCartonLabelData(format, printItems)).length;
}

function buildInnerBoxLabels(items: PrintSelectionItem[]) {
  if (items.length === 0) {
    return defaultInnerBoxLabels;
  }

  const labels = items.flatMap((item) => getPrintItemPackingSources(item).flatMap((packingDetail) => {
    const baseLabel = {
      styleName: pickFirstText(packingDetail?.customerStyleNo, item.customerStyleNo, packingDetail?.companyStyleNo, item.developmentNo, "SCHEDULE"),
      material: pickFirstText(packingDetail?.material, packingDetail?.itemNumber, item.englishMaterial, item.upperMaterial),
      colour: pickFirstText(packingDetail?.customerColor, item.englishColor),
    };
    const sizeEntries = getSortedSizeEntries(getSourceSizeQuantities(item, packingDetail));
    if (sizeEntries.length === 0) {
      return [{ ...baseLabel, size: "" }];
    }
    return sizeEntries.flatMap(([size, count]) =>
      Array.from({ length: Math.max(1, Math.trunc(count)) }, () => ({
        ...baseLabel,
        size,
      })),
    );
  }));

  return labels.length > 0 ? labels : defaultInnerBoxLabels;
}

function chunkInnerBoxLabels(labels: InnerBoxLabelData[]) {
  const pages: InnerBoxLabelData[][] = [];
  for (let index = 0; index < labels.length; index += INNER_BOX_LABELS_PER_PAGE) {
    pages.push(labels.slice(index, index + INNER_BOX_LABELS_PER_PAGE));
  }
  return pages.length > 0 ? pages : [defaultInnerBoxLabels];
}

function getInnerBoxPageCount(printItems: PrintSelectionItem[]) {
  if (printItems.length === 0) {
    return 1;
  }
  return chunkInnerBoxLabels(buildInnerBoxLabels(printItems)).length;
}

function fillRightSideIfNeeded(labels: InnerBoxLabelData[]) {
  if (labels.length % 2 === 0) {
    return labels;
  }
  return [...labels, { styleName: "", material: "", colour: "", size: "" }];
}

function applyPrintPageSize(format: PrintFormatKey) {
  const styleId = "label-print-page-size";
  const pageSize = format === "inner-box-a4" ? "A4 portrait" : "A4 landscape";
  let style = document.getElementById(styleId) as HTMLStyleElement | null;
  if (!style) {
    style = document.createElement("style");
    style.id = styleId;
    document.head.appendChild(style);
  }
  style.textContent = `@media print { @page { size: ${pageSize}; margin: 0; } }`;
}

export default function PrintSelectionPage({ title }: PrintSelectionPageProps) {
  const { message } = App.useApp();
  const printTargetTitle = title.replace(/^打印/, "");
  const printFormatTarget = getPrintFormatTarget(title);
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [selectedOrderId, setSelectedOrderId] = useState<number>();
  const [details, setDetails] = useState<OrderRecordDetail[]>([]);
  const [packingDetails, setPackingDetails] = useState<OrderPackingDetail[]>([]);
  const [styleConfigsByDevelopmentNo, setStyleConfigsByDevelopmentNo] = useState<Map<string, StyleConfig>>(new Map());
  const [selectedDetailIds, setSelectedDetailIds] = useState<Key[]>([]);
  const [selectedPrintIds, setSelectedPrintIds] = useState<Key[]>([]);
  const [printItems, setPrintItems] = useState<PrintSelectionItem[]>([]);
  const [developmentNoPaths, setDevelopmentNoPaths] = useState<string[][]>([]);
  const [formatModalOpen, setFormatModalOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewMode, setPreviewMode] = useState<PreviewMode>("print");
  const [previewPage, setPreviewPage] = useState(1);
  const [selectedPrintFormat, setSelectedPrintFormat] = useState<PrintFormatKey>(() => getDefaultPrintFormat(title));
  const [orderLoading, setOrderLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);

  const loadOrders = useCallback(async () => {
    setOrderLoading(true);
    try {
      const page = await fetchOrders({ page: 1, size: 100 });
      setOrders(page.records);
      setSelectedOrderId((current) => current || page.records[0]?.id);
    } catch (error) {
      setOrders([]);
      message.error(error instanceof Error ? error.message : "订单加载失败");
    } finally {
      setOrderLoading(false);
    }
  }, [message]);

  const loadDetails = useCallback(async () => {
    if (!selectedOrderId) {
      setDetails([]);
      setPackingDetails([]);
      setStyleConfigsByDevelopmentNo(new Map());
      return;
    }
    setDetailLoading(true);
    try {
      const [data, nextPackingDetails] = await Promise.all([
        fetchOrderDetails(selectedOrderId),
        fetchOrderPackingDetails(selectedOrderId),
      ]);
      setDetails(data);
      setPackingDetails(nextPackingDetails);
      const developmentNos = Array.from(new Set([
        ...data.map((item) => item.developmentNo),
        ...nextPackingDetails.map((item) => item.companyStyleNo),
      ].map((value) => value?.trim()).filter((value): value is string => Boolean(value))));
      if (developmentNos.length > 0) {
        const configPage = await fetchStyleConfigs({ developmentNos: developmentNos.join(","), page: 1, size: 100 });
        setStyleConfigsByDevelopmentNo(buildStyleConfigMap(configPage.records));
      } else {
        setStyleConfigsByDevelopmentNo(new Map());
      }
      setSelectedDetailIds([]);
      setDevelopmentNoPaths([]);
    } catch (error) {
      setDetails([]);
      setPackingDetails([]);
      setStyleConfigsByDevelopmentNo(new Map());
      message.error(error instanceof Error ? error.message : "订单明细加载失败");
    } finally {
      setDetailLoading(false);
    }
  }, [message, selectedOrderId]);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  useEffect(() => {
    void loadDetails();
  }, [loadDetails]);

  useEffect(() => {
    setSelectedPrintFormat(getDefaultPrintFormat(title));
  }, [title]);

  useEffect(() => {
    if (previewOpen) {
      setPreviewPage(1);
    }
  }, [previewOpen, selectedPrintFormat]);

  const printItemKeys = useMemo<Key[]>(() => printItems.map((item) => item.id), [printItems]);

  const printItemKeySet = useMemo(() => new Set<Key>(printItemKeys), [printItemKeys]);

  const selectedOrder = useMemo(
    () => orders.find((order) => order.id === selectedOrderId),
    [orders, selectedOrderId],
  );
  const selectedOrderNo = selectedOrder ? buildOrderLabel(selectedOrder) : selectedOrderId ? `订单 ${selectedOrderId}` : "-";

  const leftSelectedRowKeys = useMemo(
    () => Array.from(new Set<Key>([...printItemKeys, ...selectedDetailIds])),
    [printItemKeys, selectedDetailIds],
  );

  const developmentNoOptions = useMemo(() => buildDevelopmentNoOptions(details), [details]);

  const selectedDevelopmentNos = useMemo(() => {
    return new Set(
      developmentNoPaths
        .map((path) => path[path.length - 1])
        .filter((value): value is string => Boolean(value)),
    );
  }, [developmentNoPaths]);

  const filteredDetails = useMemo(() => {
    if (selectedDevelopmentNos.size === 0) {
      return details;
    }
    return details.filter((item) =>
      Array.from(selectedDevelopmentNos).some((developmentNo) =>
        includesKeyword(item.developmentNo, developmentNo),
      ),
    );
  }, [details, selectedDevelopmentNos]);

  const hasMatchedPacking = useCallback(
    (detail: OrderRecordDetail) => hasMatchingPackingDetails(detail, packingDetails),
    [packingDetails],
  );

  const hasRequiredWeightConfig = useCallback(
    (detail: OrderRecordDetail) => {
      if (printFormatTarget !== "outer-carton") {
        return true;
      }
      const matchedPackingDetails = getMatchingPackingDetails(detail, packingDetails);
      if (matchedPackingDetails.length === 0) {
        return false;
      }
      return hasWeightConfig(findStyleConfig(styleConfigsByDevelopmentNo, detail, matchedPackingDetails[0]));
    },
    [packingDetails, printFormatTarget, styleConfigsByDevelopmentNo],
  );

  const ensurePrintItemsReady = () => {
    if (printItems.length === 0) {
      message.warning(`请先添加待打印${printTargetTitle}`);
      return false;
    }
    if (printFormatTarget === "outer-carton" && printItems.some((item) => !hasWeightConfig(item.styleConfig))) {
      message.warning("外箱贴标必须先维护净重/毛重，不能打印");
      return false;
    }
    return true;
  };

  const addPrintItems = (items: OrderRecordDetail[]) => {
    const printOrderNo = selectedOrder ? buildOrderLabel(selectedOrder) : selectedOrderId ? `订单 ${selectedOrderId}` : "-";
    const missingPackingCount = items.filter((item) => !hasMatchedPacking(item)).length;
    const missingWeightCount = items.filter((item) => hasMatchedPacking(item) && !hasRequiredWeightConfig(item)).length;
    const eligibleItems = items.filter((item) => hasMatchedPacking(item) && hasRequiredWeightConfig(item));
    const blockedMessages = [
      missingPackingCount > 0 ? `${missingPackingCount} 条没有对应的装箱单明细` : "",
      missingWeightCount > 0 ? `${missingWeightCount} 条缺少净重/毛重` : "",
    ].filter(Boolean);
    if (blockedMessages.length > 0) {
      message.warning(`${blockedMessages.join("，")}，不能移动到右侧`);
    }
    if (eligibleItems.length === 0) {
      setSelectedPrintIds([]);
      return;
    }
    setPrintItems((current) => {
      const currentIdSet = new Set(current.map((item) => item.id));
      const nextItems = eligibleItems
        .filter((item) => !currentIdSet.has(item.id))
        .map((item) => {
          const matchedPackingDetails = getMatchingPackingDetails(item, packingDetails);
          return {
            ...item,
            printOrderNo,
            packingDetails: matchedPackingDetails,
            styleConfig: findStyleConfig(styleConfigsByDevelopmentNo, item, matchedPackingDetails[0]),
          };
        });
      if (nextItems.length === 0) {
        return current;
      }
      return [...current, ...nextItems];
    });
    setSelectedPrintIds([]);
  };

  const addSelectedPrintItems = () => {
    const selectedSet = new Set(selectedDetailIds);
    const orderRecordDetails = details.filter((item) => selectedSet.has(item.id));
    addPrintItems(orderRecordDetails);
    setSelectedDetailIds([]);
  };

  const removePrintItems = () => {
    const selectedSet = new Set(selectedPrintIds);
    setPrintItems((current) => current.filter((item) => !selectedSet.has(item.id)));
    setSelectedPrintIds([]);
  };

  const openPrintFormatModal = () => {
    if (!ensurePrintItemsReady()) {
      return;
    }
    setPreviewMode("print");
    setFormatModalOpen(true);
  };

  const openTemplateFormatModal = () => {
    setPreviewMode("template");
    setFormatModalOpen(true);
  };

  const printSelectedFormat = () => {
    if (!ensurePrintItemsReady()) {
      return;
    }
    setFormatModalOpen(false);
    applyPrintPageSize(selectedPrintFormat);
    window.setTimeout(() => window.print(), 0);
  };

  const previewSelectedFormat = (format: PrintFormatKey = selectedPrintFormat) => {
    if (previewMode === "print" && !ensurePrintItemsReady()) {
      return;
    }
    setSelectedPrintFormat(format);
    setFormatModalOpen(false);
    setPreviewOpen(true);
  };

  const backToPrintFormatModal = () => {
    setPreviewOpen(false);
    setFormatModalOpen(true);
  };

  const leftRowSelection = {
    selectedRowKeys: leftSelectedRowKeys,
    getCheckboxProps: (record: OrderRecordDetail) => ({
      disabled: printItemKeySet.has(record.id) || !hasMatchedPacking(record) || !hasRequiredWeightConfig(record),
    }),
    onChange: (nextKeys: Key[]) => {
      setSelectedDetailIds(nextKeys.filter((key) => {
        const detail = details.find((item) => item.id === key);
        return detail && !printItemKeySet.has(key) && hasMatchedPacking(detail) && hasRequiredWeightConfig(detail);
      }));
    },
    onSelectAll: (selected: boolean, _selectedRows: OrderRecordDetail[], changedRows: OrderRecordDetail[]) => {
      const changedIds = new Set(changedRows.map((item) => item.id));
      setSelectedDetailIds((current) => {
        if (selected) {
          return Array.from(
            new Set<Key>([
              ...current,
              ...changedRows.filter((item) => hasMatchedPacking(item) && hasRequiredWeightConfig(item)).map((item) => item.id),
            ]),
          );
        }
        return current.filter((key) => !changedIds.has(key as number));
      });
    },
  };

  const leftRowClassName = (record: OrderRecordDetail) => {
    return printItemKeySet.has(record.id) || !hasMatchedPacking(record) || !hasRequiredWeightConfig(record)
      ? "print-transfer-row-disabled"
      : "print-transfer-row-clickable";
  };

  const getLeftRowProps = (record: OrderRecordDetail) => ({
    onClick: (event: MouseEvent<HTMLElement>) => {
      const target = event.target as HTMLElement;
      if (target.closest(".ant-checkbox-wrapper, .ant-table-selection-column, button, a")) {
        return;
      }
      if (printItemKeySet.has(record.id)) {
        return;
      }
      if (!hasMatchedPacking(record)) {
        message.warning("这条订单明细没有对应的装箱单明细，不能移动到右侧");
        return;
      }
      if (!hasRequiredWeightConfig(record)) {
        message.warning("这条订单明细缺少净重/毛重，不能移动到右侧");
        return;
      }
      setSelectedDetailIds((current) =>
        current.includes(record.id) ? current.filter((key) => key !== record.id) : [...current, record.id],
      );
    },
  });

  const detailColumns = useMemo<ColumnsType<OrderRecordDetail>>(
    () => [
      { title: "开发编号", dataIndex: "developmentNo", width: 140, render: formatEmpty },
      { title: "尺码数量", dataIndex: "sizeQuantities", width: 220, render: renderSizeQuantities },
      { title: "双数", dataIndex: "quantity", width: 80, align: "right", render: formatEmpty },
      { title: "箱数", dataIndex: "cartonCount", width: 80, align: "right", render: formatEmpty },
      { title: "开始箱号", dataIndex: "cartonStart", width: 110, render: formatEmpty },
      { title: "结束箱号", dataIndex: "cartonEnd", width: 110, render: formatEmpty },
    ],
    [],
  );

  const leftColumns = useMemo<ColumnsType<OrderRecordDetail>>(
    () => [
      { title: "订单流水号", key: "orderNo", width: 130, render: () => formatEmpty(selectedOrderNo) },
      ...detailColumns,
    ],
    [detailColumns, selectedOrderNo],
  );

  const printColumns = useMemo<ColumnsType<PrintSelectionItem>>(
    () => [
      { title: "订单流水号", dataIndex: "printOrderNo", width: 130, render: formatEmpty },
      ...detailColumns,
    ],
    [detailColumns],
  );
  const availablePrintFormatOptions = useMemo(
    () => printFormatOptions.filter((option) => option.target === printFormatTarget),
    [printFormatTarget],
  );
  const selectedPrintFormatLabel = printFormatTemplates[selectedPrintFormat].label;
  const selectedPrintFormatIsInnerBox = selectedPrintFormat === "inner-box-a4";
  const previewPrintItems = previewMode === "template" ? templatePrintItems : printItems;
  const hasPrintItems = printItems.length > 0;
  const selectedPrintFormatIsInnerBoxTemplate = selectedPrintFormatIsInnerBox && previewPrintItems.length === 0;
  const previewPageCount = useMemo(() => {
    if (selectedPrintFormatIsInnerBox) {
      return getInnerBoxPageCount(previewPrintItems);
    }
    if (isCartonPrintFormat(selectedPrintFormat)) {
      return getCartonPageCount(selectedPrintFormat, previewPrintItems);
    }
    return 1;
  }, [previewPrintItems, selectedPrintFormat, selectedPrintFormatIsInnerBox]);

  useEffect(() => {
    setPreviewPage((current) => Math.min(Math.max(current, 1), previewPageCount));
  }, [previewPageCount]);

  return (
    <div className="workspace">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>{title}</Typography.Title>
          <Typography.Text type="secondary">
            从左侧订单明细中选择要打印的数据，移动到右侧待打印列表。
          </Typography.Text>
        </div>
        <Space wrap>
          <Button type="primary" icon={<PrinterOutlined />} disabled={!hasPrintItems} onClick={openPrintFormatModal}>
            打印预览
          </Button>
          <Button icon={<EyeOutlined />} onClick={openTemplateFormatModal}>
            查看模板
          </Button>
        </Space>
      </div>

      <div className="print-transfer-layout">
        <div className="page-panel print-transfer-panel">
          <div className="print-transfer-heading">
            <div className="print-transfer-heading-main">
              <Typography.Title level={4}>订单明细表数据</Typography.Title>
              <Space className="print-transfer-filters" wrap>
                <Select
                  className="print-order-select"
                  placeholder="订单号"
                  loading={orderLoading}
                  showSearch
                  optionFilterProp="label"
                  value={selectedOrderId}
                  options={orders.map((order) => ({ value: order.id, label: buildOrderLabel(order) }))}
                  onChange={(value) => setSelectedOrderId(value)}
                />
                <Cascader
                  allowClear
                  className="print-development-cascader"
                  displayRender={(labels) => labels.join("-")}
                  maxTagCount="responsive"
                  multiple
                  options={developmentNoOptions}
                  placeholder="开发编号"
                  showCheckedStrategy={Cascader.SHOW_CHILD}
                  showSearch
                  value={developmentNoPaths}
                  onChange={(value) => setDevelopmentNoPaths(value as string[][])}
                />
                <Button icon={<ReloadOutlined />} onClick={() => void loadDetails()}>
                  刷新明细
                </Button>
              </Space>
            </div>
            <Typography.Text type="secondary">
              {renderCountText(
                filteredDetails.length,
                details.length,
                "明细",
                selectedDevelopmentNos.size > 0,
              )}
            </Typography.Text>
          </div>
          <Table
            rowKey="id"
            loading={detailLoading}
            columns={leftColumns}
            dataSource={filteredDetails}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            scroll={{ x: 870 }}
            className="data-table"
            rowClassName={leftRowClassName}
            rowSelection={leftRowSelection}
            onRow={getLeftRowProps}
          />
        </div>

        <div className="print-transfer-actions">
          <Button
            type="primary"
            icon={<ArrowRightOutlined />}
            disabled={!selectedDetailIds.length}
            onClick={addSelectedPrintItems}
          />
          <Button
            icon={<ArrowLeftOutlined />}
            disabled={!selectedPrintIds.length}
            onClick={removePrintItems}
          />
        </div>

        <div className="page-panel print-transfer-panel">
          <div className="print-transfer-heading">
            <div className="print-transfer-heading-main">
              <Typography.Title level={4}>待打印{printTargetTitle}</Typography.Title>
            </div>
            <Typography.Text type="secondary">{printItems.length} 条待打印</Typography.Text>
          </div>
          <Table
            rowKey="id"
            columns={printColumns}
            dataSource={printItems}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            scroll={{ x: 870 }}
            className="data-table"
            rowSelection={{
              selectedRowKeys: selectedPrintIds,
              onChange: setSelectedPrintIds,
            }}
          />
        </div>
      </div>

      <Modal
        open={formatModalOpen}
        title={previewMode === "template" ? "选择模板格式" : "选择打印格式"}
        onCancel={() => setFormatModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setFormatModalOpen(false)}>
            取消
          </Button>,
          previewMode === "template" ? (
            <Button key="template" type="primary" icon={<EyeOutlined />} onClick={() => previewSelectedFormat()}>
              查看模板
            </Button>
          ) : (
            <Button
              key="print"
              type="primary"
              icon={<PrinterOutlined />}
              disabled={!hasPrintItems}
              onClick={printSelectedFormat}
            >
              打印
            </Button>
          ),
        ]}
        destroyOnClose
      >
        <Radio.Group
          className="print-format-options"
          value={selectedPrintFormat}
          onChange={(event) => setSelectedPrintFormat(event.target.value)}
        >
          {availablePrintFormatOptions.map((option) => (
            <div
              className={`print-format-option${selectedPrintFormat === option.value ? " print-format-option-selected" : ""}`}
              key={option.value}
              onClick={() => setSelectedPrintFormat(option.value)}
            >
              <Radio value={option.value}>{option.label}</Radio>
              <Button
                size="small"
                onClick={(event) => {
                  event.stopPropagation();
                  previewSelectedFormat(option.value);
                }}
              >
                {previewMode === "template" ? "查看模板" : "预览"}
              </Button>
            </div>
          ))}
        </Radio.Group>
      </Modal>

      <Modal
        open={previewOpen}
        title={`${selectedPrintFormatLabel}${previewMode === "template" ? "模板预览" : "预览"}`}
        onCancel={backToPrintFormatModal}
        width={selectedPrintFormatIsInnerBoxTemplate ? 560 : selectedPrintFormatIsInnerBox ? 860 : 1120}
        footer={null}
        destroyOnClose
      >
        <div
          className={`label-preview label-preview-modal ${
            selectedPrintFormatIsInnerBoxTemplate
              ? "inner-box-label-template-preview"
              : selectedPrintFormatIsInnerBox
                ? "inner-box-label-preview"
                : "carton-label-preview"
          }`}
        >
          <div
            className={
              selectedPrintFormatIsInnerBoxTemplate
                ? "inner-box-label-template-preview-scale"
                : selectedPrintFormatIsInnerBox
                  ? "inner-box-label-preview-scale"
                  : "carton-label-preview-scale"
            }
          >
            <PrintFormatSheet
              format={selectedPrintFormat}
              printItems={previewPrintItems}
              cartonPageIndex={isCartonPrintFormat(selectedPrintFormat) ? previewPage - 1 : undefined}
              innerBoxPageIndex={selectedPrintFormatIsInnerBox ? previewPage - 1 : undefined}
            />
          </div>
        </div>
        {previewPageCount > 1 ? (
          <div className="label-preview-pagination">
            <Pagination
              current={previewPage}
              pageSize={1}
              total={previewPageCount}
              showSizeChanger={false}
              onChange={setPreviewPage}
            />
          </div>
        ) : null}
      </Modal>

      <div className="label-print-root" aria-hidden>
        <div className="label-preview">
          <PrintFormatSheet format={selectedPrintFormat} printItems={printItems} />
        </div>
      </div>
    </div>
  );
}

function PrintFormatSheet({
  format,
  printItems,
  cartonPageIndex,
  innerBoxPageIndex,
}: {
  format: PrintFormatKey;
  printItems: PrintSelectionItem[];
  cartonPageIndex?: number;
  innerBoxPageIndex?: number;
}) {
  if (format === "inner-box-a4") {
    return <InnerBoxLabelA4 printItems={printItems} pageIndex={innerBoxPageIndex} />;
  }
  if (isCartonPrintFormat(format)) {
    return <CartonLabelA4 format={format} printItems={printItems} pageIndex={cartonPageIndex} />;
  }
  return null;
}

function CartonLabelA4({
  format,
  printItems,
  pageIndex,
}: {
  format: CartonPrintFormatKey;
  printItems: PrintSelectionItem[];
  pageIndex?: number;
}) {
  const pages = chunkCartonLabelData(buildCartonLabelData(format, printItems));
  const safePageIndex =
    typeof pageIndex === "number" ? Math.min(Math.max(pageIndex, 0), pages.length - 1) : undefined;
  const pageEntries =
    safePageIndex === undefined
      ? pages.map((labels, index) => [index, labels] as const)
      : ([[safePageIndex, pages[safePageIndex]]] as const);

  return (
    <div className="carton-label-pages">
      {pageEntries.map(([pageIndex, labels]) => (
        <section className="carton-label-a4" key={`carton-label-page-${pageIndex}`}>
          {labels.map((data, labelIndex) => (
            <CartonLabelTemplate data={data} key={`carton-label-${pageIndex}-${labelIndex}`} />
          ))}
        </section>
      ))}
    </div>
  );
}

function CartonLabelTemplate({ data }: { data: CartonLabelTemplateData }) {
  const getSizeCellClassName = (value: string) => {
    const isDecimalSize = /^\d+\.\d+$/.test(value);
    return isDecimalSize ? "carton-label-size-cell carton-label-size-cell-decimal" : "carton-label-size-cell";
  };

  return (
    <section className="carton-label-sheet">
      <div className="carton-label-brand">JEFFREY CAMPBELL</div>
      <div className="carton-label-top">
        <div className="carton-label-side-brand">
          <span>
            JEFFREY
            <br />
            CAMPBELL
          </span>
        </div>
        <div className="carton-label-carton-box">
          <div className="carton-label-customer">{data.customerName}</div>
          <div className="carton-label-empty-line">{data.storeLine}</div>
          <div className="carton-label-caption">CARTON MUMBER</div>
          <div className="carton-label-carton-number">{data.cartonNumber}</div>
        </div>
      </div>

      <CartonInfoRow label="Factory order NO:" value={data.factoryOrderNo} />
      <CartonInfoRow label="STYLE:" value={data.style} />
      <CartonInfoRow label="MATERIAL:" value={data.material} />
      <CartonInfoRow adaptiveValue label="COLOR:" value={data.color} />
      <CartonInfoRow label="Number:" value={data.number} />
      <CartonInfoRow label="PO#:" value={data.po} />
      <CartonInfoRow label="ORDER NUMBER:" value={data.orderNumber} />

      <div className="carton-label-size-grid">
        {data.sizeColumns.map((value, index) => (
          <div key={`size-${index}`} className={getSizeCellClassName(value)}>
            {value}
          </div>
        ))}
        {data.sizeValues.map((value, index) => (
          <div key={`value-${index}`} className={getSizeCellClassName(value)}>
            {value}
          </div>
        ))}
        <div className="carton-label-weight-cell carton-label-weight-gross">G.W: {data.grossWeight}</div>
        <div className="carton-label-weight-cell carton-label-weight-net">N.W:{data.netWeight}</div>
      </div>
      <div className="carton-label-origin">MADE IN CHINA</div>
    </section>
  );
}

function InnerBoxLabelA4({ printItems, pageIndex }: { printItems: PrintSelectionItem[]; pageIndex?: number }) {
  if (printItems.length === 0) {
    return (
      <div className="inner-box-label-pages">
        <div className="inner-box-label-template-only">
          <InnerBoxLabel data={defaultInnerBoxLabel} />
        </div>
      </div>
    );
  }

  const pages = chunkInnerBoxLabels(buildInnerBoxLabels(printItems));
  const safePageIndex =
    typeof pageIndex === "number" ? Math.min(Math.max(pageIndex, 0), pages.length - 1) : undefined;
  const pageEntries =
    safePageIndex === undefined
      ? pages.map((labels, index) => [index, labels] as const)
      : ([[safePageIndex, pages[safePageIndex]]] as const);

  return (
    <div className="inner-box-label-pages">
      {pageEntries.map(([pageIndex, labels]) => (
        <section className="inner-box-label-a4" key={`inner-box-page-${pageIndex}`}>
          <div className="inner-box-label-grid">
            {fillRightSideIfNeeded(labels).map((label, labelIndex) => (
              <InnerBoxLabel
                data={label}
                key={`inner-box-label-${pageIndex}-${labelIndex}`}
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

function InnerBoxLabel({ data }: { data: InnerBoxLabelData }) {
  return (
    <div className="inner-box-label">
      <div className="inner-box-label-cell inner-box-label-header">STYEL NAME</div>
      <div className="inner-box-label-cell inner-box-label-header">MATERIAL</div>
      <div className="inner-box-label-cell inner-box-label-header">COLOUR</div>
      <div className="inner-box-label-cell inner-box-label-header">SIZE</div>
      <div className="inner-box-label-cell inner-box-label-style">{data.styleName}</div>
      <div className="inner-box-label-cell inner-box-label-material">{data.material}</div>
      <div className="inner-box-label-cell inner-box-label-colour">{data.colour}</div>
      <div className="inner-box-label-cell inner-box-label-size">{data.size}</div>
    </div>
  );
}

function CartonInfoRow({ label, value, adaptiveValue = false }: { label: string; value: string; adaptiveValue?: boolean }) {
  return (
    <div className="carton-label-info-row">
      <div className="carton-label-info-label">{label}</div>
      {adaptiveValue ? <CartonAdaptiveInfoValue value={value} /> : <div className="carton-label-info-value">{value}</div>}
    </div>
  );
}
