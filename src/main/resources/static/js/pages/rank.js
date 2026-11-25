import { request } from "../api.js";
import { currentUser } from "../nav.js";
import { formatRate, renderPagination, renderTableSkeleton } from "../ui.js";

const rows = document.getElementById("ranking-rows");
const podium = document.getElementById("ranking-podium");
const cardTemplate = document.getElementById("podium-card");
const status = document.getElementById("ranking-status");
const pagination = document.getElementById("ranking-pagination");
const medals = ["金牌", "银牌", "铜牌"];

function renderPodium(entries) {
    const cards = entries.slice(0, 3).map((entry, index) => {
        const card = cardTemplate.content.firstElementChild.cloneNode(true);
        card.dataset.rank = entry.rank;
        card.querySelector(".podium-medal").textContent = "#" + entry.rank + " " + medals[index];
        card.querySelector(".podium-username").textContent = entry.username;
        card.querySelector(".podium-solved").textContent = entry.acProblemCount;
        card.querySelector(".podium-rate").textContent = formatRate(entry.acRate);
        return card;
    });
    podium.replaceChildren(...cards);
}

function renderEntry(entry, viewerId) {
    const row = rows.insertRow();
    row.insertCell().textContent = entry.rank;
    const user = row.insertCell();
    const name = document.createElement("span");
    name.textContent = entry.username;
    user.append(name);
    if (entry.userId === viewerId) {
        row.className = "ranking-viewer";
        const badge = document.createElement("span");
        badge.className = "viewer-badge";
        badge.textContent = "我";
        user.append(badge);
    }
    row.insertCell().textContent = entry.acProblemCount;
    row.insertCell().textContent = entry.totalSubmissions;
    row.insertCell().textContent = formatRate(entry.acRate);
}

async function loadPage(page) {
    pagination.setAttribute("aria-busy", "true");
    status.textContent = "正在加载排行榜…";
    renderTableSkeleton(rows, 5);
    try {
        const [result, viewer] = await Promise.all([
            request("/api/ranking?" + new URLSearchParams({ page })),
            currentUser
        ]);
        if (page === 1) {
            renderPodium(result.items);
        }
        rows.replaceChildren();
        result.items.forEach(entry => renderEntry(entry, viewer?.id));
        renderPagination(pagination, result, loadPage);
        status.textContent = result.total ? `共 ${result.total} 名用户` : "暂无排名。";
    } catch (error) {
        rows.replaceChildren();
        pagination.hidden = true;
        status.textContent = error.message;
    } finally {
        pagination.removeAttribute("aria-busy");
    }
}

loadPage(1);
