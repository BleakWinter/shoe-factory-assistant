import { Input } from "antd";
import { joinBoxSpec, splitBoxSpec } from "../utils/boxSpec";
import type { BoxDimensions } from "../utils/boxSpec";

interface Props {
  id?: string;
  value?: string;
  onChange?: (value: string) => void;
  disabled?: boolean;
}

export default function BoxSpecInput({ id, value, onChange, disabled }: Props) {
  const dimensions = splitBoxSpec(value);
  return <div className="box-spec-input">
    {(["长", "宽", "高"] as const).map((label, index) => <div className="box-spec-dimension" key={label}>
      {index > 0 && <span className="box-spec-separator" aria-hidden="true">×</span>}
      <Input id={id ? `${id}-${index}` : undefined} aria-label={`内盒规格${label}`} placeholder={label}
        inputMode="decimal" value={dimensions[index]} disabled={disabled} allowClear maxLength={30}
        onChange={(event) => {
          const next: BoxDimensions = [...dimensions];
          next[index] = event.target.value;
          onChange?.(joinBoxSpec(next));
        }} />
    </div>)}
  </div>;
}
