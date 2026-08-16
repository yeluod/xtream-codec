import { Button, Chip, Tooltip } from "@heroui/react";
import clsx from "clsx";
import { useEffect, useMemo, useRef, useState } from "react";
import type { PointerEvent as ReactPointerEvent } from "react";

import {
  chunkBytes,
  getNodeLabel,
  splitHexBytes,
  Tone,
  tonePanelClass,
} from "../debug-utils";

import { BytePreviewPanel } from "./byte-preview-panel";
import { RawResultPanel } from "./raw-result-panel";
import { TraceInspector } from "./trace-inspector";
import { TraceNodeView } from "./trace-node-view";

import { LuCloneIcon } from "@/components/icons.tsx";
import { CodecTraceNode, CodecTraceView } from "@/types";

const TRACE_FULLSCREEN_SPLIT_STORAGE_KEY = "xtream.debug.traceFullscreenSplit";

const clamp = (value: number, min: number, max: number) => {
  return Math.min(Math.max(value, min), max);
};

const readTraceFullscreenSplit = () => {
  try {
    const value = Number(
      window.localStorage.getItem(TRACE_FULLSCREEN_SPLIT_STORAGE_KEY),
    );

    return Number.isFinite(value) ? clamp(value, 25, 75) : 64;
  } catch {
    return 64;
  }
};

const TraceStat = ({
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
      "flex min-w-0 items-baseline gap-2 rounded-lg border px-2.5 py-1.5 shadow-[inset_0_1px_0_rgb(255_255_255/0.04)]",
      tonePanelClass(tone),
    )}
  >
    <span className="shrink-0 text-[11px] font-medium text-muted">{label}</span>
    <span className="truncate font-mono text-xs font-semibold text-foreground">
      {value}
    </span>
  </div>
);

export const TracePanel = ({
  trace,
  result,
  label,
  showRawResult = true,
  traceTitle,
}: {
  trace?: CodecTraceView;
  result?: unknown;
  label?: string;
  showRawResult?: boolean;
  traceTitle?: string;
}) => {
  const [selectedNodeId, setSelectedNodeId] = useState<string>();
  const [selectedByteOffset, setSelectedByteOffset] = useState<number>();
  const [autoScrollTraceTree, setAutoScrollTraceTree] = useState(true);
  const [collapsedNodeIds, setCollapsedNodeIds] = useState<Set<string>>(
    () => new Set(),
  );
  const [copyState, setCopyState] = useState<"idle" | "copied" | "failed">(
    "idle",
  );
  const [isTraceFullscreen, setIsTraceFullscreen] = useState(false);
  const [fullscreenSplit, setFullscreenSplit] = useState(
    readTraceFullscreenSplit,
  );
  const [traceScrollTarget, setTraceScrollTarget] = useState<{
    nodeId: string;
    serial: number;
  }>();
  const traceTreeRef = useRef<HTMLDivElement>(null);
  const fullscreenLayoutRef = useRef<HTMLDivElement>(null);
  const copyResetTimerRef = useRef<number | undefined>(undefined);
  const nodes = trace?.nodes ?? [];
  const nodeMap = useMemo(() => {
    return new Map(nodes.map((node) => [node.id, node]));
  }, [nodes]);
  const payloadBytes = useMemo(
    () => splitHexBytes(trace?.payloadHex),
    [trace?.payloadHex],
  );
  const payloadRows = useMemo(() => chunkBytes(payloadBytes), [payloadBytes]);

  useEffect(() => {
    setSelectedNodeId(undefined);
    setSelectedByteOffset(undefined);
    setCollapsedNodeIds(new Set());
    setTraceScrollTarget(undefined);
    setCopyState("idle");
  }, [trace?.root?.id]);

  useEffect(() => {
    return () => {
      if (copyResetTimerRef.current != null) {
        window.clearTimeout(copyResetTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    try {
      window.localStorage.setItem(
        TRACE_FULLSCREEN_SPLIT_STORAGE_KEY,
        String(fullscreenSplit),
      );
    } catch {
      // localStorage 不可用时忽略，拖拽仍然在当前页面有效。
    }
  }, [fullscreenSplit]);

  useEffect(() => {
    if (!isTraceFullscreen) {
      return;
    }

    const previousOverflow = document.body.style.overflow;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setIsTraceFullscreen(false);
      }
    };

    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isTraceFullscreen]);

  useEffect(() => {
    if (!traceScrollTarget) {
      return;
    }

    const frame = window.requestAnimationFrame(() => {
      const container = traceTreeRef.current;

      if (!container) {
        return;
      }
      const element = Array.from(
        container.querySelectorAll<HTMLElement>("[data-trace-node-id]"),
      ).find((item) => item.dataset.traceNodeId === traceScrollTarget.nodeId);

      if (!element) {
        return;
      }

      const containerRect = container.getBoundingClientRect();
      const elementRect = element.getBoundingClientRect();
      const targetTop =
        container.scrollTop +
        elementRect.top -
        containerRect.top -
        (container.clientHeight - elementRect.height) / 2;

      container.scrollTo({
        top: Math.max(targetTop, 0),
        behavior: "smooth",
      });
    });

    return () => window.cancelAnimationFrame(frame);
  }, [traceScrollTarget]);

  const selectedNode = selectedNodeId ? nodeMap.get(selectedNodeId) : undefined;
  const selectedRangeStart = selectedNode?.byteStart;
  const selectedRangeEnd = selectedNode?.byteEnd;
  const selectedLabel = selectedNode ? getNodeLabel(selectedNode) : "无";
  const selectedRange =
    selectedNode?.byteStart != null && selectedNode?.byteEnd != null
      ? `${selectedNode.byteStart}-${selectedNode.byteEnd}`
      : selectedByteOffset != null
        ? `byte ${selectedByteOffset}`
        : "-";
  const selectedBytes = useMemo(() => {
    if (selectedRangeStart != null && selectedRangeEnd != null) {
      return payloadBytes.slice(selectedRangeStart, selectedRangeEnd);
    }
    if (selectedByteOffset != null) {
      return payloadBytes.slice(selectedByteOffset, selectedByteOffset + 1);
    }

    return [];
  }, [payloadBytes, selectedByteOffset, selectedRangeEnd, selectedRangeStart]);

  const selectNode = (node: CodecTraceNode) => {
    setSelectedNodeId(node.id);
    setSelectedByteOffset(node.byteStart ?? undefined);
  };

  const toggleNode = (node: CodecTraceNode) => {
    setCollapsedNodeIds((previous) => {
      const next = new Set(previous);

      if (next.has(node.id)) {
        next.delete(node.id);
      } else {
        next.add(node.id);
      }

      return next;
    });
  };

  const expandTraceAncestors = (nodeId: string) => {
    const ancestorIds: string[] = [];
    let current = nodeMap.get(nodeId);

    while (current?.parentId) {
      ancestorIds.push(current.parentId);
      current = nodeMap.get(current.parentId);
    }

    if (ancestorIds.length === 0) {
      return;
    }

    setCollapsedNodeIds((previous) => {
      const next = new Set(previous);

      ancestorIds.forEach((ancestorId) => next.delete(ancestorId));

      return next;
    });
  };

  const selectByte = (byteOffset: number) => {
    setSelectedByteOffset(byteOffset);
    const nodeId = trace?.nodeIdsByByteOffset[String(byteOffset)]?.at(-1);

    if (nodeId) {
      setSelectedNodeId(nodeId);
      if (autoScrollTraceTree) {
        expandTraceAncestors(nodeId);
        setTraceScrollTarget((previous) => ({
          nodeId,
          serial: (previous?.serial ?? 0) + 1,
        }));
      }
    }
  };

  const writeTextToClipboard = async (value: string) => {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value);

      return;
    }

    const textArea = document.createElement("textarea");

    textArea.value = value;
    textArea.style.position = "fixed";
    textArea.style.inset = "0";
    textArea.style.opacity = "0";
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    document.execCommand("copy");
    document.body.removeChild(textArea);
  };

  const copyPayloadHex = async () => {
    const payloadHex = trace?.payloadHex?.trim();

    if (!payloadHex) {
      return;
    }

    try {
      await writeTextToClipboard(`7e${payloadHex}7e`);
      setCopyState("copied");
    } catch {
      setCopyState("failed");
    }

    if (copyResetTimerRef.current != null) {
      window.clearTimeout(copyResetTimerRef.current);
    }
    copyResetTimerRef.current = window.setTimeout(() => {
      setCopyState("idle");
    }, 1400);
  };

  const updateFullscreenSplit = (clientX: number) => {
    const container = fullscreenLayoutRef.current;

    if (!container) {
      return;
    }

    const rect = container.getBoundingClientRect();
    const percent = ((clientX - rect.left) / rect.width) * 100;

    setFullscreenSplit(clamp(percent, 25, 75));
  };

  const startFullscreenResize = (
    event: ReactPointerEvent<HTMLButtonElement>,
  ) => {
    event.preventDefault();
    updateFullscreenSplit(event.clientX);

    const handlePointerMove = (moveEvent: PointerEvent) => {
      updateFullscreenSplit(moveEvent.clientX);
    };
    const handlePointerUp = () => {
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
    };

    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);
  };

  const renderMacWindowControls = (
    action: "maximize" | "close",
    label: string,
  ) => {
    const handleClick = () => {
      setIsTraceFullscreen(action === "maximize");
    };

    return (
      <div className="flex shrink-0 items-center gap-2">
        {[
          ["#ff5f57", "红色"],
          ["#ffbd2e", "黄色"],
          ["#28c840", "绿色"],
        ].map(([color, name]) => (
          <button
            key={color}
            aria-label={`${label}（${name}圆点）`}
            className="size-3.5 cursor-pointer rounded-full shadow-[inset_0_0_0_1px_rgb(0_0_0/0.16),inset_0_1px_0_rgb(255_255_255/0.35)] transition hover:brightness-110"
            style={{ backgroundColor: color }}
            type="button"
            onClick={handleClick}
          />
        ))}
      </div>
    );
  };

  const renderTraceTreeSection = (fullscreen = false) => {
    const currentTrace = trace;

    if (!currentTrace) {
      return null;
    }

    return (
      <section
        className={clsx(
          "overflow-hidden rounded-2xl border border-border/60 bg-background-secondary/45 shadow-[inset_0_1px_0_rgb(255_255_255/0.035)]",
          fullscreen ? "flex h-full min-h-0 flex-col rounded-xl" : "",
        )}
      >
        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-border/60 bg-background-secondary/60 px-4 py-3">
          <div className="flex min-w-0 items-start gap-3">
            <div className="min-w-0">
              <div className="flex items-center gap-2 text-sm font-semibold">
                <span className="h-1.5 w-1.5 rounded-full bg-sky-500/80" />
                {label ? `Trace Tree · ${label}` : "Trace Tree"}
              </div>
              <div className="mt-0.5 flex min-w-0 flex-wrap items-center gap-2 text-xs text-muted">
                <span>当前选中</span>
                <span className="max-w-90 truncate font-mono text-foreground">
                  {selectedLabel}
                </span>
                <Chip color="default" size="sm" variant="soft">
                  {selectedRange}
                </Chip>
              </div>
            </div>
          </div>
          <div
            className={clsx(
              "grid w-full gap-2 sm:w-auto",
              currentTrace.diagnostics.length > 0
                ? "sm:grid-cols-4"
                : "sm:grid-cols-3",
            )}
          >
            <TraceStat
              label="方向"
              tone="primary"
              value={currentTrace.direction}
            />
            <TraceStat label="节点" tone="secondary" value={nodes.length} />
            <TraceStat
              label="字节"
              tone="success"
              value={currentTrace.root?.byteEnd ?? "-"}
            />
            {currentTrace.diagnostics.length > 0 ? (
              <TraceStat
                label="诊断"
                tone="danger"
                value={currentTrace.diagnostics.length}
              />
            ) : null}
          </div>
        </div>
        <div
          ref={traceTreeRef}
          className={clsx(
            fullscreen
              ? "min-h-0 flex-1 overflow-auto"
              : "max-h-140 overflow-auto",
          )}
        >
          <div className="sticky top-0 z-10 grid min-w-190 grid-cols-[minmax(240px,1.2fr)_116px_92px_minmax(180px,1fr)] gap-3 border-b border-border/60 bg-background-secondary/95 px-3 py-2 text-[11px] font-medium text-muted backdrop-blur">
            <span>节点</span>
            <span>类型</span>
            <span>范围</span>
            <span>值 / Codec</span>
          </div>
          {currentTrace.root ? (
            <TraceNodeView
              collapsedNodeIds={collapsedNodeIds}
              node={currentTrace.root}
              selectedNodeId={selectedNodeId}
              onSelectNode={selectNode}
              onToggleNode={toggleNode}
            />
          ) : (
            <div className="px-3 py-4 text-sm text-muted">暂无节点数据</div>
          )}
        </div>
      </section>
    );
  };

  const renderPayloadSection = (fullscreen = false) => (
    <section
      className={clsx(
        "overflow-hidden rounded-2xl border border-border/60 bg-background-secondary/45 shadow-[inset_0_1px_0_rgb(255_255_255/0.035)]",
        fullscreen ? "flex min-h-0 flex-col rounded-xl" : "xl:col-span-2",
      )}
    >
      <div className="flex items-center justify-between gap-2 border-b border-border/60 bg-background-secondary/60 px-4 py-3">
        <div>
          <div className="flex items-center gap-2 text-sm font-semibold">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500/80" />
            Payload HEX
          </div>
          <div className="mt-0.5 text-xs text-muted">
            点击字节定位字段，点击字段反向高亮字节
          </div>
        </div>
        <div className="flex items-center gap-3">
          <Tooltip>
            <Tooltip.Trigger>
              <Button
                className="h-8 px-2.5 font-medium"
                isDisabled={payloadBytes.length === 0}
                size="sm"
                variant="secondary"
                onPress={copyPayloadHex}
              >
                <LuCloneIcon size={14} />
                {copyState === "copied"
                  ? "已复制"
                  : copyState === "failed"
                    ? "复制失败"
                    : "复制报文"}
              </Button>
            </Tooltip.Trigger>
            <Tooltip.Content>复制完整 Payload HEX</Tooltip.Content>
          </Tooltip>
          <button
            aria-checked={autoScrollTraceTree}
            className={clsx(
              "inline-flex h-7 cursor-pointer select-none items-center gap-2 rounded-lg px-1 text-xs font-medium transition-colors",
              autoScrollTraceTree
                ? "text-foreground"
                : "text-muted hover:text-foreground",
            )}
            role="switch"
            type="button"
            onClick={() => setAutoScrollTraceTree((enabled) => !enabled)}
          >
            <span>自动滚动</span>
            <span
              className={clsx(
                "relative h-4 w-8 rounded-full border transition-colors",
                autoScrollTraceTree
                  ? "border-accent/50 bg-accent/20 shadow-[0_0_0_1px_rgb(0_149_255/0.12)]"
                  : "border-border/70 bg-background-secondary",
              )}
            >
              <span
                className={clsx(
                  "absolute left-0.5 top-1/2 size-3 -translate-y-1/2 rounded-full transition-transform",
                  autoScrollTraceTree
                    ? "translate-x-4 bg-accent"
                    : "translate-x-0 bg-muted",
                )}
              />
            </span>
          </button>
          <Chip color="success" size="sm" variant="soft">
            每行 16 字节
          </Chip>
        </div>
      </div>
      <div
        className={clsx(
          "grid items-stretch gap-3 bg-background-tertiary/45 p-3",
          fullscreen
            ? "min-h-0 flex-1 overflow-auto xl:grid-cols-[minmax(0,1fr)_340px]"
            : "lg:grid-cols-[minmax(0,1fr)_340px]",
        )}
      >
        <div className="h-full min-h-0 overflow-auto">
          <div className="relative min-w-150 space-y-1.5 font-mono text-xs">
            {payloadRows.length > 0 ? (
              <>
                <div className="absolute bottom-0 left-9 top-0 w-px bg-border/70" />
                <div className="grid grid-cols-[40px_minmax(0,1fr)] border-b border-border/50 pb-1 text-[11px] text-muted">
                  <div className="pr-2">
                    <div className="size-7" />
                  </div>
                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 pl-2">
                    {chunkBytes(
                      Array.from({ length: 16 }, (_, index) => index),
                      4,
                    ).map((group) => (
                      <div key={group[0]} className="grid grid-cols-4 gap-1.5">
                        {group.map((index) => (
                          <span
                            key={index}
                            className="grid size-7 place-items-center rounded-md border border-border/40 bg-background/70 text-muted"
                          >
                            {index.toString(16).padStart(2, "0")}
                          </span>
                        ))}
                      </div>
                    ))}
                  </div>
                </div>
                {payloadRows.map((row) => {
                  const rowStart = row[0]?.index ?? 0;
                  const byteGroups = chunkBytes(row, 4);

                  return (
                    <div
                      key={rowStart}
                      className="grid grid-cols-[40px_minmax(0,1fr)]"
                    >
                      <div className="pr-2">
                        <div className="grid size-7 place-items-center rounded-md border border-border/50 bg-background text-center text-muted">
                          {rowStart.toString(16).padStart(2, "0")}
                        </div>
                      </div>
                      <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 pl-2">
                        {byteGroups.map((group) => (
                          <div
                            key={group[0]?.index}
                            className="grid grid-cols-4 gap-1.5"
                          >
                            {group.map(({ index, byte }) => {
                              const isInSelectedRange =
                                selectedRangeStart != null &&
                                selectedRangeEnd != null &&
                                index >= selectedRangeStart &&
                                index < selectedRangeEnd;
                              const shouldHighlightRange = isInSelectedRange;
                              const isSelectedByte =
                                selectedRangeStart == null &&
                                selectedRangeEnd == null &&
                                selectedByteOffset === index;

                              return (
                                <button
                                  key={`${index}-${byte}`}
                                  className={clsx(
                                    "grid size-7 place-items-center rounded-md text-center transition-colors",
                                    shouldHighlightRange || isSelectedByte
                                      ? "bg-accent text-accent-foreground"
                                      : "text-foreground hover:bg-background",
                                  )}
                                  title={`byte ${index}`}
                                  type="button"
                                  onClick={() => selectByte(index)}
                                >
                                  {byte}
                                </button>
                              );
                            })}
                          </div>
                        ))}
                      </div>
                    </div>
                  );
                })}
              </>
            ) : (
              <div className="rounded-xl border border-dashed border-border/70 bg-background px-3 py-4 text-sm text-muted">
                -
              </div>
            )}
          </div>
        </div>
        <div
          className={clsx(
            "min-w-0",
            fullscreen ? "w-full max-w-[360px] justify-self-end" : "",
          )}
        >
          <BytePreviewPanel bytes={selectedBytes} range={selectedRange} />
        </div>
      </div>
    </section>
  );

  if (!trace) {
    return (
      <section className="overflow-hidden rounded-2xl border border-border/70 bg-background/80 shadow-[0_10px_30px_-24px_rgb(0_0_0/0.6)]">
        <div className="grid gap-4 p-4 xl:grid-cols-[minmax(0,1.45fr)_340px]">
          <div className="min-h-140 rounded-2xl border border-dashed border-border/70 bg-background-secondary/40" />
          <div className="min-h-140 rounded-2xl border border-dashed border-border/70 bg-background-secondary/40" />
        </div>
      </section>
    );
  }

  return (
    <>
      <section className="overflow-hidden rounded-2xl border border-border/70 bg-background/80 shadow-[0_10px_30px_-24px_rgb(0_0_0/0.6)]">
        <div className="flex items-center justify-between gap-3 border-b border-border/60 bg-background-secondary/45 px-4 py-3">
          <div className="flex min-w-0 items-center gap-3">
            <Tooltip>
              <Tooltip.Trigger>
                {renderMacWindowControls("maximize", "最大化 Trace 视图")}
              </Tooltip.Trigger>
              <Tooltip.Content>最大化 Trace 视图</Tooltip.Content>
            </Tooltip>
            <div className="flex min-w-0 items-baseline gap-3">
              <div className="shrink-0 text-sm font-semibold">Trace 视图</div>
              <div className="truncate font-mono text-xs text-muted">
                {traceTitle}
              </div>
            </div>
          </div>
          <Chip color="default" size="sm" variant="soft">
            {selectedRange}
          </Chip>
        </div>

        <div className="grid gap-4 p-4 xl:grid-cols-[minmax(0,1.45fr)_340px]">
          <div className="min-w-0">{renderTraceTreeSection()}</div>

          <aside className="min-w-0">
            <TraceInspector selectedNode={selectedNode} trace={trace} />
          </aside>

          {renderPayloadSection()}
        </div>
        {showRawResult ? (
          <div className="px-4 pb-4">
            <RawResultPanel result={result} trace={trace} />
          </div>
        ) : null}
      </section>

      {isTraceFullscreen ? (
        <div className="fixed inset-0 z-[80] bg-background/95 p-3 text-foreground backdrop-blur-xl">
          <div className="flex h-full min-h-0 flex-col overflow-hidden rounded-2xl border border-border/70 bg-background shadow-[0_30px_120px_-60px_rgb(0_0_0/0.95)]">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/70 bg-background-secondary/70 px-4 py-3">
              <div className="flex min-w-0 items-center gap-3">
                {renderMacWindowControls("close", "关闭 Trace 视图")}
                <div className="flex min-w-0 items-baseline gap-3">
                  <div className="shrink-0 text-sm font-semibold">
                    Trace 视图
                  </div>
                  <div className="truncate font-mono text-xs text-muted">
                    {traceTitle}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Chip color="default" size="sm" variant="soft">
                  Esc 退出
                </Chip>
                <TraceStat
                  label="方向"
                  tone="primary"
                  value={trace.direction}
                />
                <TraceStat label="节点" tone="secondary" value={nodes.length} />
                <TraceStat
                  label="字节"
                  tone="success"
                  value={trace.root?.byteEnd ?? "-"}
                />
              </div>
            </div>

            <div
              ref={fullscreenLayoutRef}
              className="grid min-h-0 flex-1 p-3"
              style={{
                gridTemplateColumns: `minmax(0, ${fullscreenSplit}%) 12px minmax(0, 1fr)`,
              }}
            >
              <div className="min-h-0 min-w-0">
                {renderTraceTreeSection(true)}
              </div>
              <button
                aria-label="拖动调整分栏宽度"
                className="group flex cursor-col-resize items-stretch justify-center px-1"
                type="button"
                onPointerDown={startFullscreenResize}
              >
                <span className="h-full w-px rounded-full bg-border transition-colors group-hover:bg-accent" />
              </button>
              <div className="min-h-0 min-w-0 space-y-3 overflow-y-auto pl-2">
                <TraceInspector selectedNode={selectedNode} trace={trace} />
                {renderPayloadSection(true)}
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
};
