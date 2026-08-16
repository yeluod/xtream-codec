import { Chip } from "@heroui/react";

import { stripTraceDetails } from "../debug-utils";

import { JsonPreview } from "@/components/json-preview.tsx";
import { CodecTraceView } from "@/types";

export const RawResultPanel = ({
  trace,
  result,
}: {
  trace: CodecTraceView;
  result?: unknown;
}) => {
  return (
    <section className="overflow-hidden rounded-2xl border border-border/60 bg-background-secondary/50">
      <details>
        <summary className="cursor-pointer list-none border-b border-border/60 px-4 py-3 text-sm font-semibold marker:text-muted">
          原始返回
          <Chip className="ml-2" color="default" size="sm" variant="tertiary">
            trace 明细已折叠
          </Chip>
        </summary>
        <div className="max-h-90 overflow-auto p-4">
          <div className="min-w-max rounded-xl bg-background-tertiary p-3">
            <JsonPreview
              json={stripTraceDetails((result ?? trace) as object) as object}
            />
          </div>
        </div>
      </details>
    </section>
  );
};
