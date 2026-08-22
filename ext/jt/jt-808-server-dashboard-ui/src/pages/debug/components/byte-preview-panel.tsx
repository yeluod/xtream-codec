import { Chip } from "@heroui/react";
import clsx from "clsx";

import {
  chunkBytes,
  formatAscii,
  formatBcd,
  formatInteger,
  formatText,
  formatUtf8,
  toByteValues,
} from "../debug-utils";

const BytePreviewCell = ({
  label,
  value,
}: {
  label: string;
  value: string;
}) => (
  <div className="flex items-start justify-between gap-3 rounded-md border border-border/60 bg-background px-2.5 py-1.5">
    <div className="shrink-0 text-[11px] font-medium text-muted">{label}</div>
    <div className="min-w-0 break-all text-right font-mono text-xs text-foreground">
      {value}
    </div>
  </div>
);

const ByteBitsPanel = ({
  range,
  values,
}: {
  range: string;
  values: number[];
}) => {
  const totalBits = values.length * 8;

  return (
    <aside className="rounded-xl border border-border/60 bg-background-secondary/45 p-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-sm font-semibold">Bit 预览</div>
          <div className="mt-0.5 font-mono text-xs text-muted">{range}</div>
        </div>
        {values.length > 0 && values.length <= 4 ? (
          <Chip color="default" size="sm" variant="tertiary">
            BE {totalBits - 1}..0
          </Chip>
        ) : null}
      </div>
      {values.length === 0 || values.length > 4 ? (
        <div className="mt-3 rounded-lg border border-dashed border-border/70 bg-background px-2.5 py-2 text-xs text-muted">
          选择 1-4 字节查看 bit 位图
        </div>
      ) : (
        <div className="mt-3 space-y-2 rounded-lg border border-border/60 bg-background px-2.5 py-2">
          {values.map((value, byteIndex) => {
            const bits = [7, 6, 5, 4, 3, 2, 1, 0].map((bitInByte) => {
              const bit = (value >> bitInByte) & 1;
              const bitOffset = byteIndex * 8 + (7 - bitInByte);
              const globalBitIndex = totalBits - bitOffset - 1;

              return { bit, globalBitIndex };
            });
            const bitGroups = chunkBytes(bits, 4);

            return (
              <div
                key={`${byteIndex}-${value}`}
                className="grid grid-cols-[34px_minmax(0,1fr)] items-start gap-2"
              >
                <div className="pt-1 font-mono text-[11px] text-muted">
                  +{byteIndex}
                </div>
                <div className="flex max-w-[220px] gap-2">
                  {bitGroups.map((group) => (
                    <div
                      key={group[0]?.globalBitIndex}
                      className="min-w-0 flex-1"
                    >
                      <div className="grid grid-cols-4 gap-1">
                        {group.map(({ bit, globalBitIndex }) => (
                          <span
                            key={globalBitIndex}
                            className={clsx(
                              "grid aspect-square place-items-center rounded-md border font-mono text-[11px]",
                              bit
                                ? "border-accent/25 bg-accent/15 text-foreground"
                                : "border-border/60 bg-background-secondary/70 text-muted",
                            )}
                            title={`bit ${globalBitIndex} = ${bit}`}
                          >
                            {bit}
                          </span>
                        ))}
                      </div>
                      <div className="mt-0.5 grid grid-cols-4 gap-1 text-center font-mono text-[9px] text-muted">
                        {group.map(({ globalBitIndex }) => (
                          <span key={globalBitIndex}>{globalBitIndex}</span>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </aside>
  );
};

export const BytePreviewPanel = ({
  bytes,
  range,
}: {
  bytes: Array<{ index: number; byte: string }>;
  range: string;
}) => {
  const values = toByteValues(bytes);
  const hex = bytes.map(({ byte }) => byte).join(" ");
  const binary = values
    .map((value) => value.toString(2).padStart(8, "0"))
    .join(" ");
  const valuePreviews = [
    ["HEX", hex || "-"],
    ["BIN", binary || "-"],
    ["UInt BE", formatInteger(values, "be", false)],
    ["UInt LE", formatInteger(values, "le", false)],
    ["Int BE", formatInteger(values, "be", true)],
    ["Int LE", formatInteger(values, "le", true)],
  ] as const;
  const encodingPreviews = [
    ["ASCII", formatAscii(values)],
    ["UTF-8", formatUtf8(values)],
    ["GBK", formatText(values, "gbk")],
    ["GB2312", formatText(values, "gb2312")],
    ["BCD", formatBcd(values)],
  ] as const;

  return (
    <div className="min-w-0 columns-[260px] gap-3 [column-fill:balance]">
      <div className="mb-3 break-inside-avoid">
        <ByteBitsPanel range={range} values={values} />
      </div>
      <aside className="mb-3 break-inside-avoid rounded-xl border border-border/60 bg-background-secondary/45 p-3">
        <div>
          <div className="text-sm font-semibold">编码预览</div>
          <div className="mt-0.5 font-mono text-xs text-muted">{range}</div>
        </div>
        <div className="mt-3 space-y-1.5">
          {encodingPreviews.map(([label, value]) => (
            <BytePreviewCell key={label} label={label} value={value} />
          ))}
        </div>
      </aside>
      <aside className="mb-3 break-inside-avoid rounded-xl border border-border/60 bg-background-secondary/45 p-3">
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="text-sm font-semibold">数值预览</div>
            <div className="mt-0.5 font-mono text-xs text-muted">{range}</div>
          </div>
          <Chip color="default" size="sm" variant="tertiary">
            {values.length} B
          </Chip>
        </div>
        <div className="mt-3 space-y-1.5">
          {valuePreviews.map(([label, value]) => (
            <BytePreviewCell key={label} label={label} value={value} />
          ))}
        </div>
      </aside>
    </div>
  );
};
