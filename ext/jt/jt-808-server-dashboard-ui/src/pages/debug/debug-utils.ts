import { CodecDebugEntityOption, CodecTraceNode } from "@/types";

export type DebugMode = "decode" | "encode";
export type Tone =
  | "default"
  | "primary"
  | "secondary"
  | "success"
  | "warning"
  | "danger";

export type DecodeResult = {
  single?: {
    rawHexString: string;
    escapedHexString: string;
    details: import("@/types").CodecTraceView;
  };
  multiple?: {
    subPackageMetadata: unknown[];
    mergedHexString: string;
    details: import("@/types").CodecTraceView;
  };
};

export const formControlClass =
  "h-11 rounded-xl border border-border/80 bg-background-secondary/90 text-foreground shadow-[inset_0_1px_0_0_rgb(255_255_255/0.04)] outline-none transition-colors hover:border-border focus:border-accent focus:ring-1 focus:ring-accent/30";

export const getSimpleClassName = (fullName: string) => {
  return fullName.includes(".")
    ? fullName.slice(fullName.lastIndexOf(".") + 1)
    : fullName;
};

export const getEntityOptionLabel = (item: CodecDebugEntityOption) => {
  return `${getSimpleClassName(item.targetClass)} - 0x${item.messageId
    .toString(16)
    .padStart(4, "0")}`;
};

export const getMessageIdHex = (messageId?: number) => {
  if (messageId == null) return "-";

  return `0x${messageId.toString(16).padStart(4, "0")}`;
};

export const getEntityOptionSearchText = (item: CodecDebugEntityOption) => {
  return [getEntityOptionLabel(item), item.desc, item.targetClass]
    .filter(Boolean)
    .join(" ");
};

export const requireIntegerField = (value: number, label: string) => {
  if (!Number.isInteger(value)) {
    throw new Error(`${label} 必须是整数`);
  }

  return value;
};

export const splitHexBytes = (hex?: string | null) => {
  if (!hex) return [];

  return (
    hex
      .replace(/\s+/g, "")
      .match(/.{1,2}/g)
      ?.map((byte, index) => ({
        index,
        byte,
      })) ?? []
  );
};

export const stripTraceDetails = (value: unknown): unknown => {
  if (Array.isArray(value)) {
    return value.map((item) => stripTraceDetails(item));
  }

  if (value && typeof value === "object") {
    const record = value as Record<string, unknown>;

    if ("details" in record) {
      const { details, ...rest } = record;

      return {
        ...rest,
        trace: details ? "[trace view omitted]" : undefined,
      };
    }

    return Object.fromEntries(
      Object.entries(record).map(([key, item]) => [
        key,
        stripTraceDetails(item),
      ]),
    );
  }

  return value;
};

export const getNodeLabel = (node: CodecTraceNode) => {
  if (node.kind === "ROOT") return "root";
  if (node.path) return node.path;

  return node.name;
};

export const getNodeFieldDescription = (node?: CodecTraceNode) => {
  const fieldDesc = node?.attributes?.fieldDesc;

  return typeof fieldDesc === "string" && fieldDesc.trim()
    ? fieldDesc
    : undefined;
};

export const sortTraceChildrenByRange = (children: CodecTraceNode[] = []) => {
  return children
    .map((node, index) => ({ node, index }))
    .sort((left, right) => {
      const leftStart = left.node.byteStart ?? Number.MAX_SAFE_INTEGER;
      const rightStart = right.node.byteStart ?? Number.MAX_SAFE_INTEGER;

      if (leftStart !== rightStart) return leftStart - rightStart;

      const leftEnd = left.node.byteEnd ?? Number.MAX_SAFE_INTEGER;
      const rightEnd = right.node.byteEnd ?? Number.MAX_SAFE_INTEGER;

      if (leftEnd !== rightEnd) return leftEnd - rightEnd;

      return left.index - right.index;
    })
    .map(({ node }) => node);
};

const codecChipColors = ["accent", "success", "warning", "danger"] as const;

export const codecChipColor = (
  codecType: string,
): (typeof codecChipColors)[number] => {
  let hash = 0;

  for (let i = 0; i < codecType.length; i += 1) {
    hash = (hash * 31 + codecType.charCodeAt(i)) >>> 0;
  }

  return codecChipColors[hash % codecChipColors.length];
};

export const kindTone = (kind?: CodecTraceNode["kind"]): Tone => {
  switch (kind) {
    case "ROOT":
      return "primary";
    case "FIELD":
      return "success";
    case "NESTED_FIELD":
      return "secondary";
    case "COLLECTION":
    case "COLLECTION_ITEM":
      return "warning";
    case "MAP":
    case "MAP_ENTRY":
    case "MAP_ENTRY_ITEM":
      return "primary";
    case "LENGTH_FIELD":
      return "danger";
    default:
      return "default";
  }
};

export const statusTone = (status?: CodecTraceNode["status"]): Tone => {
  switch (status) {
    case "SUCCESS":
      return "success";
    case "ERROR":
      return "danger";
    case "SKIPPED":
      return "warning";
    case "STARTED":
      return "primary";
    default:
      return "default";
  }
};

export const toneDotClass = (tone: Tone) => {
  switch (tone) {
    case "primary":
      return "bg-sky-500/75";
    case "secondary":
      return "bg-violet-500/70";
    case "success":
      return "bg-emerald-500/70";
    case "warning":
      return "bg-amber-500/70";
    case "danger":
      return "bg-rose-500/80";
    default:
      return "bg-muted";
  }
};

export const tonePanelClass = (tone: Tone) => {
  switch (tone) {
    case "primary":
      return "border-sky-500/20 bg-sky-500/[0.025]";
    case "secondary":
      return "border-violet-500/20 bg-violet-500/[0.025]";
    case "success":
      return "border-emerald-500/20 bg-emerald-500/[0.025]";
    case "warning":
      return "border-amber-500/20 bg-amber-500/[0.025]";
    case "danger":
      return "border-rose-500/30 bg-rose-500/[0.045]";
    default:
      return "border-border/60 bg-background";
  }
};

export const chipColor = (
  tone: Tone,
): "default" | "success" | "warning" | "danger" | "accent" => {
  switch (tone) {
    case "success":
      return "success";
    case "warning":
      return "warning";
    case "danger":
      return "danger";
    case "primary":
    case "secondary":
      return "accent";
    default:
      return "default";
  }
};

export const chunkBytes = <T>(items: T[], size = 16) => {
  const rows: T[][] = [];

  for (let i = 0; i < items.length; i += size) {
    rows.push(items.slice(i, i + size));
  }

  return rows;
};

export const toByteValues = (bytes: Array<{ byte: string }>) => {
  return bytes
    .map(({ byte }) => Number.parseInt(byte, 16))
    .filter((value) => Number.isFinite(value));
};

export const formatInteger = (
  values: number[],
  endian: "be" | "le",
  signed: boolean,
) => {
  if (!values.length || values.length > 8) {
    return "-";
  }

  const ordered = endian === "be" ? values : [...values].reverse();
  let result = 0n;

  for (const value of ordered) {
    result = (result << 8n) + BigInt(value);
  }

  if (signed) {
    const bits = BigInt(values.length * 8);
    const signBit = 1n << (bits - 1n);

    if ((result & signBit) !== 0n) {
      result -= 1n << bits;
    }
  }

  return result.toString();
};

export const formatAscii = (values: number[]) => {
  if (!values.length) return "-";

  return values
    .map((value) =>
      value >= 0x20 && value <= 0x7e ? String.fromCharCode(value) : ".",
    )
    .join("");
};

export const formatUtf8 = (values: number[]) => {
  if (!values.length) return "-";

  const text = new TextDecoder("utf-8").decode(new Uint8Array(values));
  const visible = text.replace(/[\u0000-\u001f\u007f-\u009f\ufffd]/g, ".");

  return visible || "-";
};

export const formatText = (values: number[], encoding: string) => {
  if (!values.length) return "-";

  try {
    const text = new TextDecoder(encoding).decode(new Uint8Array(values));
    const visible = text.replace(/[\u0000-\u001f\u007f-\u009f\ufffd]/g, ".");

    return visible || "-";
  } catch {
    return "-";
  }
};

export const formatBcd = (values: number[]) => {
  if (!values.length) return "-";

  const digits: string[] = [];

  for (const value of values) {
    const high = (value >> 4) & 0x0f;
    const low = value & 0x0f;

    if (high > 9 || low > 9) {
      return "-";
    }
    digits.push(String(high), String(low));
  }

  return digits.join("");
};
