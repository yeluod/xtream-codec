import { Label, NumberField } from "@heroui/react";
import clsx from "clsx";

import { formControlClass } from "../debug-utils";

export const DebugNumberField = ({
  label,
  maxValue,
  minValue,
  isDisabled = false,
  onChange,
  value,
}: {
  label: string;
  maxValue?: number;
  minValue?: number;
  isDisabled?: boolean;
  onChange: (value: number) => void;
  value: number;
}) => (
  <NumberField
    className="min-w-0"
    isDisabled={isDisabled}
    maxValue={maxValue}
    minValue={minValue}
    value={value}
    onChange={onChange}
  >
    <Label className="text-muted">{label}</Label>
    <NumberField.Group
      className={clsx(
        formControlClass,
        "grid grid-cols-[28px_minmax(0,1fr)_28px] items-center gap-1 px-1",
      )}
    >
      <NumberField.DecrementButton className="grid size-7 place-items-center rounded-lg text-muted transition-colors hover:bg-background-tertiary hover:text-foreground disabled:cursor-not-allowed disabled:opacity-35">
        -
      </NumberField.DecrementButton>
      <NumberField.Input className="h-full min-w-0 bg-transparent px-1 text-center font-mono text-sm text-foreground outline-none" />
      <NumberField.IncrementButton className="grid size-7 place-items-center rounded-lg text-muted transition-colors hover:bg-background-tertiary hover:text-foreground disabled:cursor-not-allowed disabled:opacity-35">
        +
      </NumberField.IncrementButton>
    </NumberField.Group>
  </NumberField>
);
