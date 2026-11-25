import { request } from "../api.js";
import { renderDifficultyChart, renderTrendChart, renderHeatCalendar } from "../charts.js";
import "../nav.js";
import { skeleton } from "../ui.js";

const loading = document.getElementById("profile-loading");
const message = document.getElementById("profile-status");
const summary = document.getElementById("profile-summary");
const legend = document.querySelectorAll("#difficulty-legend li");
loading.append(...["40%", "70%", "55%"].map(skeleton));

async function loadProfile() {
    try {
        const stats = await request("/api/users/me/stats").finally(() => {
            loading.hidden = true;
        });
        document.getElementById("profile-username").textContent = stats.username;
        document.getElementById("profile-submissions").textContent = stats.totalSubmissions;
        document.getElementById("profile-solved").textContent = stats.acProblemCount;
        const difficulties = Array.from(legend, entry => {
            const { solved, total } = stats.difficultyStats[entry.dataset.difficulty];
            entry.querySelector("strong").textContent = solved + " / " + total;
            return { label: entry.children[1].textContent, solved, total };
        });
        message.hidden = true;
        summary.hidden = false;
        renderDifficultyChart(document.getElementById("difficulty-chart"), difficulties, stats.acProblemCount);
        renderTrendChart(document.getElementById("trend-chart"), stats.dailyTrend);
        renderHeatCalendar(document.getElementById("heat-chart"), stats.yearlyCalendar);
    } catch (error) {
        message.hidden = false;
        message.textContent = error.message;
    }
}

loadProfile();
