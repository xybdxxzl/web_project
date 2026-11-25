import { request } from "./api.js";
import { launchConfetti } from "./confetti.js";
import "./nav.js";
import { initializeEditor } from "./problem-editor.js";
import { showToast } from "./ui.js";

const problemId = new URLSearchParams(window.location.search).get("id");
const layout = document.getElementById("problem-layout");
const message = document.getElementById("problem-message");
const form = document.getElementById("submission-form");
const submit = form.querySelector('button[type="submit"]');
const divider = document.getElementById("problem-divider");
const submissionMessage = document.getElementById("submission-message");
const progress = document.getElementById("judge-progress");
const progressCount = document.getElementById("judge-count");
const progressFill = document.getElementById("judge-fill");
const difficulties = { EASY: "简单", MEDIUM: "中等", HARD: "困难" };
const verdicts = {
    PENDING: "等待判题", JUDGING: "正在判题", AC: "通过", WA: "答案错误", TLE: "运行超时",
    MLE: "内存超限", RE: "运行错误", CE: "编译错误", SE: "系统错误"
};

let split = 50;
let drag;
function setSplit(value) {
    split = Math.min(75, Math.max(25, value));
    layout.style.setProperty("--statement-width", split + "fr");
    layout.style.setProperty("--editor-width", (100 - split) + "fr");
    divider.setAttribute("aria-valuenow", String(Math.round(split)));
}

divider.addEventListener("pointerdown", event => {
    if (event.button !== 0 || !event.isPrimary) {
        return;
    }
    const gap = parseFloat(getComputedStyle(layout).columnGap);
    const width = layout.clientWidth - divider.offsetWidth - 2 * gap;
    drag = { pointerId: event.pointerId, x: event.clientX, split, width };
    divider.setPointerCapture(event.pointerId);
    document.body.classList.add("dragging-split");
});
divider.addEventListener("pointermove", event => {
    if (drag && drag.pointerId === event.pointerId) {
        setSplit(drag.split + 100 * (event.clientX - drag.x) / drag.width);
    }
});
divider.addEventListener("lostpointercapture", () => {
    drag = undefined;
    document.body.classList.remove("dragging-split");
});
divider.addEventListener("dblclick", () => setSplit(50));
divider.addEventListener("keydown", event => {
    const widths = { ArrowLeft: split - 5, ArrowRight: split + 5, Home: 25, End: 75, Enter: 50 };
    if (Object.hasOwn(widths, event.key)) {
        event.preventDefault();
        setSplit(widths[event.key]);
    }
});

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
            showToast("已复制" + label, "success");
        } catch {
            showToast("无法自动复制，请选中文本手动复制。", "error");
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
        initializeEditor(form, problemId);
        layout.hidden = false;
        message.textContent = "";
    } catch (error) {
        message.textContent = error.message;
    }
}

function renderProgress({ status, passedCases, totalCases }) {
    const count = passedCases + " / " + totalCases;
    if (progressCount.textContent !== count) {
        progressCount.textContent = count;
    }
    progress.dataset.status = status;
    progressFill.style.width = (totalCases ? 100 * passedCases / totalCases : 0) + "%";
    progress.hidden = false;
}

function isJudging(status) {
    return status === "PENDING" || status === "JUDGING";
}

async function pollSubmission(id) {
    const prefix = "提交 #" + id;
    let result = { status: "PENDING", passedCases: 0, totalCases: 0 };
    renderProgress(result);
    submissionMessage.classList.add("judging");
    submissionMessage.textContent = prefix + " · " + verdicts[result.status];
    while (isJudging(result.status)) {
        await new Promise(resolve => window.setTimeout(resolve, 1000));
        const previous = result.status;
        result = await request("/api/submissions/" + id);
        renderProgress(result);
        if (isJudging(result.status) && result.status !== previous) {
            submissionMessage.textContent = prefix + " · " + verdicts[result.status];
        }
    }

    const lines = [prefix + " · " + verdicts[result.status] + " (" + result.status + ")"];
    if (result.timeUsedMs !== null) {
        lines.push("最大运行耗时 " + result.timeUsedMs + " ms");
    }
    if (result.errorMessage) {
        lines.push(result.errorMessage);
    }
    submissionMessage.textContent = lines.join("\n");
    if (result.status === "AC") {
        launchConfetti();
    }
}

form.addEventListener("submit", async event => {
    event.preventDefault();
    if (submit.disabled) {
        return;
    }
    submit.disabled = true;
    progress.hidden = true;
    submissionMessage.textContent = "正在提交…";

    let submissionId;
    try {
        const data = await request(form.action, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                problemId: Number(problemId),
                language: form.elements.language.value,
                code: form.elements.code.value
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
