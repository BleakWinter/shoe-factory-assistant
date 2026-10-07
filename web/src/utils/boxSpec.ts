export type BoxDimensions = [string, string, string];

export function splitBoxSpec(value?: string): BoxDimensions {
  const parts = (value || "").trim().split(/\s*[xX×*＊]\s*/);
  return [parts[0] || "", parts[1] || "", parts.slice(2).join("×")];
}

export function joinBoxSpec(dimensions: BoxDimensions): string {
  const values = dimensions.map((value) => value.trim());
  return values.every((value) => !value) ? "" : values.join("×");
}

export function isCompleteBoxSpec(value?: string): boolean {
  const parts = (value || "").trim().split(/\s*[xX×*＊]\s*/);
  return parts.length === 3 && parts.every((part) => Boolean(part.trim()));
}
