import CodeMirror, { EditorView } from "@uiw/react-codemirror";
import { json } from "@codemirror/lang-json";
import clsx from "clsx";
import { useEffect, useMemo, useState } from "react";

type JsonEditorProps = {
  className?: string;
  minHeight?: string;
  value: string;
  onChange: (value: string) => void;
};

const createJsonEditorTheme = (colorScheme: "light" | "dark") => {
  const isDark = colorScheme === "dark";

  return EditorView.theme(
    {
      "&": {
        backgroundColor: "transparent",
        color: isDark ? "#e5e7eb" : "#27272a",
        fontSize: "12px",
      },
      "&.cm-focused": {
        outline: "none",
      },
      ".cm-content": {
        caretColor: isDark ? "#f9fafb" : "#18181b",
        padding: "10px 0",
      },
      ".cm-cursor": {
        borderLeftColor: isDark ? "#f9fafb" : "#18181b",
      },
      ".cm-gutters": {
        backgroundColor: "transparent",
        border: "none",
        color: isDark ? "rgb(161 161 170 / 0.72)" : "rgb(82 82 91 / 0.8)",
      },
      ".cm-line": {
        padding: "0 12px 0 6px",
      },
      ".cm-scroller": {
        fontFamily:
          "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace",
      },
      ".cm-activeLine": {
        backgroundColor: isDark
          ? "rgb(255 255 255 / 0.04)"
          : "rgb(0 0 0 / 0.04)",
      },
      ".cm-activeLineGutter": {
        backgroundColor: "transparent",
        color: isDark ? "#d4d4d8" : "#3f3f46",
      },
      ".cm-selectionBackground": {
        backgroundColor: isDark
          ? "rgb(0 149 255 / 0.28) !important"
          : "rgb(0 111 238 / 0.2) !important",
      },
      ".cm-matchingBracket, .cm-nonmatchingBracket": {
        backgroundColor: isDark
          ? "rgb(0 149 255 / 0.18)"
          : "rgb(0 111 238 / 0.12)",
        outline: isDark
          ? "1px solid rgb(0 149 255 / 0.3)"
          : "1px solid rgb(0 111 238 / 0.3)",
      },
    },
    { dark: isDark },
  );
};

const readColorScheme = (): "light" | "dark" => {
  if (typeof document === "undefined") {
    return "dark";
  }

  const root = document.documentElement;

  if (root.dataset.theme === "light" || root.classList.contains("light")) {
    return "light";
  }

  if (root.dataset.theme === "dark" || root.classList.contains("dark")) {
    return "dark";
  }

  return window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
};

export const JsonEditor = ({
  className,
  minHeight = "6rem",
  onChange,
  value,
}: JsonEditorProps) => {
  const [colorScheme, setColorScheme] = useState(readColorScheme);

  useEffect(() => {
    const root = document.documentElement;
    const observer = new MutationObserver(() => {
      setColorScheme(readColorScheme());
    });

    observer.observe(root, {
      attributeFilter: ["class", "data-theme"],
      attributes: true,
    });

    return () => observer.disconnect();
  }, []);

  const editorTheme = useMemo(
    () => createJsonEditorTheme(colorScheme),
    [colorScheme],
  );
  const extensions = useMemo(
    () => [json(), EditorView.lineWrapping, editorTheme],
    [editorTheme],
  );

  return (
    <div
      className={clsx(
        "overflow-hidden rounded-xl border border-border/80 bg-background-secondary/90 text-foreground shadow-[inset_0_1px_0_0_rgb(255_255_255/0.04)] transition-colors hover:border-border focus-within:border-accent focus-within:ring-1 focus-within:ring-accent/30",
        className,
      )}
    >
      <CodeMirror
        key={colorScheme}
        basicSetup={{
          foldGutter: true,
          highlightActiveLine: true,
          highlightActiveLineGutter: false,
          lineNumbers: true,
        }}
        extensions={extensions}
        minHeight={minHeight}
        theme={editorTheme}
        value={value}
        onChange={onChange}
      />
    </div>
  );
};
