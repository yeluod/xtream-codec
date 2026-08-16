export type DebugDraftMode = "decode" | "encode";

export type DebugDrafts = Record<DebugDraftMode, Record<string, string>>;

const DRAFTS_STORAGE_KEY = "xtream-codec:debug:drafts";
const SELECTED_CLASS_STORAGE_KEY = "xtream-codec:debug:selected-class";

const emptyDrafts = (): DebugDrafts => ({
  decode: {},
  encode: {},
});

export const readDebugDrafts = (): DebugDrafts => {
  if (typeof window === "undefined") {
    return emptyDrafts();
  }

  try {
    const value = window.localStorage.getItem(DRAFTS_STORAGE_KEY);

    if (!value) {
      return emptyDrafts();
    }

    const parsed = JSON.parse(value) as Partial<DebugDrafts>;

    return {
      decode: parsed.decode ?? {},
      encode: parsed.encode ?? {},
    };
  } catch {
    return emptyDrafts();
  }
};

export const saveDebugDraft = (
  drafts: DebugDrafts,
  mode: DebugDraftMode,
  targetClass: string,
  value: string,
) => {
  drafts[mode][targetClass] = value;

  try {
    window.localStorage.setItem(DRAFTS_STORAGE_KEY, JSON.stringify(drafts));
  } catch {
    // 忽略浏览器禁用本地存储或存储空间不足的情况
  }
};

export const readSelectedClass = () => {
  if (typeof window === "undefined") {
    return undefined;
  }

  try {
    return window.localStorage.getItem(SELECTED_CLASS_STORAGE_KEY) ?? undefined;
  } catch {
    return undefined;
  }
};

export const saveSelectedClass = (targetClass: string) => {
  try {
    window.localStorage.setItem(SELECTED_CLASS_STORAGE_KEY, targetClass);
  } catch {
    // 忽略浏览器禁用本地存储或存储空间不足的情况
  }
};
