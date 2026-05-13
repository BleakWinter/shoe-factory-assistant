export function formatFileSize(size?: number) {
  // 给后续文件列表预留的大小格式化工具。
  if (!size) {
    return "-";
  }

  if (size < 1024) {
    return `${size} B`;
  }

  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }

  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

export function formatDateTime(value?: string) {
  // 后端返回 ISO 时间字符串，页面统一显示成中文本地时间。
  if (!value) {
    return "-";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function formatEmpty(value?: string | number | null) {
  // 表格里空值统一显示 -，避免一片 undefined/null。
  if (value === null || value === undefined || value === "") {
    return "-";
  }
  return value;
}
