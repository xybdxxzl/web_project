import { request } from "./api.js";
import "./nav.js";

const filters = document.getElementById("problem-filters");
const rows = document.getElementById("problem-rows");
const message = document.getElementById("list-message");
const previous = document.getElementById("previous-page");
const next = document.getElementById("next-page");
const pageLabel = document.getElementById("page-label");
const searchButton = filters.querySelector('button[type="submit"]');
const difficulties = { EASY: "简单", MEDIUM: "中等", HARD: "困难" };

let criteria = new URLSearchParams();
let page = 1;

async function loadPage(target) {
    previous.disabled = true;
    next.disabled = true;
    searchButton.disabled = true;
    message.textContent = "正在加载题库…";
    criteria.set("page", target);

    try {
        const data = await request("/api/problems?" + criteria);
        rows.replaceChildren();
        for (const problem of data.items) {
            const row = rows.insertRow();
            const title = document.createElement("a");
            title.href = "/problem.html?id=" + problem.id;
            title.textContent = problem.title;

            row.insertCell().textContent = problem.id;
            row.insertCell().append(title);
            row.insertCell().textContent = difficulties[problem.difficulty];
            row.insertCell().textContent = problem.timeLimitMs + " ms";
            row.insertCell().textContent = problem.memoryLimitMb + " MB";
        }

        page = data.page;
        previous.disabled = page <= 1;
        next.disabled = page >= data.totalPages;
        pageLabel.textContent = data.total
            ? `第 ${page} / ${data.totalPages} 页，共 ${data.total} 题`
            : "";
        message.textContent = data.items.length ? "" : "当前筛选没有题目。";
    } catch (error) {
        rows.replaceChildren();
        pageLabel.textContent = "";
        message.textContent = error.message;
    } finally {
        searchButton.disabled = false;
    }
}

filters.addEventListener("submit", event => {
    event.preventDefault();
    criteria = new URLSearchParams(new FormData(filters));
    loadPage(1);
});
previous.addEventListener("click", () => loadPage(page - 1));
next.addEventListener("click", () => loadPage(page + 1));
loadPage(1);
