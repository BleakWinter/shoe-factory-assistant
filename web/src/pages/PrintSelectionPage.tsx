import {
  CheckCircleOutlined,
  ClearOutlined,
  ClockCircleOutlined,
  EyeOutlined,
  PrinterOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import { App, Button, Checkbox, Modal, Pagination, Radio, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { batchRecordPrintProcess, fetchOrderDetails, fetchOrderPackingDetails, fetchOrders } from "../api/orderApi";
import { fetchStyleConfigs } from "../api/styleConfigApi";
import type { OrderPackingDetail, OrderRecord, OrderRecordDetail } from "../types/order";
import type { StyleConfig } from "../types/styleConfig";
import { getMatchingPackingDetails } from "../utils/orderMatching";

interface PrintSelectionPageProps {
  title: string;
}

type PrintSelectionItem = OrderRecordDetail & {
  packingDetails?: OrderPackingDetail[];
  styleConfig?: StyleConfig;
};

interface PrintStyleGroup {
  id: number;
  developmentNo: string;
  detail: OrderRecordDetail;
  details: OrderRecordDetail[];
  detailIds: number[];
  hasOuterCarton: boolean;
  hasInnerBox: boolean;
  outerPrintedAt?: string;
  innerPrintedAt?: string;
  totalQuantity: number;
  totalCartonCount: number;
  cartonRange: string;
}

type PrintEligibilityStatus = "checking" | "ready" | "missing-packing" | "missing-weight" | "error";

interface PrintEligibility {
  detailId: number;
  status: PrintEligibilityStatus;
  reason: string;
  packingDetails?: OrderPackingDetail[];
  styleConfig?: StyleConfig;
}

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

function formatDateTimeText(value?: string) {
  const text = String(value || "").trim();
  if (!text) {
    return "";
  }
  return text.replace("T", " ").slice(0, 16);
}

function getDetailProcess(detail: OrderRecordDetail, processType: number) {
  return detail.processes?.find(process => process.processType === processType);
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

function mergeStyleConfigsMap(base: Map<string, StyleConfig>, configs: StyleConfig[]) {
  const next = new Map(base);
  configs.forEach((config) => next.set(normalizeKey(config.developmentNo), config));
  return next;
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
        factoryOrderNo: pickFirstText(item.orderNo),
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
  const [outerEligibilityMap, setOuterEligibilityMap] = useState<Map<number, PrintEligibility>>(new Map());
  const [selectedOrderId, setSelectedOrderId] = useState<number>();
  const [selectedDetailIds, setSelectedDetailIds] = useState<Set<number>>(new Set());
  const [hideFinishedOrders, setHideFinishedOrders] = useState(true);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewMode, setPreviewMode] = useState<PreviewMode>("print");
  const [previewPage, setPreviewPage] = useState(1);
  const [selectedPrintFormat, setSelectedPrintFormat] = useState<PrintFormatKey>(() => getDefaultPrintFormat(title));

  const loadOrders = useCallback(async () => {
    setOrderLoading(true);
    try {
      const page = await fetchOrders({
        page: 1,
        size: 100,
        unfinishedProcessType: hideFinishedOrders ? processType : undefined,
      });
      setOrders(page.records);
    } catch (error) {
      setOrders([]);
      message.error(error instanceof Error ? error.message : "订单加载失败");
    } finally {
      setOrderLoading(false);
    }
  }, [hideFinishedOrders, message, processType]);

  useEffect(() => { loadOrders(); }, [loadOrders]);
  useEffect(() => { setSelectedPrintFormat(getDefaultPrintFormat(title)); }, [title]);

  const refreshOuterEligibility = useCallback(async (orderId: number, details: OrderRecordDetail[]) => {
    if (printFormatTarget !== "outer-carton" || details.length === 0) {
      return;
    }

    setOuterEligibilityMap(prev => {
      const next = new Map(prev);
      details.forEach(detail => next.set(detail.id, {
        detailId: detail.id,
        status: "checking",
        reason: "检查中",
      }));
      return next;
    });

    try {
      const currentPackingDetailsMap = new Map(packingDetailsMap);
      let orderPackingDetails = currentPackingDetailsMap.get(orderId);
      if (!orderPackingDetails) {
        orderPackingDetails = await fetchOrderPackingDetails(orderId);
        currentPackingDetailsMap.set(orderId, orderPackingDetails);
        setPackingDetailsMap(currentPackingDetailsMap);
      }

      const matchedPackingByDetailId = new Map<number, OrderPackingDetail[]>();
      const styleLookupKeys = new Set<string>();

      details.forEach((detail) => {
        const matchedPacking = getMatchingPackingDetails(detail, orderPackingDetails || []);
        matchedPackingByDetailId.set(detail.id, matchedPacking);
        if (matchedPacking.length === 0) {
          return;
        }
        if (detail.developmentNo) {
          styleLookupKeys.add(detail.developmentNo);
        }
        matchedPacking.forEach((packingDetail) => {
          if (packingDetail.companyStyleNo) {
            styleLookupKeys.add(packingDetail.companyStyleNo);
          }
        });
      });

      let currentStyleConfigsMap = styleConfigsMap;
      if (styleLookupKeys.size > 0) {
        const configPage = await fetchStyleConfigs({
          developmentNos: Array.from(styleLookupKeys).join(","),
          page: 1,
          size: Math.max(100, styleLookupKeys.size),
        });
        currentStyleConfigsMap = mergeStyleConfigsMap(currentStyleConfigsMap, configPage.records);
        setStyleConfigsMap(currentStyleConfigsMap);
      }

      setOuterEligibilityMap(prev => {
        const next = new Map(prev);
        details.forEach((detail) => {
          const matchedPacking = matchedPackingByDetailId.get(detail.id) || [];
          if (matchedPacking.length === 0) {
            next.set(detail.id, {
              detailId: detail.id,
              status: "missing-packing",
              reason: "缺装箱单",
            });
            return;
          }

          const styleConfig = findStyleConfig(currentStyleConfigsMap, detail, matchedPacking[0]);
          if (!hasWeightConfig(styleConfig)) {
            next.set(detail.id, {
              detailId: detail.id,
              status: "missing-weight",
              reason: "缺净重/毛重",
              packingDetails: matchedPacking,
            });
            return;
          }

          next.set(detail.id, {
            detailId: detail.id,
            status: "ready",
            reason: "可打印",
            packingDetails: matchedPacking,
            styleConfig,
          });
        });
        return next;
      });
    } catch (error) {
      setOuterEligibilityMap(prev => {
        const next = new Map(prev);
        details.forEach(detail => next.set(detail.id, {
          detailId: detail.id,
          status: "error",
          reason: "检查失败",
        }));
        return next;
      });
      message.error(error instanceof Error ? error.message : "外箱打印条件检查失败");
    }
  }, [message, packingDetailsMap, printFormatTarget, styleConfigsMap]);

  const loadOrderDetails = useCallback(async (orderId: number) => {
    if (detailsMap.has(orderId)) return;
    try {
      const details = await fetchOrderDetails(orderId);
      setDetailsMap(prev => new Map(prev).set(orderId, details));
      void refreshOuterEligibility(orderId, details);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "明细加载失败");
    }
  }, [detailsMap, message, refreshOuterEligibility]);

  const selectOrder = useCallback((orderId: number, options?: { keepSelection?: boolean }) => {
    setSelectedOrderId(orderId);
    if (!options?.keepSelection) {
      setSelectedDetailIds(new Set());
    }
    void loadOrderDetails(orderId);
  }, [loadOrderDetails]);

  useEffect(() => {
    if (orders.length === 0) {
      if (selectedOrderId !== undefined) {
        setSelectedOrderId(undefined);
      }
      setSelectedDetailIds(prev => prev.size === 0 ? prev : new Set());
      return;
    }
    const selectedOrderExists = selectedOrderId !== undefined && orders.some(order => order.id === selectedOrderId);
    if (!selectedOrderExists) {
      selectOrder(orders[0].id);
    }
  }, [orders, selectOrder, selectedOrderId]);

  const getStyleGroups = useCallback((orderId: number) => {
    const details = detailsMap.get(orderId) || [];
    return details.map((detail): PrintStyleGroup => {
      const outerProcess = getDetailProcess(detail, 6);
      const innerProcess = getDetailProcess(detail, 5);
      return {
        id: detail.id,
        developmentNo: detail.developmentNo || "未知",
        detail,
        details: [detail],
        detailIds: [detail.id],
        hasOuterCarton: Boolean(outerProcess),
        hasInnerBox: Boolean(innerProcess),
        outerPrintedAt: formatDateTimeText(outerProcess?.lastProcessAt),
        innerPrintedAt: formatDateTimeText(innerProcess?.lastProcessAt),
        totalQuantity: Number(detail.quantity || 0),
        totalCartonCount: Number(detail.cartonCount || 0),
        cartonRange: formatCartonNumber(detail.cartonStart, detail.cartonEnd),
      };
    });
  }, [detailsMap]);

  const selectedDetails = useMemo(() => {
    const details: OrderRecordDetail[] = [];
    detailsMap.forEach((orderDetails) => {
      orderDetails.forEach((detail) => {
        if (selectedDetailIds.has(detail.id)) {
          details.push(detail);
        }
      });
    });
    return details;
  }, [detailsMap, selectedDetailIds]);

  const selectedOrder = useMemo(
    () => orders.find(order => order.id === selectedOrderId),
    [orders, selectedOrderId],
  );
  const selectedOrderNo = selectedOrder?.orderNo;

  const activePrintItems = useMemo<PrintSelectionItem[]>(() => {
    if (printFormatTarget === "inner-box") {
      return selectedDetails.map(detail => ({
        ...detail,
        orderNo: detail.orderNo || selectedOrderNo,
      }));
    }

    return selectedDetails.flatMap((detail) => {
      const eligibility = outerEligibilityMap.get(detail.id);
      if (eligibility?.status !== "ready" || !eligibility.styleConfig) {
        return [];
      }
      return [{
        ...detail,
        orderNo: detail.orderNo || selectedOrderNo,
        packingDetails: eligibility.packingDetails,
        styleConfig: eligibility.styleConfig,
      }];
    });
  }, [outerEligibilityMap, printFormatTarget, selectedDetails, selectedOrderNo]);

  const getGroupPrintEligibility = useCallback((group: PrintStyleGroup) => {
    if (printFormatTarget === "inner-box") {
      return { status: "ready" as const, reason: "可打印", disabled: false };
    }

    const eligibilities = group.detailIds.map(id => outerEligibilityMap.get(id));
    if (eligibilities.some(eligibility => !eligibility || eligibility.status === "checking")) {
      return { status: "checking" as const, reason: "检查中", disabled: true };
    }
    const firstMissingPacking = eligibilities.find(eligibility => eligibility?.status === "missing-packing");
    if (firstMissingPacking) {
      return { status: "missing-packing" as const, reason: firstMissingPacking.reason, disabled: true };
    }
    const firstMissingWeight = eligibilities.find(eligibility => eligibility?.status === "missing-weight");
    if (firstMissingWeight) {
      return { status: "missing-weight" as const, reason: firstMissingWeight.reason, disabled: true };
    }
    const firstError = eligibilities.find(eligibility => eligibility?.status === "error");
    if (firstError) {
      return { status: "error" as const, reason: firstError.reason, disabled: true };
    }
    return { status: "ready" as const, reason: "可打印", disabled: false };
  }, [outerEligibilityMap, printFormatTarget]);

  const currentStyleGroups = useMemo(
    () => selectedOrderId === undefined ? [] : getStyleGroups(selectedOrderId),
    [getStyleGroups, selectedOrderId],
  );

  const selectedGroups = useMemo(() => (
    currentStyleGroups.filter(group =>
      group.detailIds.length > 0 && group.detailIds.every(id => selectedDetailIds.has(id)),
    )
  ), [currentStyleGroups, selectedDetailIds]);

  const printableSelectedGroups = useMemo(() => (
    selectedGroups.filter(group => !getGroupPrintEligibility(group).disabled)
  ), [getGroupPrintEligibility, selectedGroups]);

  const isGroupSelected = useCallback((detailIds: number[]) => {
    return detailIds.every(id => selectedDetailIds.has(id));
  }, [selectedDetailIds]);

  const toggleGroup = useCallback((group: PrintStyleGroup) => {
    const allSelected = group.detailIds.every(id => selectedDetailIds.has(id));
    const eligibility = getGroupPrintEligibility(group);
    if (!allSelected && eligibility.disabled) {
      message.warning(`款号 ${group.developmentNo} ${eligibility.reason}，不能选择`);
      return;
    }

    setSelectedDetailIds(prev => {
      const next = new Set(prev);
      if (allSelected) {
        group.detailIds.forEach(id => next.delete(id));
      } else {
        group.detailIds.forEach(id => next.add(id));
      }
      return next;
    });
  }, [getGroupPrintEligibility, message, selectedDetailIds]);

  const selectedCount = selectedGroups.length;

  const openTemplateFormatModal = () => {
    setPreviewMode("template");
    setPreviewOpen(true);
  };

  const printSelectedFormat = async () => {
    if (activePrintItems.length === 0) {
      message.warning(`请先选择要打印${printTargetTitle}的款号`);
      return;
    }
    applyPrintPageSize(selectedPrintFormat);

    // 记录打印状态（按订单分组）
    const orderGroups = new Map<number, number[]>();
    activePrintItems.forEach(item => {
      const oid = item.orderId;
      if (!orderGroups.has(oid)) orderGroups.set(oid, []);
      orderGroups.get(oid)!.push(item.id);
    });

    try {
      await Promise.all(
        Array.from(orderGroups.entries()).map(([oid, ids]) => batchRecordPrintProcess(oid, ids, processType)),
      );
      await Promise.all(
        Array.from(orderGroups.keys()).map(async (oid) => {
          const details = await fetchOrderDetails(oid);
          setDetailsMap(prev => new Map(prev).set(oid, details));
        }),
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : "打印状态记录失败");
      return;
    }

    window.setTimeout(() => {
      window.print();
      void loadOrders();
    }, 0);
  };

  const previewSelectedFormat = (format: PrintFormatKey = selectedPrintFormat) => {
    setSelectedPrintFormat(format);
    setPreviewMode("print");
    setPreviewOpen(true);
  };

  const closePreviewModal = () => {
    setPreviewOpen(false);
  };

  useEffect(() => {
    if (previewOpen) setPreviewPage(1);
  }, [previewOpen, selectedPrintFormat]);

  const selectedPrintFormatLabel = printFormatTemplates[selectedPrintFormat].label;
  const selectedPrintFormatIsInnerBox = selectedPrintFormat === "inner-box-a4";
  const previewPrintItems = previewMode === "template" ? templatePrintItems : activePrintItems;
  const selectedPrintFormatIsInnerBoxTemplate = selectedPrintFormatIsInnerBox && previewPrintItems.length === 0;
  const selectedPairCount = printableSelectedGroups.reduce((total, group) => total + Number(group.totalQuantity || 0), 0);
  const selectedCartonCount = printableSelectedGroups.reduce((total, group) => total + Number(group.totalCartonCount || 0), 0);
  const printableLabelCount = activePrintItems.length === 0
    ? 0
    : selectedPrintFormatIsInnerBox
      ? buildInnerBoxLabels(activePrintItems).length
      : isCartonPrintFormat(selectedPrintFormat)
        ? buildCartonLabelData(selectedPrintFormat, activePrintItems).length
        : 0;

  const previewPageCount = useMemo(() => {
    if (selectedPrintFormatIsInnerBox) return getInnerBoxPageCount(previewPrintItems);
    if (isCartonPrintFormat(selectedPrintFormat)) return getCartonPageCount(selectedPrintFormat, previewPrintItems);
    return 1;
  }, [previewPrintItems, selectedPrintFormat, selectedPrintFormatIsInnerBox]);
  const activePageCount = useMemo(() => {
    if (activePrintItems.length === 0) {
      return 0;
    }
    if (selectedPrintFormatIsInnerBox) return getInnerBoxPageCount(activePrintItems);
    if (isCartonPrintFormat(selectedPrintFormat)) return getCartonPageCount(selectedPrintFormat, activePrintItems);
    return 0;
  }, [activePrintItems, selectedPrintFormat, selectedPrintFormatIsInnerBox]);

  useEffect(() => {
    setPreviewPage(p => Math.min(Math.max(p, 1), previewPageCount));
  }, [previewPageCount]);

  const renderEligibilityTag = useCallback((record: PrintStyleGroup) => {
    const eligibility = getGroupPrintEligibility(record);
    if (eligibility.status === "ready") {
      return <Tag color="success" icon={<CheckCircleOutlined />}>可打印</Tag>;
    }
    if (eligibility.status === "checking") {
      return <Tag color="processing" icon={<ClockCircleOutlined />}>检查中</Tag>;
    }
    return <Tag color="warning" icon={<WarningOutlined />}>{eligibility.reason}</Tag>;
  }, [getGroupPrintEligibility]);

  const styleColumns: ColumnsType<PrintStyleGroup> = [
    {
      title: "开发编号",
      dataIndex: "developmentNo",
      width: 180,
      render: (value: string) => <Typography.Text strong>{value}</Typography.Text>,
    },
    { title: "双数", dataIndex: "totalQuantity", width: 80, align: "right" },
    { title: "箱数", dataIndex: "totalCartonCount", width: 80, align: "right" },
    {
      title: "箱号范围",
      dataIndex: "cartonRange",
      width: 130,
      render: (value: string) => value || "-",
    },
    {
      title: "打印条件",
      width: 120,
      render: (_: unknown, record) => renderEligibilityTag(record),
    },
    {
      title: "外箱贴标",
      width: 150,
      align: "center",
      render: (_: unknown, record) => (
        record.hasOuterCarton
          ? <Typography.Text type="success">{record.outerPrintedAt || "已打"}</Typography.Text>
          : <Typography.Text type="secondary">-</Typography.Text>
      ),
    },
    {
      title: "内盒贴标",
      width: 150,
      align: "center",
      render: (_: unknown, record) => (
        record.hasInnerBox
          ? <Typography.Text type="success">{record.innerPrintedAt || "已打"}</Typography.Text>
          : <Typography.Text type="secondary">-</Typography.Text>
      ),
    },
  ];

  const detailsLoading = selectedOrderId !== undefined && !detailsMap.has(selectedOrderId);

  const detailRowSelection = {
    selectedRowKeys: currentStyleGroups.filter(g => isGroupSelected(g.detailIds)).map(g => g.id),
    onSelect: (record: PrintStyleGroup) => { void toggleGroup(record); },
    onSelectAll: (selected: boolean, _selectedRows: PrintStyleGroup[], changeRows: PrintStyleGroup[]) => {
      const selectableRows = selected
        ? changeRows.filter(group => !getGroupPrintEligibility(group).disabled)
        : changeRows;
      if (selected && selectableRows.length < changeRows.length) {
        message.warning(`${changeRows.length - selectableRows.length} 个款缺资料，已跳过`);
      }
      const allIds = selectableRows.flatMap(g => g.detailIds);
      if (selected && allIds.length === 0) {
        return;
      }

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
    getCheckboxProps: (record: PrintStyleGroup) => ({
      disabled: getGroupPrintEligibility(record).disabled,
      title: getGroupPrintEligibility(record).reason,
    }),
  };

  const clearSelection = () => {
    setSelectedDetailIds(new Set());
  };

  return (
    <div className="workspace print-workbench">
      <div className="print-workbench-header">
        <div>
          <Typography.Title level={3}>{title}</Typography.Title>
          <div className="print-workbench-meta">
            <Tag color={printFormatTarget === "outer-carton" ? "blue" : "green"}>
              {printTargetTitle}
            </Tag>
            <span>{orders.length} 个订单</span>
          </div>
        </div>
        <Space wrap>
          <Button icon={<EyeOutlined />} onClick={openTemplateFormatModal}>
            查看模板
          </Button>
        </Space>
      </div>

      <div className="print-workbench-layout">
        <section className="print-order-panel">
          <div className="print-order-picker">
            <div className="print-order-section-heading">
              <Typography.Text strong>订单流水号</Typography.Text>
              <Space size={12} wrap>
                <Checkbox
                  checked={hideFinishedOrders}
                  onChange={(event) => setHideFinishedOrders(event.target.checked)}
                >
                  只显示未全部打印
                </Checkbox>
                {selectedOrder ? (
                  <Typography.Text type="secondary">{selectedOrder.orderNo || selectedOrder.id}</Typography.Text>
                ) : null}
              </Space>
            </div>
            <Select
              showSearch
              loading={orderLoading}
              className="print-order-select"
              placeholder="选择订单流水号"
              value={selectedOrderId}
              optionFilterProp="label"
              onChange={(orderId) => selectOrder(Number(orderId))}
              options={orders.map((order) => ({
                value: order.id,
                label: `${order.orderNo || order.id} ${order.customerName || ""}`.trim(),
                order,
              }))}
              optionRender={(option) => {
                const order = option.data.order as OrderRecord;
                return (
                  <div className="print-order-option">
                    <span>{order.orderNo || order.id}</span>
                    <span>{order.customerName || "-"}</span>
                    <span>{order.totalQuantity || 0} 双 / {order.totalCartonCount || 0} 箱</span>
                    <span>{formatDateTimeText(order.createdAt)}</span>
                  </div>
                );
              }}
            />
          </div>

          <div className="print-detail-picker">
            <div className="print-order-section-heading">
              <Typography.Text strong>订单明细</Typography.Text>
              <Typography.Text type="secondary">{currentStyleGroups.length} 行</Typography.Text>
            </div>
            <Table
              rowKey="id"
              dataSource={currentStyleGroups}
              columns={styleColumns}
              pagination={false}
              size="small"
              loading={detailsLoading}
              rowSelection={detailRowSelection}
              locale={{ emptyText: selectedOrderId === undefined ? "先选择订单流水号" : "该订单没有明细数据" }}
            />
          </div>
        </section>

        <aside className="print-side-panel">
          <section className="print-side-section">
            <div className="print-side-heading">
              <Typography.Text strong>待打印</Typography.Text>
              <Typography.Text type="secondary">{printableSelectedGroups.length} 款</Typography.Text>
            </div>
            <div className="print-summary-grid">
              <div>
                <span>已选</span>
                <strong>{selectedCount}</strong>
              </div>
              <div>
                <span>双数</span>
                <strong>{selectedPairCount}</strong>
              </div>
              <div>
                <span>{printFormatTarget === "outer-carton" ? "箱数" : "标签"}</span>
                <strong>{printFormatTarget === "outer-carton" ? selectedCartonCount : printableLabelCount}</strong>
              </div>
              <div>
                <span>页数</span>
                <strong>{activePageCount}</strong>
              </div>
            </div>
          </section>

          <section className="print-side-section">
            <div className="print-side-heading">
              <Typography.Text strong>格式</Typography.Text>
            </div>
            <Radio.Group
              className="print-side-format"
              value={selectedPrintFormat}
              onChange={(event) => setSelectedPrintFormat(event.target.value)}
            >
              {printFormatOptions.filter(o => o.target === printFormatTarget).map((option) => (
                <Radio.Button value={option.value} key={option.value}>
                  {option.label.replace(`${printTargetTitle}-`, "")}
                </Radio.Button>
              ))}
            </Radio.Group>
          </section>

          <section className="print-side-section">
            <div className="print-side-heading">
              <Typography.Text strong>清单</Typography.Text>
              {selectedCount > 0 ? (
                <Button size="small" icon={<ClearOutlined />} onClick={clearSelection}>
                  清空
                </Button>
              ) : null}
            </div>
            <div className="print-queue-list">
              {printableSelectedGroups.length === 0 ? (
                <div className="print-queue-empty">暂无待打印</div>
              ) : (
                printableSelectedGroups.slice(0, 8).map((group) => {
                  const firstDetail = group.details[0];
                  return (
                  <div className="print-queue-item" key={group.id}>
                    <div>
                      <Typography.Text strong>{group.developmentNo || firstDetail.customerStyleNo || firstDetail.id}</Typography.Text>
                      <Typography.Text type="secondary">{firstDetail.englishColor || firstDetail.customerStyleNo || "-"}</Typography.Text>
                    </div>
                    <span>{printFormatTarget === "outer-carton" ? `${group.totalCartonCount || 0} 箱` : `${group.totalQuantity || 0} 双`}</span>
                  </div>
                  );
                })
              )}
              {printableSelectedGroups.length > 8 ? (
                <Typography.Text type="secondary">还有 {printableSelectedGroups.length - 8} 款</Typography.Text>
              ) : null}
            </div>
          </section>

          <section className="print-side-section print-side-preview-section">
            <div className="print-side-heading">
              <Typography.Text strong>预览</Typography.Text>
              <Button size="small" icon={<EyeOutlined />} onClick={() => previewSelectedFormat()}>
                放大
              </Button>
            </div>
            <div className={`print-side-preview-canvas ${
              selectedPrintFormatIsInnerBox ? "print-side-preview-inner" : "print-side-preview-carton"
            }`}>
              <div className={
                selectedPrintFormatIsInnerBox ? "print-side-preview-inner-scale" : "print-side-preview-carton-scale"
              }>
                <PrintFormatSheet
                  format={selectedPrintFormat}
                  printItems={activePrintItems}
                  cartonPageIndex={isCartonPrintFormat(selectedPrintFormat) ? 0 : undefined}
                  innerBoxPageIndex={selectedPrintFormatIsInnerBox ? 0 : undefined}
                />
              </div>
            </div>
          </section>

          <div className="print-side-actions">
            <Button icon={<EyeOutlined />} onClick={() => previewSelectedFormat()}>
              预览
            </Button>
            <Button type="primary" icon={<PrinterOutlined />} disabled={activePrintItems.length === 0} onClick={printSelectedFormat}>
              打印
            </Button>
          </div>
        </aside>
      </div>

      <Modal
        open={previewOpen}
        title={`${selectedPrintFormatLabel}${previewMode === "template" ? "模板预览" : "预览"}`}
        onCancel={closePreviewModal}
        width={selectedPrintFormatIsInnerBoxTemplate ? 560 : selectedPrintFormatIsInnerBox ? 860 : 1120}
        footer={null}
        destroyOnHidden
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
          <PrintFormatSheet format={selectedPrintFormat} printItems={activePrintItems} />
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
      <div className="inner-box-label-cell inner-box-label-header">STYLE NAME</div>
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
