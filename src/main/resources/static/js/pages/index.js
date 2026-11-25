import { request } from "../api.js";
import "../nav.js";
import { formatRate, renderTableSkeleton, skeleton } from "../ui.js";

const metrics = document.getElementById("site-metrics");
const values = metrics.querySelectorAll("[data-metric]");
const rows = document.getElementById("recent-rows");
const status = document.getElementById("recent-status");
const numberFormat = new Intl.NumberFormat("zh-CN");
const relativeFormat = new Intl.RelativeTimeFormat("zh-CN");
const absoluteFormat = new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" });
const timeUnits = [["year", 31536000], ["month", 2592000], ["day", 86400], ["hour", 3600], ["minute", 60]];
const languages = { JAVA: "Java", CPP: "C++" };

function easeOutCubic(progress) {
    return 1 - (1 - progress) ** 3;
}

function animateMetrics(stats) {
    function paint(fraction) {
        for (const node of values) {
            const value = stats[node.dataset.metric] * fraction;
            node.textContent = node.dataset.metric === "acRate" ? formatRate(value) : numberFormat.format(Math.round(value));
        }
    }

    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        paint(1);
        return;
    }
    let started;
    paint(0);
    function frame(now) {
        started ??= now;
        const elapsed = Math.min(1, (now - started) / 800);
        paint(easeOutCubic(elapsed));
        if (elapsed < 1) {
            window.requestAnimationFrame(frame);
        }
    }
    window.requestAnimationFrame(frame);
}

function relativeTime(date) {
    const seconds = (date.getTime() - Date.now()) / 1000;
    for (const [unit, length] of timeUnits) {
        if (Math.abs(seconds) >= length) {
            return relativeFormat.format(Math.trunc(seconds / length), unit);
        }
    }
    return "刚刚";
}

function renderSubmission(item) {
    const row = rows.insertRow();
    row.insertCell().textContent = item.username;
    const problemCell = row.insertCell();
    if (item.problemTitle === null) {
        problemCell.textContent = "题目 #" + item.problemId + "（已删除）";
    } else {
        const link = document.createElement("a");
        link.href = "/problem.html?id=" + item.problemId;
        link.textContent = item.problemTitle;
        problemCell.append(link);
    }

    const language = document.createElement("span");
    language.className = "language-chip";
    language.textContent = languages[item.language];
    row.insertCell().append(language);
    const badge = document.createElement("span");
    badge.className = "status-badge";
    badge.dataset.status = item.status;
    badge.textContent = item.status;
    row.insertCell().append(badge);

    const date = new Date(item.createdAt);
    const time = document.createElement("time");
    time.dateTime = item.createdAt;
    time.title = absoluteFormat.format(date);
    time.textContent = relativeTime(date);
    row.insertCell().append(time);
}

async function loadDashboard() {
    status.textContent = "正在加载全站统计…";
    for (const node of values) {
        node.replaceChildren(skeleton());
    }
    renderTableSkeleton(rows, 5);
    try {
        const stats = await request("/api/stats");
        animateMetrics(stats);
        rows.replaceChildren();
        stats.recentSubmissions.forEach(renderSubmission);
        status.textContent = stats.recentSubmissions.length ? "" : "暂无提交。";
    } catch (error) {
        for (const node of values) {
            node.textContent = "—";
        }
        rows.replaceChildren();
        status.textContent = error.message;
    } finally {
        metrics.removeAttribute("aria-busy");
    }
}

loadDashboard();
