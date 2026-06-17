import { EyeOutlined, PrinterOutlined } from "@ant-design/icons";
import { App, Button, Modal, Pagination, Radio, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { Key, MouseEvent } from "react";
import { batchRecordPrintProcess, fetchOrderDetails, fetchOrderPackingDetails, fetchOrders } from "../api/orderApi";
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
  const isInnerBox = format === "inner-box-a4";
  let style = document.getElementById(styleId) as HTMLStyleElement | null;
  if (!style) {
    style = document.createElement("style");
    style.id = styleId;
    document.head.appendChild(style);
  }
  style.textContent = `@media print { @page { size: ${pageSize}; margin: 0; }${isInnerBox ? ".label-print-root{page:inner-box-label-page;}" : ""} }`;
}

export default function PrintSelectionPage({ title }: PrintSelectionPageProps) {
  const { message } = App.useApp();
  const printTargetTitle = title.replace(/^打印/, "");
  const printFormatTarget = getPrintFormatTarget(title);
  const processType = printFormatTarget === "inner-box" ? 5 : 6;

  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [orderLoading, setOrderLoading] = useState(false);
  const [detailsMap, setDetailsMap] = useState<Map<number, OrderRecordDetail[]>>(new Map());
  const [packingDetailsMap, setPackingDetailsMap] = useState<Map<number, OrderPackingDetail[]>>(new Map());
  const [styleConfigsMap, setStyleConfigsMap] = useState<Map<string, StyleConfig>>(new Map());
  const [selectedDetailIds, setSelectedDetailIds] = useState<Set<number>>(new Set());
  const [printItems, setPrintItems] = useState<PrintSelectionItem[]>([]);
  const [formatModalOpen, setFormatModalOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewMode, setPreviewMode] = useState<PreviewMode>("print");
  const [previewPage, setPreviewPage] = useState(1);
  const [selectedPrintFormat, setSelectedPrintFormat] = useState<PrintFormatKey>(() => getDefaultPrintFormat(title));

  const loadOrders = useCallback(async () => {
    setOrderLoading(true);
    try {
      const page = await fetchOrders({ page: 1, size: 100 });
      setOrders(page.records);
    } catch (error) {
      setOrders([]);
      message.error(error instanceof Error ? error.message : "订单加载失败");
    } finally {
      setOrderLoading(false);
    }
  }, [message]);

  useEffect(() => { loadOrders(); }, [loadOrders]);
  useEffect(() => { setSelectedPrintFormat(getDefaultPrintFormat(title)); }, [title]);

  const loadOrderDetails = useCallback(async (orderId: number) => {
    if (detailsMap.has(orderId)) return;
    try {
      const details = await fetchOrderDetails(orderId);
      setDetailsMap(prev => new Map(prev).set(orderId, details));
    } catch (error) {
      message.error(error instanceof Error ? error.message : "明细加载失败");
    }
  }, [detailsMap, message]);

  const getStyleGroups = useCallback((orderId: number) => {
    const details = detailsMap.get(orderId) || [];
    const groups = new Map<string, {
      details: OrderRecordDetail[];
      hasOuterCarton: boolean;
      hasInnerBox: boolean;
      detailIds: number[];
    }>();

    for (const detail of details) {
      const devNo = detail.developmentNo || "未知";
      if (!groups.has(devNo)) {
        groups.set(devNo, { details: [], hasOuterCarton: false, hasInnerBox: false, detailIds: [] });
      }
      const group = groups.get(devNo)!;
      group.details.push(detail);
      group.detailIds.push(detail.id);
      if (detail.processes) {
        for (const p of detail.processes) {
          if (p.processType === 6) group.hasOuterCarton = true;
          if (p.processType === 5) group.hasInnerBox = true;
        }
      }
    }

    return Array.from(groups.entries()).map(([developmentNo, group]) => ({
      developmentNo,
      detailIds: group.detailIds,
      hasOuterCarton: group.hasOuterCarton,
      hasInnerBox: group.hasInnerBox,
      totalQuantity: group.details.reduce((sum, d) => sum + (d.quantity || 0), 0),
      totalCartonCount: group.details.reduce((sum, d) => sum + (d.cartonCount || 0), 0),
    }));
  }, [detailsMap]);

  const isGroupSelected = useCallback((detailIds: number[]) => {
    return detailIds.every(id => selectedDetailIds.has(id));
  }, [selectedDetailIds]);

  const toggleGroup = useCallback((detailIds: number[]) => {
    setSelectedDetailIds(prev => {
      const next = new Set(prev);
      const allSelected = detailIds.every(id => next.has(id));
      if (allSelected) {
        detailIds.forEach(id => next.delete(id));
      } else {
        detailIds.forEach(id => next.add(id));
      }
      return next;
    });
  }, []);

  const selectedCount = selectedDetailIds.size;

  const openPrintFormatModal = async () => {
    if (selectedDetailIds.size === 0) {
      message.warning(`请先选择要打印${printTargetTitle}的款号`);
      return;
    }

    const selectedDetails: OrderRecordDetail[] = [];
    const affectedOrderIds = new Set<number>();
    detailsMap.forEach((details) => {
      details.forEach(d => {
        if (selectedDetailIds.has(d.id)) {
          selectedDetails.push(d);
          affectedOrderIds.add(d.orderId);
        }
      });
    });

    if (selectedDetails.length === 0) {
      message.warning("请先展开订单并选择要打印的款号");
      return;
    }

    try {
      let allPackingDetails: OrderPackingDetail[] = [];
      if (printFormatTarget === "outer-carton") {
        for (const oid of affectedOrderIds) {
          if (!packingDetailsMap.has(oid)) {
            const pd = await fetchOrderPackingDetails(oid);
            setPackingDetailsMap(prev => new Map(prev).set(oid, pd));
            allPackingDetails = [...allPackingDetails, ...pd];
          } else {
            allPackingDetails = [...allPackingDetails, ...(packingDetailsMap.get(oid) || [])];
          }
        }

        const allDevNos = Array.from(new Set(
          selectedDetails.map(d => d.developmentNo).filter((v): v is string => Boolean(v)),
        ));
        if (allDevNos.length > 0) {
          const configPage = await fetchStyleConfigs({ developmentNos: allDevNos.join(","), page: 1, size: 100 });
          setStyleConfigsMap(buildStyleConfigMap(configPage.records));
        }
      }

      const currentPackingDetails = allPackingDetails.length > 0 ? allPackingDetails
        : Array.from(packingDetailsMap.values()).flat();

      const items: PrintSelectionItem[] = [];
      for (const detail of selectedDetails) {
        if (printFormatTarget === "outer-carton") {
          const matchedPacking = getMatchingPackingDetails(detail, currentPackingDetails);
          if (matchedPacking.length === 0) {
            message.warning(`款号 ${detail.developmentNo} 没有对应的装箱单明细，已跳过`);
            continue;
          }
          const styleConfig = findStyleConfig(styleConfigsMap, detail, matchedPacking[0]);
          if (!hasWeightConfig(styleConfig)) {
            message.warning(`款号 ${detail.developmentNo} 缺少净重/毛重配置，已跳过`);
            continue;
          }
          items.push({
            ...detail,
            printOrderNo: detail.developmentNo,
            packingDetails: matchedPacking,
            styleConfig,
          });
        } else {
          items.push({ ...detail, printOrderNo: detail.developmentNo });
        }
      }

      if (items.length === 0) {
        message.warning("没有符合打印条件的款号");
        return;
      }

      setPrintItems(items);
      setPreviewMode("print");
      setFormatModalOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "数据加载失败");
    }
  };

  const openTemplateFormatModal = () => {
    setPreviewMode("template");
    setFormatModalOpen(true);
  };

  const printSelectedFormat = () => {
    if (printItems.length === 0) {
      message.warning(`请先选择要打印${printTargetTitle}的款号`);
      return;
    }
    setFormatModalOpen(false);
    applyPrintPageSize(selectedPrintFormat);

    // 记录打印状态（按订单分组）
    const orderGroups = new Map<number, number[]>();
    printItems.forEach(item => {
      const oid = item.orderId;
      if (!orderGroups.has(oid)) orderGroups.set(oid, []);
      orderGroups.get(oid)!.push(item.id);
    });
    orderGroups.forEach((ids, oid) => {
      batchRecordPrintProcess(oid, ids, processType).catch(() => {});
    });

    window.setTimeout(() => window.print(), 0);
  };

  const previewSelectedFormat = (format: PrintFormatKey = selectedPrintFormat) => {
    setSelectedPrintFormat(format);
    setFormatModalOpen(false);
    setPreviewOpen(true);
  };

  const backToPrintFormatModal = () => {
    setPreviewOpen(false);
    setFormatModalOpen(true);
  };

  useEffect(() => {
    if (previewOpen) setPreviewPage(1);
  }, [previewOpen, selectedPrintFormat]);

  const selectedPrintFormatLabel = printFormatTemplates[selectedPrintFormat].label;
  const selectedPrintFormatIsInnerBox = selectedPrintFormat === "inner-box-a4";
  const previewPrintItems = previewMode === "template" ? templatePrintItems : printItems;
  const selectedPrintFormatIsInnerBoxTemplate = selectedPrintFormatIsInnerBox && previewPrintItems.length === 0;

  const previewPageCount = useMemo(() => {
    if (selectedPrintFormatIsInnerBox) return getInnerBoxPageCount(previewPrintItems);
    if (isCartonPrintFormat(selectedPrintFormat)) return getCartonPageCount(selectedPrintFormat, previewPrintItems);
    return 1;
  }, [previewPrintItems, selectedPrintFormat, selectedPrintFormatIsInnerBox]);

  useEffect(() => {
    setPreviewPage(p => Math.min(Math.max(p, 1), previewPageCount));
  }, [previewPageCount]);

  const styleColumns: ColumnsType<{
    developmentNo: string;
    detailIds: number[];
    hasOuterCarton: boolean;
    hasInnerBox: boolean;
    totalQuantity: number;
    totalCartonCount: number;
  }> = [
    {
      title: "开发编号",
      dataIndex: "developmentNo",
      width: 180,
      render: (value: string) => <Typography.Text strong>{value}</Typography.Text>,
    },
    { title: "双数", dataIndex: "totalQuantity", width: 80, align: "right" },
    { title: "箱数", dataIndex: "totalCartonCount", width: 80, align: "right" },
    {
      title: "外箱贴标",
      width: 100,
      align: "center",
      render: (_: unknown, record) => (
        record.hasOuterCarton
          ? <Typography.Text type="success">✓ 已打</Typography.Text>
          : <Typography.Text type="secondary">-</Typography.Text>
      ),
    },
    {
      title: "内盒贴标",
      width: 100,
      align: "center",
      render: (_: unknown, record) => (
        record.hasInnerBox
          ? <Typography.Text type="success">✓ 已打</Typography.Text>
          : <Typography.Text type="secondary">-</Typography.Text>
      ),
    },
  ];

  const expandedRowRender = (record: OrderRecord) => {
    if (!detailsMap.has(record.id)) {
      return <div style={{ padding: 24, textAlign: "center", color: "#999" }}>展开以加载明细数据…</div>;
    }
    const groups = getStyleGroups(record.id);
    if (groups.length === 0) {
      return <div style={{ padding: 24, textAlign: "center", color: "#999" }}>该订单没有明细数据</div>;
    }
    return (
      <div style={{ padding: "8px 0" }}>
        <Table
          rowKey="developmentNo"
          dataSource={groups}
          columns={styleColumns}
          pagination={false}
          size="small"
          rowSelection={{
            selectedRowKeys: groups.filter(g => isGroupSelected(g.detailIds)).map(g => g.developmentNo),
            onSelect: (record) => toggleGroup(record.detailIds),
            onSelectAll: (selected, _selectedRows, changeRows) => {
              const allIds = changeRows.flatMap(g => g.detailIds);
              setSelectedDetailIds(prev => {
                const next = new Set(prev);
                if (selected) {
                  allIds.forEach(id => next.add(id));
                } else {
                  allIds.forEach(id => next.delete(id));
                }
                return next;
              });
            },
          }}
        />
      </div>
    );
  };

  return (
    <div className="workspace">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>{title}</Typography.Title>
          <Typography.Text type="secondary">
            展开订单，勾选需要打印的款号，点击"打印选中"按钮。
          </Typography.Text>
        </div>
        <Space wrap>
          <Typography.Text>
            已选 {selectedCount} 项
          </Typography.Text>
          <Button type="primary" icon={<PrinterOutlined />} disabled={selectedCount === 0} onClick={openPrintFormatModal}>
            打印选中
          </Button>
          <Button icon={<EyeOutlined />} onClick={openTemplateFormatModal}>
            查看模板
          </Button>
        </Space>
      </div>

      <Table
        dataSource={orders}
        rowKey="id"
        loading={orderLoading}
        className="data-table"
        columns={[
          { title: "订单流水号", dataIndex: "orderNo", width: 160 },
          { title: "客户", dataIndex: "customerName", width: 120 },
          { title: "总对数", dataIndex: "totalQuantity", width: 80, align: "right" },
          { title: "总箱数", dataIndex: "totalCartonCount", width: 80, align: "right" },
          { title: "创建时间", dataIndex: "createdAt", width: 160 },
        ]}
        expandable={{
          expandedRowRender,
          onExpand: (expanded, record) => {
            if (expanded) loadOrderDetails(record.id);
          },
          rowExpandable: () => true,
        }}
      />

      <Modal
        open={formatModalOpen}
        title={previewMode === "template" ? "选择模板格式" : "选择打印格式"}
        onCancel={() => setFormatModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setFormatModalOpen(false)}>取消</Button>,
          previewMode === "template" ? (
            <Button key="template" type="primary" icon={<EyeOutlined />} onClick={() => previewSelectedFormat()}>
              查看模板
            </Button>
          ) : (
            <Button key="print" type="primary" icon={<PrinterOutlined />} disabled={printItems.length === 0} onClick={printSelectedFormat}>
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
          {printFormatOptions.filter(o => o.target === printFormatTarget).map((option) => (
            <div
              className={`print-format-option${selectedPrintFormat === option.value ? " print-format-option-selected" : ""}`}
              key={option.value}
              onClick={() => setSelectedPrintFormat(option.value)}
            >
              <Radio value={option.value}>{option.label}</Radio>
              <Button size="small" onClick={(event) => {
                event.stopPropagation();
                previewSelectedFormat(option.value);
              }}>
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
        <div className={`label-preview label-preview-modal ${
          selectedPrintFormatIsInnerBoxTemplate ? "inner-box-label-template-preview"
            : selectedPrintFormatIsInnerBox ? "inner-box-label-preview"
              : "carton-label-preview"
        }`}>
          <div className={
            selectedPrintFormatIsInnerBoxTemplate ? "inner-box-label-template-preview-scale"
              : selectedPrintFormatIsInnerBox ? "inner-box-label-preview-scale"
                : "carton-label-preview-scale"
          }>
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
            <Pagination current={previewPage} pageSize={1} total={previewPageCount} showSizeChanger={false} onChange={setPreviewPage} />
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
