import { confirmModal, showToast } from "./ui.js";

const indent = "    ";
const templates = {
    JAVA: `public class Main {
    public static void main(String[] args) {

    }
}
`,
    CPP: `#include <bits/stdc++.h>
using namespace std;

int main() {
    return 0;
}
`
};

export function indentSelection(code, start, end, outdent) {
    const collapsed = start === end;
    if (collapsed && !outdent) {
        const caret = start + indent.length;
        return { text: indent, from: start, to: end, start: caret, end: caret };
    }

    const from = code.slice(0, start).lastIndexOf("\n") + 1;
    // 选区结束在下一行行首时，换行符之后的内容仍在选区外。
    const newline = code.indexOf("\n", collapsed ? end : end - 1);
    const to = newline === -1 ? code.length : newline;
    const selected = code.slice(from, to);
    const text = selected.split("\n").map(line => outdent
        ? line.replace(/^(?: {1,4}|\t)/, "")
        : indent + line).join("\n");
    if (text === selected) {
        return null;
    }
    if (collapsed) {
        const caret = Math.max(from, start - (selected.length - text.length));
        return { text, from, to, start: caret, end: caret };
    }
    return { text, from, to, start: from, end: from + text.length };
}

export function initializeEditor(form, problemId) {
    const { code: editor, language } = form.elements;
    const lineNumbers = form.querySelector("#editor-lines");
    const reset = form.querySelector("#reset-template");
    const submit = form.querySelector('button[type="submit"]');
    const legacyPrefix = "mini-oj:draft:" + problemId + ":";
    const draftPrefix = "minioj_draft_" + problemId + "_";
    const unsavedDrafts = new Map();
    let activeLanguage = language.value;
    let draftTimer = null;
    let storageWarningShown = false;
    let leaveOnTab = false;
    try {
        const lastLanguage = sessionStorage.getItem(legacyPrefix + "language");
        if (Object.hasOwn(templates, lastLanguage)) {
            activeLanguage = lastLanguage;
        }
    } catch {
        // 浏览器禁用站点存储时，只保留当前页面中的草稿。
    }
    language.value = activeLanguage;

    function alignLineNumbers() {
        lineNumbers.style.transform = `translateY(${-editor.scrollTop}px)`;
    }

    function updateLineNumbers() {
        const count = editor.value.split("\n").length;
        if (lineNumbers.dataset.count !== String(count)) {
            lineNumbers.textContent = Array.from({ length: count }, (_, index) => index + 1).join("\n");
            lineNumbers.dataset.count = count;
        }
        alignLineNumbers();
    }

    function setCode(code) {
        editor.value = code;
        editor.setSelectionRange(0, 0);
        editor.scrollTop = 0;
        editor.scrollLeft = 0;
        updateLineNumbers();
    }

    function storeDraft(code) {
        try {
            localStorage.setItem(draftPrefix + activeLanguage, code);
            unsavedDrafts.delete(activeLanguage);
        } catch {
            unsavedDrafts.set(activeLanguage, code);
            if (!storageWarningShown) {
                showToast("浏览器无法保存草稿，草稿将保留到本页关闭。", "error");
                storageWarningShown = true;
            }
        }
    }

    function readDraft() {
        if (unsavedDrafts.has(activeLanguage)) {
            return unsavedDrafts.get(activeLanguage);
        }
        try {
            const saved = localStorage.getItem(draftPrefix + activeLanguage);
            if (saved !== null) {
                return saved;
            }
            const legacy = sessionStorage.getItem(legacyPrefix + activeLanguage);
            if (legacy !== null) {
                storeDraft(legacy);
            }
            return legacy;
        } catch {
            return null;
        }
    }

    function flushDraft() {
        if (draftTimer !== null) {
            window.clearTimeout(draftTimer);
            draftTimer = null;
            storeDraft(editor.value);
        }
    }

    setCode(readDraft() ?? templates[activeLanguage]);
    editor.addEventListener("input", () => {
        updateLineNumbers();
        window.clearTimeout(draftTimer);
        draftTimer = window.setTimeout(flushDraft, 350);
    });
    editor.addEventListener("scroll", alignLineNumbers);
    window.addEventListener("pagehide", flushDraft);
    language.addEventListener("change", () => {
        flushDraft();
        activeLanguage = language.value;
        try {
            sessionStorage.setItem(legacyPrefix + "language", activeLanguage);
        } catch {
            // 语言选择仍在当前页面生效。
        }
        setCode(readDraft() ?? templates[activeLanguage]);
    });

    reset.addEventListener("click", async () => {
        const template = templates[activeLanguage];
        if (editor.value === template) {
            return;
        }
        if (!await confirmModal("重置代码模板", "当前语言的草稿将被默认模板覆盖。")) {
            return;
        }
        setCode(template);
        window.clearTimeout(draftTimer);
        draftTimer = null;
        storeDraft(template);
        editor.focus();
    });

    editor.addEventListener("blur", () => { leaveOnTab = false; });
    editor.addEventListener("keydown", event => {
        if (event.isComposing) {
            return;
        }
        if (event.key === "Escape") {
            leaveOnTab = true;
            return;
        }
        const leave = leaveOnTab;
        leaveOnTab = false;
        if (event.key !== "Tab" || leave || event.ctrlKey || event.altKey || event.metaKey) {
            return;
        }
        event.preventDefault();
        const change = indentSelection(editor.value, editor.selectionStart, editor.selectionEnd, event.shiftKey);
        if (change) {
            const direction = editor.selectionDirection;
            editor.setRangeText(change.text, change.from, change.to);
            editor.setSelectionRange(change.start, change.end, direction);
            editor.dispatchEvent(new Event("input", { bubbles: true }));
        }
    });
    form.addEventListener("keydown", event => {
        if (!event.isComposing && event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
            event.preventDefault();
            if (!event.repeat && !submit.disabled) {
                form.requestSubmit();
            }
        }
    });
}
