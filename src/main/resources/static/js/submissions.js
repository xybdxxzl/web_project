import { request } from "./api.js";
import "./nav.js";

const rows = document.getElementById("history-rows");
const message = document.getElementById("history-message");
const previous = document.getElementById("history-previous");
const next = document.getElementById("history-next");
const pageLabel = document.getElementById("history-page");
const dialog = document.getElementById("submission-detail");
const detailContent = document.getElementById("detail-content");
const detailMessage = document.getElementById("detail-message");
const errorSection = document.getElementById("detail-error-section");
const dateFormat = new Intl.DateTimeFormat("zh-CN", { dateStyle: "short", timeStyle: "medium" });
let page = 1;
let totalPages = 0;
let detailRequest;

function badge(status) {
    const element = document.createElement("span");
    element.className = "status-badge";
    element.dataset.status = status;
    element.textContent = status;
    return element;
}

function renderRow(item) {
    const row = rows.insertRow();
    const detail = document.createElement("button");
    detail.type = "button";
    detail.textContent = "#" + item.submissionId;
    detail.addEventListener("click", () => showDetail(item.submissionId));
    row.insertCell().append(detail);

    const problemCell = row.insertCell();
    if (item.problemTitle === null) {
        problemCell.textContent = "题目 #" + item.problemId + "（已删除）";
    } else {
        const problem = document.createElement("a");
        problem.href = "/problem.html?id=" + item.problemId;
        problem.textContent = item.problemTitle;
        problemCell.append(problem);
    }

    row.insertCell().textContent = item.language;
    row.insertCell().append(badge(item.status));
    row.insertCell().textContent = item.timeUsedMs === null ? "—" : item.timeUsedMs + " ms";
    row.insertCell().textContent = dateFormat.format(new Date(item.createdAt));
}

async function loadPage(target) {
    previous.disabled = true;
    next.disabled = true;
    message.textContent = "正在加载提交记录…";

    try {
        const history = await request("/api/submissions?" + new URLSearchParams({ page: target }));
        page = history.page;
        totalPages = history.totalPages;
        rows.replaceChildren();
        history.items.forEach(renderRow);
        pageLabel.textContent = history.total
            ? `第 ${page} / ${history.totalPages} 页，共 ${history.total} 条`
            : "";
        message.textContent = history.total ? "" : "暂无提交记录。";
    } catch (error) {
        message.textContent = error.message;
    } finally {
        previous.disabled = page <= 1;
        next.disabled = page >= totalPages;
    }
}

async function showDetail(id) {
    const controller = new AbortController();
    detailRequest = controller;
    const { signal } = controller;
    detailContent.hidden = true;
    detailMessage.textContent = "正在加载…";
    document.getElementById("submission-detail-title").textContent = "提交 #" + id;
    dialog.showModal();

    try {
        const item = await request("/api/submissions/" + id, { signal });
        signal.throwIfAborted();
        const elapsed = item.timeUsedMs === null ? "" : " · " + item.timeUsedMs + " ms";
        document.getElementById("detail-status").replaceChildren(badge(item.status), elapsed);
        document.getElementById("detail-code").textContent = item.code;
        document.getElementById("detail-error").textContent = item.errorMessage ?? "";
        errorSection.hidden = !item.errorMessage;
        detailMessage.textContent = "";
        detailContent.hidden = false;
    } catch (error) {
        if (!signal.aborted) {
            detailMessage.textContent = error.message;
        }
    }
}

dialog.addEventListener("close", () => detailRequest.abort());
previous.addEventListener("click", () => loadPage(page - 1));
next.addEventListener("click", () => loadPage(page + 1));
loadPage(1);
