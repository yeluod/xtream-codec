import {
  Button,
  Card,
  Chip,
  Input,
  Label,
  Spinner,
  TextArea,
  TextField,
} from "@heroui/react";
import clsx from "clsx";

import {
  DebugMode,
  formControlClass,
  getEntityOptionLabel,
} from "../debug-utils";

import { DebugNumberField } from "./debug-number-field";

import { LuChevronDownIcon, LuResetIcon } from "@/components/icons.tsx";
import { JsonEditor } from "@/components/ui/json-editor.tsx";
import { Segment } from "@/components/ui/segment.tsx";
import { CodecDebugEntityOption, CodecDebugOptions } from "@/types";

type DebugFormCardProps = {
  bodyJson: string;
  entityQuery: string;
  flowId: number;
  hexString: string;
  isEntityPickerOpen: boolean;
  isEntitySearching: boolean;
  loading: boolean;
  maxPackageSize: number;
  mode: DebugMode;
  options?: CodecDebugOptions;
  reversedBit15InHeader: number;
  selectedEntity?: CodecDebugEntityOption;
  selectedClass: string;
  terminalId: string;
  visibleEntityOptions: CodecDebugEntityOption[];
  version: string;
  onBodyJsonChange: (value: string) => void;
  onEntityPickerOpenChange: (open: boolean) => void;
  onEntityQueryChange: (value: string) => void;
  onFlowIdChange: (value: number) => void;
  onHexStringChange: (value: string) => void;
  onMaxPackageSizeChange: (value: number) => void;
  onModeChange: (mode: DebugMode) => void;
  onReversedBit15InHeaderChange: (value: number) => void;
  onResetSample: () => void;
  onRun: () => void;
  onSelectEntity: (item: CodecDebugEntityOption) => void;
  onTerminalIdChange: (value: string) => void;
  onVersionChange: (value: string) => void;
};

export const DebugFormCard = ({
  bodyJson,
  entityQuery,
  flowId,
  hexString,
  isEntityPickerOpen,
  isEntitySearching,
  loading,
  maxPackageSize,
  mode,
  reversedBit15InHeader,
  selectedEntity,
  selectedClass,
  terminalId,
  visibleEntityOptions,
  version,
  onBodyJsonChange,
  onEntityPickerOpenChange,
  onEntityQueryChange,
  onFlowIdChange,
  onHexStringChange,
  onMaxPackageSizeChange,
  onModeChange,
  onReversedBit15InHeaderChange,
  onResetSample,
  onRun,
  onSelectEntity,
  onTerminalIdChange,
  onVersionChange,
}: DebugFormCardProps) => {
  return (
    <Card className="relative z-20 overflow-visible rounded-2xl border border-border/60 bg-background-secondary/50 shadow-[inset_0_1px_0_rgb(255_255_255/0.035)]">
      <Card.Content className="p-4">
        <div className="min-w-0 space-y-3">
          <div
            className={clsx(
              "grid gap-3",
              mode === "encode"
                ? "xl:grid-cols-[180px_minmax(320px,520px)_120px_150px]"
                : "xl:grid-cols-[180px_minmax(320px,520px)]",
            )}
          >
            <div className="flex min-w-0 flex-col gap-1">
              <Label className="flex items-center gap-2 text-sm text-muted">
                <span className="h-1.5 w-1.5 rounded-full bg-sky-500/75" />
                方向
              </Label>
              <Segment
                selectedKey={mode}
                onSelectionChange={(key) =>
                  onModeChange(String(key) as DebugMode)
                }
              >
                <Segment.Item id="decode">解码</Segment.Item>
                <Segment.Item id="encode">
                  <Segment.Separator />
                  编码
                </Segment.Item>
              </Segment>
            </div>

            <div
              className="relative flex min-w-0 flex-col gap-1"
              onBlur={(event) => {
                const nextFocused = event.relatedTarget;

                if (
                  nextFocused instanceof Node &&
                  event.currentTarget.contains(nextFocused)
                ) {
                  return;
                }

                if (selectedEntity) {
                  onEntityQueryChange(getEntityOptionLabel(selectedEntity));
                }
                onEntityPickerOpenChange(false);
              }}
              onFocus={() => {
                onEntityPickerOpenChange(true);
              }}
            >
              <Label className="flex items-center gap-2 text-sm text-muted">
                <span className="h-1.5 w-1.5 rounded-full bg-violet-500/70" />
                实体类型
              </Label>
              <div
                className={clsx(
                  formControlClass,
                  "grid grid-cols-[minmax(0,1fr)_auto] items-center gap-4 px-3",
                )}
              >
                <div className="flex min-w-0 items-baseline gap-2">
                  <Input
                    className="h-full min-w-0 flex-1 rounded-none border-0 bg-transparent p-0 text-sm shadow-none outline-none ring-0 focus:border-0 focus:outline-none focus:ring-0 focus-visible:outline-none"
                    value={entityQuery}
                    onChange={(event) => {
                      onEntityQueryChange(event.target.value);
                    }}
                    onCompositionEnd={(event) => {
                      onEntityQueryChange(event.currentTarget.value);
                    }}
                    onInput={(event) => {
                      onEntityQueryChange(event.currentTarget.value);
                    }}
                  />
                  {selectedEntity?.desc && !isEntitySearching ? (
                    <Chip
                      className="min-w-0 shrink truncate"
                      color="default"
                      size="sm"
                      variant="tertiary"
                    >
                      {selectedEntity.desc}
                    </Chip>
                  ) : null}
                </div>
                <button
                  className="grid size-7 shrink-0 place-items-center rounded-md text-muted transition-colors hover:bg-background-tertiary hover:text-foreground"
                  type="button"
                  onClick={() => {
                    onEntityPickerOpenChange(!isEntityPickerOpen);
                  }}
                  onMouseDown={(event) => {
                    event.preventDefault();
                  }}
                >
                  <LuChevronDownIcon
                    className={clsx(
                      "size-4 transition-transform",
                      isEntityPickerOpen ? "rotate-180" : "",
                    )}
                  />
                </button>
              </div>

              {isEntityPickerOpen ? (
                <div className="absolute left-0 right-0 top-[calc(100%+0.35rem)] z-50 max-h-112 overflow-y-auto rounded-2xl border border-border/70 bg-background-secondary/95 p-2 shadow-[0_24px_80px_-42px_rgb(0_0_0/0.9)] backdrop-blur">
                  {visibleEntityOptions.length ? (
                    visibleEntityOptions.map((item) => (
                      <button
                        key={item.targetClass}
                        className={clsx(
                          "flex w-full min-w-0 items-baseline gap-2 rounded-xl border border-transparent px-3 py-2.5 text-left transition-colors hover:border-border/80 hover:bg-background-tertiary/70",
                          item.targetClass === selectedClass
                            ? "border-accent/25 bg-accent/6 text-foreground"
                            : "text-foreground/90",
                        )}
                        type="button"
                        onClick={() => {
                          onSelectEntity(item);
                        }}
                        onMouseDown={(event) => {
                          event.preventDefault();
                        }}
                      >
                        <div className="shrink-0">
                          {getEntityOptionLabel(item)}
                        </div>
                        {item.desc ? (
                          <Chip
                            className="min-w-0 truncate"
                            color="default"
                            size="sm"
                            variant="tertiary"
                          >
                            {item.desc}
                          </Chip>
                        ) : null}
                      </button>
                    ))
                  ) : (
                    <div className="px-3 py-6 text-sm text-muted">
                      没有匹配的实体类型
                    </div>
                  )}
                </div>
              ) : null}
            </div>

            {mode === "encode" ? (
              <>
                <div className="flex min-w-0 flex-col gap-1">
                  <Label className="text-muted">消息 ID</Label>
                  <div
                    className={clsx(
                      formControlClass,
                      "flex items-center gap-2 px-3",
                    )}
                  >
                    <span className="font-mono text-sm text-foreground">
                      {selectedEntity?.messageId ?? "-"}
                    </span>
                    <Chip color="danger" size="sm" variant="soft">
                      {selectedEntity
                        ? `0x${selectedEntity.messageId.toString(16).padStart(4, "0")}`
                        : "-"}
                    </Chip>
                  </div>
                </div>
                <DebugNumberField
                  label="单包最大字节数"
                  minValue={25}
                  value={maxPackageSize}
                  onChange={onMaxPackageSizeChange}
                />
              </>
            ) : null}
          </div>

          {mode === "encode" ? (
            <fieldset className="rounded-xl border border-border/50 bg-background/20 px-3 pb-3 pt-1.5">
              <legend className="px-2 text-xs font-medium text-muted">
                终端与控制
              </legend>
              <div className="grid gap-3 xl:grid-cols-[150px_minmax(260px,420px)_130px_160px_110px]">
                <div className="flex min-w-0 flex-col gap-1">
                  <Label className="text-muted">协议版本</Label>
                  <Segment
                    className="min-h-11"
                    selectedKey={version}
                    onSelectionChange={(key) => onVersionChange(String(key))}
                  >
                    <Segment.Item id="VERSION_2019">2019</Segment.Item>
                    <Segment.Item id="VERSION_2013">
                      <Segment.Separator />
                      2013
                    </Segment.Item>
                  </Segment>
                </div>
                <div className="min-w-0">
                  <TextField
                    className="min-w-0"
                    value={terminalId}
                    onChange={onTerminalIdChange}
                  >
                    <Label className="text-muted">终端手机号</Label>
                    <Input className={formControlClass} />
                  </TextField>
                </div>
                <DebugNumberField
                  label="流水号"
                  value={flowId}
                  onChange={onFlowIdChange}
                />
                <DebugNumberField
                  label="保留位(消息头 bit15)"
                  maxValue={1}
                  minValue={0}
                  value={reversedBit15InHeader}
                  onChange={onReversedBit15InHeaderChange}
                />
                <DebugNumberField
                  isDisabled
                  label="加密类型"
                  value={0}
                  onChange={() => {}}
                />
              </div>
            </fieldset>
          ) : null}

          {mode === "decode" ? (
            <div className="grid min-w-0 gap-3 xl:grid-cols-[minmax(0,1fr)_120px]">
              <div className="flex min-w-0 flex-col gap-1">
                <Label className="flex items-center gap-2 text-sm text-muted">
                  <span className="h-1.5 w-1.5 rounded-full bg-emerald-500/75" />
                  报文 HEX
                </Label>
                <TextArea
                  fullWidth
                  className={clsx(
                    formControlClass,
                    "min-h-26 resize-y px-3 py-2.5 font-mono text-[13px] leading-6",
                  )}
                  spellCheck={false}
                  value={hexString}
                  variant="secondary"
                  onChange={(event) => onHexStringChange(event.target.value)}
                />
              </div>
              <div className="flex flex-col justify-end gap-2">
                <Button
                  className="h-11 w-full"
                  isDisabled={loading || !selectedClass}
                  variant="secondary"
                  onPress={onResetSample}
                >
                  <LuResetIcon size={14} />
                  重置示例
                </Button>
                <Button
                  className="h-11 w-full"
                  isDisabled={loading}
                  onPress={onRun}
                >
                  {loading ? <Spinner size="sm" /> : null}
                  解码
                </Button>
              </div>
            </div>
          ) : (
            <div className="grid min-w-0 gap-3 xl:grid-cols-[minmax(0,1fr)_120px]">
              <div className="flex min-w-0 flex-col gap-1">
                <Label className="flex items-center gap-2 text-sm text-muted">
                  <span className="h-1.5 w-1.5 rounded-full bg-amber-500/75" />
                  消息体 JSON
                </Label>
                <JsonEditor
                  minHeight="6rem"
                  value={bodyJson}
                  onChange={onBodyJsonChange}
                />
              </div>
              <div className="flex flex-col justify-end gap-2">
                <Button
                  className="h-11 w-full"
                  isDisabled={loading || !selectedClass}
                  variant="secondary"
                  onPress={onResetSample}
                >
                  <LuResetIcon size={14} />
                  重置示例
                </Button>
                <Button
                  className="h-11 w-full"
                  isDisabled={loading}
                  onPress={onRun}
                >
                  {loading ? <Spinner size="sm" /> : null}
                  编码
                </Button>
              </div>
            </div>
          )}
        </div>
      </Card.Content>
    </Card>
  );
};
