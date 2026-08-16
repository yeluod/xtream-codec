import { useEffect, useMemo, useRef, useState } from "react";

import { DebugFormCard } from "./components/debug-form-card";
import { TraceResultsPanel } from "./components/trace-results-panel";
import {
  DebugMode,
  DecodeResult,
  getEntityOptionLabel,
  getEntityOptionSearchText,
  requireIntegerField,
} from "./debug-utils";
import {
  DebugDraftMode,
  DebugDrafts,
  readDebugDrafts,
  readSelectedClass,
  saveDebugDraft,
  saveSelectedClass,
} from "./debug-storage";

import { PageSection } from "@/components/page-header.tsx";
import codecMockData from "@/data/codec-mock-data.json";
import {
  CodecDebugEntityOption,
  CodecDebugOptions,
  CodecDebugTracker,
  CodecTraceView,
  Dic,
} from "@/types";
import { request } from "@/utils/request.ts";

type CodecMockDataItem = {
  bodyJson?: unknown;
  hexString?: string;
};

const getSampleValue = (mode: DebugDraftMode, targetClass: string) => {
  const sample = (codecMockData as Record<string, CodecMockDataItem>)[
    targetClass
  ];

  if (mode === "encode") {
    return JSON.stringify(sample?.bodyJson ?? {}, null, 2);
  }

  return sample?.hexString ?? "";
};

export const DebugPage = () => {
  const [mode, setMode] = useState<DebugMode>("decode");
  const [options, setOptions] = useState<CodecDebugOptions>();
  const [selectedClass, setSelectedClass] = useState("");
  const [entityQuery, setEntityQuery] = useState("");
  const [isEntityPickerOpen, setIsEntityPickerOpen] = useState(false);
  const [version, setVersion] = useState("VERSION_2019");
  const [terminalId, setTerminalId] = useState("");
  const [flowId, setFlowId] = useState(-1);
  const [maxPackageSize, setMaxPackageSize] = useState(1024);
  const [reversedBit15InHeader, setReversedBit15InHeader] = useState(0);
  const [hexString, setHexString] = useState("");
  const [bodyJson, setBodyJson] = useState("{}");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<unknown>();
  const [traces, setTraces] = useState<CodecTraceView[]>([]);
  const draftsRef = useRef<DebugDrafts>(readDebugDrafts());

  const getDraftValue = (mode: DebugDraftMode, targetClass: string) => {
    return (
      draftsRef.current[mode][targetClass] ?? getSampleValue(mode, targetClass)
    );
  };

  const updateDraft = (
    mode: DebugDraftMode,
    targetClass: string,
    value: string,
  ) => {
    saveDebugDraft(draftsRef.current, mode, targetClass, value);
  };

  useEffect(() => {
    request<CodecDebugOptions>({
      path: "codec/codec-options",
      method: "GET",
    }).then((data) => {
      setOptions(data);
      setTerminalId(data.defaultTerminalId);
      const savedClass = readSelectedClass();
      const first =
        data.classMetadata.find((item) => item.targetClass === savedClass) ??
        data.classMetadata[0];

      if (first) {
        setSelectedClass(first.targetClass);
        setEntityQuery(getEntityOptionLabel(first));
        setMaxPackageSize(first.maxPackageSize ?? 1024);
        setReversedBit15InHeader(first.reversedBit15InHeader ?? 0);
        setHexString(getDraftValue("decode", first.targetClass));
        setBodyJson(getDraftValue("encode", first.targetClass));
      }
    });
  }, []);

  const selectedEntity = useMemo<CodecDebugEntityOption | undefined>(() => {
    return options?.classMetadata.find(
      (item) => item.targetClass === selectedClass,
    );
  }, [options, selectedClass]);

  const selectedEntityLabel = selectedEntity
    ? getEntityOptionLabel(selectedEntity)
    : "";
  const traceTitle = [selectedEntityLabel, selectedEntity?.desc]
    .filter(Boolean)
    .join(" · ");
  const isEntitySearching = entityQuery !== selectedEntityLabel;

  const visibleEntityOptions = useMemo(() => {
    const all = options?.classMetadata ?? [];
    const query = isEntitySearching ? entityQuery.trim().toLowerCase() : "";

    if (!query) {
      return all;
    }

    return all.filter((item) => {
      return getEntityOptionSearchText(item).toLowerCase().includes(query);
    });
  }, [entityQuery, isEntitySearching, options?.classMetadata]);

  const updateEntityQuery = (value: string) => {
    setEntityQuery(value);
    setIsEntityPickerOpen(true);
  };

  const updateMode = (nextMode: DebugMode) => {
    if (selectedClass) {
      updateDraft(
        mode,
        selectedClass,
        mode === "encode" ? bodyJson : hexString,
      );
    }

    setMode(nextMode);

    if (selectedClass) {
      const nextValue = getDraftValue(nextMode, selectedClass);

      if (nextMode === "encode") {
        setBodyJson(nextValue);
      } else {
        setHexString(nextValue);
      }
    }
  };

  const selectEntity = (item: CodecDebugEntityOption) => {
    if (selectedClass) {
      updateDraft(
        mode,
        selectedClass,
        mode === "encode" ? bodyJson : hexString,
      );
    }

    setSelectedClass(item.targetClass);
    setEntityQuery(getEntityOptionLabel(item));
    setMaxPackageSize(item.maxPackageSize ?? 1024);
    setReversedBit15InHeader(item.reversedBit15InHeader ?? 0);
    setIsEntityPickerOpen(false);
    saveSelectedClass(item.targetClass);

    const nextValue = getDraftValue(mode, item.targetClass);

    if (mode === "encode") {
      setBodyJson(nextValue);
    } else {
      setHexString(nextValue);
    }
  };

  const updateBodyJson = (value: string) => {
    setBodyJson(value);

    if (selectedClass) {
      updateDraft("encode", selectedClass, value);
    }
  };

  const updateHexString = (value: string) => {
    setHexString(value);

    if (selectedClass) {
      updateDraft("decode", selectedClass, value);
    }
  };

  const resetSampleInput = () => {
    if (!selectedClass) {
      return;
    }

    const sampleValue = getSampleValue(mode, selectedClass);

    if (mode === "encode") {
      setBodyJson(sampleValue);
    } else {
      setHexString(sampleValue);
    }

    updateDraft(mode, selectedClass, sampleValue);
    setError("");
  };

  const runDecode = async () => {
    const response = await request<DecodeResult>({
      path: "codec/decode-with-entity",
      method: "POST",
      data: {
        bodyClass: selectedClass,
        hexString: hexString
          .split("\n")
          .map((line) => line.trim())
          .filter(Boolean),
      },
    });
    const detail = response.single?.details ?? response.multiple?.details;

    setTraces(detail ? [detail] : []);
    setResult(response);
  };

  const runEncode = async () => {
    const bodyData = JSON.parse(bodyJson) as Dic;
    const flowIdValue = requireIntegerField(flowId, "流水号");
    const reversedBit15InHeaderValue = requireIntegerField(
      reversedBit15InHeader,
      "保留位",
    );
    const maxPackageSizeValue = requireIntegerField(
      maxPackageSize,
      "单包最大字节数",
    );

    if (reversedBit15InHeaderValue < 0 || reversedBit15InHeaderValue > 1) {
      throw new Error("保留位只能是 0 或 1");
    }
    if (maxPackageSizeValue < 25) {
      throw new Error("单包最大字节数不能小于 25");
    }

    const response = await request<CodecDebugTracker[]>({
      path: "codec/encode-with-entity",
      method: "POST",
      data: {
        version,
        terminalId,
        messageId: selectedEntity?.messageId,
        flowId: flowIdValue,
        reversedBit15InHeader: reversedBit15InHeaderValue,
        maxPackageSize: maxPackageSizeValue,
        encryptionType: 0,
        bodyClass: selectedClass,
        bodyData,
      },
    });

    setTraces(response.map((item) => item.details).filter(Boolean));
    setResult(response);
  };

  const run = async () => {
    setLoading(true);
    setError("");
    try {
      if (!selectedClass) {
        throw new Error("请选择实体类型");
      }
      if (mode === "decode") {
        await runDecode();
      } else {
        await runEncode();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <PageSection>
      <div className="flex min-h-0 flex-col gap-4">
        <DebugFormCard
          bodyJson={bodyJson}
          entityQuery={entityQuery}
          flowId={flowId}
          hexString={hexString}
          isEntityPickerOpen={isEntityPickerOpen}
          isEntitySearching={isEntitySearching}
          loading={loading}
          maxPackageSize={maxPackageSize}
          mode={mode}
          options={options}
          reversedBit15InHeader={reversedBit15InHeader}
          selectedClass={selectedClass}
          selectedEntity={selectedEntity}
          terminalId={terminalId}
          version={version}
          visibleEntityOptions={visibleEntityOptions}
          onBodyJsonChange={updateBodyJson}
          onEntityPickerOpenChange={setIsEntityPickerOpen}
          onEntityQueryChange={updateEntityQuery}
          onFlowIdChange={setFlowId}
          onHexStringChange={updateHexString}
          onMaxPackageSizeChange={setMaxPackageSize}
          onModeChange={updateMode}
          onReversedBit15InHeaderChange={setReversedBit15InHeader}
          onResetSample={resetSampleInput}
          onRun={run}
          onSelectEntity={selectEntity}
          onTerminalIdChange={setTerminalId}
          onVersionChange={setVersion}
        />

        {error ? (
          <div className="rounded-md border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
            {error}
          </div>
        ) : null}

        <TraceResultsPanel
          result={result}
          traces={traces}
          traceTitle={traceTitle}
        />
      </div>
    </PageSection>
  );
};
