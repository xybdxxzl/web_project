import { request } from "./api.js";
import "./nav.js";

const problemId = new URLSearchParams(window.location.search).get("id");
const layout = document.getElementById("problem-layout");
const message = document.getElementById("problem-message");
const copyMessage = document.getElementById("copy-message");
const editor = document.getElementById("code-editor");
const form = document.getElementById("submission-form");
const submit = form.querySelector('button[type="submit"]');
const language = form.elements.language;
const draftPrefix = "mini-oj:draft:" + problemId + ":";
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
const lastLanguage = sessionStorage.getItem(draftPrefix + "language");
let activeLanguage = Object.hasOwn(templates, lastLanguage) ? lastLanguage : language.value;
language.value = activeLanguage;

function loadDraft() {
    editor.value = sessionStorage.getItem(draftPrefix + activeLanguage) ?? templates[activeLanguage];
}

function saveDraft() {
    sessionStorage.setItem(draftPrefix + activeLanguage, editor.value);
}

loadDraft();
editor.addEventListener("input", saveDraft);
language.addEventListener("change", () => {
    saveDraft();
    activeLanguage = language.value;
    sessionStorage.setItem(draftPrefix + "language", activeLanguage);
    loadDraft();
});
const submissionMessage = document.getElementById("submission-message");
const difficulties = { EASY: "简单", MEDIUM: "中等", HARD: "困难" };
const verdicts = {
    AC: "通过", WA: "答案错误", TLE: "运行超时",
    MLE: "内存超限", RE: "运行错误", CE: "编译错误", SE: "系统错误"
};

async function copyText(text, button) {
    if (navigator.clipboard) {
        await navigator.clipboard.writeText(text);
        return;
    }

    // 局域网 HTTP 页面可能没有 Clipboard API，临时文本框用于兼容复制。
    const field = document.createElement("textarea");
    field.value = text;
    field.className = "clipboard-buffer";
    document.body.append(field);
    field.select();
    try {
        if (!document.execCommand("copy")) {
            throw new Error("复制失败");
        }
    } finally {
        field.remove();
        button.focus();
    }
}

function sampleBlock(label, text) {
    const block = document.createElement("div");
    const header = document.createElement("div");
    header.className = "sample-header";
    const heading = document.createElement("h3");
    heading.textContent = label;
    const copyButton = document.createElement("button");
    copyButton.type = "button";
    copyButton.textContent = "复制";
    copyButton.setAttribute("aria-label", "复制" + label);
    const content = document.createElement("pre");
    content.className = "plain-text sample-text";
    content.textContent = text;

    copyButton.addEventListener("click", async () => {
        try {
            await copyText(text, copyButton);
            copyMessage.textContent = "已复制" + label;
        } catch {
            copyMessage.textContent = "无法自动复制，请选中文本手动复制。";
        }
    });

    header.append(heading, copyButton);
    block.append(header, content);
    return block;
}

async function loadProblem() {
    if (!/^[1-9]\d*$/.test(problemId ?? "")) {
        message.textContent = "题目编号无效。";
        return;
    }

    try {
        const problem = await request("/api/problems/" + problemId);
        document.title = problem.title + " · Mini OJ";
        document.getElementById("problem-title").textContent = problem.title;
        document.getElementById("problem-description").textContent = problem.description;
        document.getElementById("problem-limits").textContent =
            `${difficulties[problem.difficulty]} · ${problem.timeLimitMs} ms · ${problem.memoryLimitMb} MB`;
        const samples = document.getElementById("problem-samples");

        for (const [index, sample] of problem.testCases.entries()) {
            const group = document.createElement("div");
            group.className = "sample-group";
            const number = index + 1;
            group.append(sampleBlock(`样例 ${number} 输入`, sample.input),
                sampleBlock(`样例 ${number} 输出`, sample.expectedOutput));
            samples.append(group);
        }
        document.getElementById("samples-section").hidden = problem.testCases.length === 0;
        layout.hidden = false;
        message.textContent = "";
    } catch (error) {
        message.textContent = error.message;
    }
}

editor.addEventListener("keydown", event => {
    if (event.key !== "Tab" || event.shiftKey || event.ctrlKey || event.altKey || event.metaKey) {
        return;
    }
    event.preventDefault();
    editor.setRangeText("    ", editor.selectionStart, editor.selectionEnd, "end");
    saveDraft();
});

async function pollSubmission(id) {
    const prefix = "提交 #" + id;
    submissionMessage.classList.add("judging");
    submissionMessage.textContent = prefix + " · Judging...";
    while (true) {
        await new Promise(resolve => window.setTimeout(resolve, 1000));
        const result = await request("/api/submissions/" + id);
        if (result.status === "PENDING" || result.status === "JUDGING") {
            continue;
        }
        const lines = [prefix + " · " + verdicts[result.status] + " (" + result.status + ")"];
        if (result.timeUsedMs !== null) {
            lines.push("最大运行耗时 " + result.timeUsedMs + " ms");
        }
        if (result.errorMessage) {
            lines.push(result.errorMessage);
        }
        submissionMessage.textContent = lines.join("\n");
        return;
    }
}

form.addEventListener("submit", async event => {
    event.preventDefault();
    saveDraft();
    submit.disabled = true;
    submissionMessage.textContent = "";

    let submissionId;
    try {
        const data = await request(form.action, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                problemId: Number(problemId),
                language: form.elements.language.value,
                code: editor.value
            })
        });
        submissionId = data.submissionId;
        await pollSubmission(submissionId);
    } catch (error) {
        submissionMessage.textContent = submissionId === undefined
            ? error.message
            : "提交 #" + submissionId + " 已保存，暂时无法获取结果。\n" + error.message;
    } finally {
        submissionMessage.classList.remove("judging");
        submit.disabled = false;
    }
});

loadProblem();
