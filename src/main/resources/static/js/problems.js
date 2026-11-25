import { request } from "./api.js";
import "./nav.js";
import { formatRate, renderPagination, renderTableSkeleton } from "./ui.js";

const filters = document.getElementById("problem-filters");
const rows = document.getElementById("problem-rows");
const message = document.getElementById("list-message");
const pagination = document.getElementById("problem-pagination");
const searchButton = filters.querySelector('button[type="submit"]');
const difficulties = { EASY: "简单", MEDIUM: "中等", HARD: "困难" };
const states = {
    AC: ["✓", "已通过"],
    TRIED: ["◔", "已尝试"],
    NONE: ["○", "未尝试"]
};

let criteria = new URLSearchParams();

function stateIcon(status) {
    const [symbol, label] = states[status];
    const icon = document.createElement("span");
    icon.className = "status-badge problem-state";
    icon.dataset.status = status;
    icon.setAttribute("role", "img");
    icon.setAttribute("aria-label", label);
    icon.title = label;
    icon.textContent = symbol;
    return icon;
}

async function loadPage(target) {
    pagination.setAttribute("aria-busy", "true");
    searchButton.disabled = true;
    message.textContent = "正在加载题库…";
    renderTableSkeleton(rows, 7);
    criteria.set("page", target);

    try {
        const data = await request("/api/problems?" + criteria);
        rows.replaceChildren();
        for (const problem of data.items) {
            const row = rows.insertRow();
            const title = document.createElement("a");
            title.href = "/problem.html?id=" + problem.id;
            title.textContent = problem.title;

            row.insertCell().append(stateIcon(problem.userStatus));
            row.insertCell().textContent = problem.id;
            row.insertCell().append(title);
            row.insertCell().textContent = difficulties[problem.difficulty];
            row.insertCell().textContent = problem.timeLimitMs + " ms";
            row.insertCell().textContent = problem.memoryLimitMb + " MB";
            const rate = row.insertCell();
            rate.className = "numeric";
            rate.textContent = problem.submitCount ? formatRate(problem.acCount / problem.submitCount) : "—";
            rate.title = `${problem.acCount} 次通过，共 ${problem.submitCount} 次提交`;
        }

        renderPagination(pagination, data, loadPage);
        message.textContent = data.items.length ? `共 ${data.total} 题` : "当前筛选没有题目。";
    } catch (error) {
        rows.replaceChildren();
        pagination.hidden = true;
        message.textContent = error.message;
    } finally {
        pagination.removeAttribute("aria-busy");
        searchButton.disabled = false;
    }
}

filters.addEventListener("submit", event => {
    event.preventDefault();
    criteria = new URLSearchParams(new FormData(filters));
    loadPage(1);
});
loadPage(1);
