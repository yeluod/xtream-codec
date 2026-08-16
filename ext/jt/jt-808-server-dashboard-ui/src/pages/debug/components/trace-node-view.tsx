import { Chip } from "@heroui/react";
import clsx from "clsx";

import {
  chipColor,
  codecChipColor,
  getNodeFieldDescription,
  getNodeLabel,
  kindTone,
  sortTraceChildrenByRange,
  toneDotClass,
} from "../debug-utils";

import { LuChevronDownIcon, LuChevronRightIcon } from "@/components/icons.tsx";
import { CodecTraceNode } from "@/types";

export const TraceNodeView = ({
  node,
  depth = 0,
  selectedNodeId,
  collapsedNodeIds,
  onSelectNode,
  onToggleNode,
}: {
  node: CodecTraceNode;
  depth?: number;
  selectedNodeId?: string;
  collapsedNodeIds: ReadonlySet<string>;
  onSelectNode: (node: CodecTraceNode) => void;
  onToggleNode: (node: CodecTraceNode) => void;
}) => {
  const tone = kindTone(node.kind);
  const isSelected = selectedNodeId === node.id;
  const range =
    node.byteStart != null && node.byteEnd != null
      ? `${node.byteStart}-${node.byteEnd}`
      : "-";
  const fieldDescription = getNodeFieldDescription(node);
  const children = sortTraceChildrenByRange(node.children);
  const hasChildren = children.length > 0;
  const isExpanded = !collapsedNodeIds.has(node.id);

  return (
    <div>
      <div className="relative">
        <button
          className={clsx(
            "grid w-full min-w-190 grid-cols-[minmax(240px,1.2fr)_116px_92px_minmax(180px,1fr)] items-center gap-3 border-b border-border/40 px-3 py-2 text-left transition-colors",
            isSelected ? "bg-accent/12" : "hover:bg-background-secondary/70",
          )}
          data-trace-node-id={node.id}
          type="button"
          onClick={() => onSelectNode(node)}
        >
          <span
            className="flex min-w-0 items-center gap-2"
            style={{ paddingLeft: depth * 14 + 18 }}
          >
            <span
              className={clsx(
                "size-2 shrink-0 rounded-full",
                toneDotClass(tone),
              )}
            />
            <span className="flex min-w-0 items-center gap-2">
              <span className="truncate font-mono text-xs font-semibold text-foreground">
                {getNodeLabel(node)}
              </span>
              {fieldDescription ? (
                <span className="max-w-40 shrink-0 truncate text-[11px] font-normal text-muted">
                  {fieldDescription}
                </span>
              ) : null}
            </span>
          </span>
          <span>
            <Chip color={chipColor(tone)} size="sm" variant="soft">
              {node.kind}
            </Chip>
          </span>
          <span className="font-mono text-xs text-muted">{range}</span>
          <span className="flex min-w-0 items-center gap-2">
            <span className="min-w-0 flex-1 truncate font-mono text-xs text-foreground">
              {node.valueSummary ?? node.status}
            </span>
            {node.codecType ? (
              <Chip
                className="max-w-52 shrink-0"
                color={codecChipColor(node.codecType)}
                size="sm"
                variant="soft"
              >
                <span className="block max-w-44 truncate font-mono text-[10px]">
                  {node.codecType}
                </span>
              </Chip>
            ) : null}
          </span>
        </button>
        {hasChildren ? (
          <button
            aria-label={isExpanded ? "折叠节点" : "展开节点"}
            className="absolute top-1/2 z-10 grid size-4 -translate-y-1/2 place-items-center rounded text-muted transition-colors hover:bg-background hover:text-foreground"
            style={{ left: depth * 14 + 2 }}
            title={isExpanded ? "折叠节点" : "展开节点"}
            type="button"
            onClick={() => onToggleNode(node)}
          >
            {isExpanded ? (
              <LuChevronDownIcon className="size-3" />
            ) : (
              <LuChevronRightIcon className="size-3" />
            )}
          </button>
        ) : null}
      </div>
      {isExpanded
        ? children.map((child) => (
            <TraceNodeView
              key={child.id}
              collapsedNodeIds={collapsedNodeIds}
              depth={depth + 1}
              node={child}
              selectedNodeId={selectedNodeId}
              onSelectNode={onSelectNode}
              onToggleNode={onToggleNode}
            />
          ))
        : null}
    </div>
  );
};
