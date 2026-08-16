import { Chip } from "@heroui/react";
import clsx from "clsx";

import {
  chipColor,
  getNodeFieldDescription,
  getNodeLabel,
  kindTone,
  statusTone,
  tonePanelClass,
  Tone,
} from "../debug-utils";

import { CodecTraceDiagnostic, CodecTraceNode, CodecTraceView } from "@/types";

const TraceDetailCell = ({
  label,
  value,
  tone = "default",
}: {
  label: string;
  value: string | number;
  tone?: Tone;
}) => (
  <div
    className={clsx(
      "rounded-xl border px-3 py-2 shadow-[inset_0_1px_0_rgb(255_255_255/0.035)]",
      tonePanelClass(tone),
    )}
  >
    <div className="text-[11px] font-medium text-muted">{label}</div>
    <div className="mt-1 wrap-break-word font-mono text-xs text-foreground">
      {value}
    </div>
  </div>
);

export const TraceInspector = ({
  trace,
  selectedNode,
}: {
  trace: CodecTraceView;
  selectedNode?: CodecTraceNode;
}) => {
  const diagnostics = selectedNode?.diagnostics ?? [];
  const diagnosticSource: CodecTraceDiagnostic[] = diagnostics.length
    ? diagnostics
    : trace.diagnostics;
  const selectedRange =
    selectedNode?.byteStart != null && selectedNode?.byteEnd != null
      ? `${selectedNode.byteStart}-${selectedNode.byteEnd}`
      : "-";
  const byteSize =
    selectedNode?.byteStart != null && selectedNode?.byteEnd != null
      ? selectedNode.byteEnd - selectedNode.byteStart
      : "-";
  const attributeCount = Object.keys(selectedNode?.attributes ?? {}).length;
  const fieldDescription = getNodeFieldDescription(selectedNode);
  const hasDiagnostics = diagnosticSource.length > 0;

  return (
    <div className="space-y-4">
      <section className="rounded-2xl border border-border/60 bg-background-secondary/50 p-4 shadow-[inset_0_1px_0_rgb(255_255_255/0.035)]">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="text-sm font-semibold">字段详情</div>
            <div className="mt-1 truncate font-mono text-xs text-muted">
              {selectedNode
                ? getNodeLabel(selectedNode)
                : "点击左侧字段查看详情"}
            </div>
            {fieldDescription ? (
              <div className="mt-1 truncate text-xs text-muted">
                {fieldDescription}
              </div>
            ) : null}
          </div>
          <Chip
            color={chipColor(kindTone(selectedNode?.kind))}
            size="sm"
            variant="soft"
          >
            {selectedNode?.kind ?? "EMPTY"}
          </Chip>
        </div>

        {selectedNode ? (
          <div className="mt-4 grid gap-2 sm:grid-cols-2">
            <TraceDetailCell label="字段说明" value={fieldDescription ?? "-"} />
            <TraceDetailCell label="范围" value={selectedRange} />
            <TraceDetailCell label="字节数" value={byteSize} />
            <TraceDetailCell
              label="值摘要"
              value={selectedNode.valueSummary ?? "-"}
            />
            <TraceDetailCell
              label="Codec"
              value={selectedNode.codecType ?? "-"}
            />
            <TraceDetailCell
              label="Java 类型"
              value={selectedNode.javaType ?? "-"}
            />
            <TraceDetailCell
              label="状态"
              tone={statusTone(selectedNode.status)}
              value={selectedNode.status}
            />
            <TraceDetailCell label="属性数" value={attributeCount} />
            <TraceDetailCell
              label="字节偏移"
              value={selectedNode.byteStart ?? "-"}
            />
          </div>
        ) : (
          <div className="mt-4 rounded-xl border border-dashed border-border/70 bg-background px-3 py-4 text-sm text-muted">
            先在左侧选择一个节点，再查看详细字段信息。
          </div>
        )}
      </section>

      {hasDiagnostics ? (
        <section className="rounded-2xl border border-border/60 bg-background-secondary/50 p-4">
          <div className="flex items-center justify-between gap-2">
            <div className="text-sm font-semibold">诊断</div>
            <Chip color="danger" size="sm" variant="soft">
              {diagnosticSource.length}
            </Chip>
          </div>
          <div className="mt-3 space-y-2">
            {diagnosticSource.map((item, index) => (
              <div
                key={`${item.level}-${item.message}-${index}`}
                className="rounded-xl border border-rose-500/30 bg-rose-500/6 px-3 py-2"
              >
                <div className="flex items-center justify-between gap-2">
                  <Chip color="danger" size="sm" variant="soft">
                    {item.level}
                  </Chip>
                  {item.byteOffset != null ? (
                    <span className="font-mono text-[11px] text-rose-200/80">
                      {item.byteOffset}
                    </span>
                  ) : null}
                </div>
                <div className="mt-1 text-xs text-foreground">
                  {item.message}
                </div>
              </div>
            ))}
          </div>
        </section>
      ) : null}
    </div>
  );
};
