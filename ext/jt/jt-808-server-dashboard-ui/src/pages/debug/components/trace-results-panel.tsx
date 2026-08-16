import { TracePanel } from "./trace-panel";

import { CodecTraceView } from "@/types";

export const TraceResultsPanel = ({
  result,
  traces,
}: {
  result?: unknown;
  traces: CodecTraceView[];
}) => {
  if (traces.length === 0) {
    return <TracePanel result={result} />;
  }

  return (
    <div className="space-y-4">
      {traces.map((trace, index) => (
        <TracePanel
          key={`${trace.root?.id ?? "trace"}-${index}`}
          label={
            traces.length > 1 ? `#${index + 1}/${traces.length}` : undefined
          }
          result={index === 0 ? result : undefined}
          showRawResult={index === 0}
          trace={trace}
        />
      ))}
    </div>
  );
};
