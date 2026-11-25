import { request } from "./api.js";
import "./nav.js";
import { openModal, renderPagination, renderTableSkeleton } from "./ui.js";

const rows = document.getElementById("history-rows");
const message = document.getElementById("history-message");
const pagination = document.getElementById("history-pagination");
const detailTemplate = document.getElementById("submission-detail-template");
const dateFormat = new Intl.DateTimeFormat("zh-CN", { dateStyle: "short", timeStyle: "medium" });

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
    pagination.setAttribute("aria-busy", "true");
    message.textContent = "正在加载提交记录…";
    renderTableSkeleton(rows, 6);

    try {
        const history = await request("/api/submissions?" + new URLSearchParams({ page: target }));
        rows.replaceChildren();
        history.items.forEach(renderRow);
        renderPagination(pagination, history, loadPage);
        message.textContent = history.total ? `共 ${history.total} 条提交` : "暂无提交记录。";
    } catch (error) {
        rows.replaceChildren();
        pagination.hidden = true;
        message.textContent = error.message;
    } finally {
        pagination.removeAttribute("aria-busy");
    }
}

async function showDetail(id) {
    const controller = new AbortController();
    const { signal } = controller;
    const content = detailTemplate.content.firstElementChild.cloneNode(true);
    const message = content.querySelector(".detail-message");
    const dialog = openModal("提交 #" + id, content);
    dialog.addEventListener("close", () => controller.abort(), { once: true });

    try {
        const item = await request("/api/submissions/" + id, { signal });
        signal.throwIfAborted();
        const elapsed = item.timeUsedMs === null ? "" : " · " + item.timeUsedMs + " ms";
        content.querySelector(".detail-status").replaceChildren(badge(item.status));
        content.querySelector(".detail-time").textContent = elapsed;
        content.querySelector(".detail-code").textContent = item.code;
        content.querySelector(".detail-error").textContent = item.errorMessage ?? "";
        content.querySelector(".detail-error-section").hidden = !item.errorMessage;
        message.hidden = true;
        content.querySelector(".detail-content").hidden = false;
    } catch (error) {
        if (!signal.aborted) {
            message.textContent = error.message;
        }
    }
}

loadPage(1);
