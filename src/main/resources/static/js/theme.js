const root = document.documentElement;
const preference = window.matchMedia("(prefers-color-scheme: dark)");
const storageKey = "mini-oj:theme";
let selectedTheme;

try {
    selectedTheme = localStorage.getItem(storageKey);
} catch {
    // 浏览器可能限制站点存储，当前页面仍可切换主题。
}

function applyTheme(theme) {
    if (root.dataset.theme === theme) {
        return;
    }
    root.dataset.theme = theme;
    window.dispatchEvent(new Event("themechange"));
}

function followSystem() {
    applyTheme(preference.matches ? "dark" : "light");
}

if (selectedTheme === "dark" || selectedTheme === "light") {
    applyTheme(selectedTheme);
} else {
    followSystem();
}

preference.addEventListener("change", () => {
    if (selectedTheme !== "dark" && selectedTheme !== "light") {
        followSystem();
    }
});

export function toggleTheme() {
    selectedTheme = root.dataset.theme === "dark" ? "light" : "dark";
    try {
        localStorage.setItem(storageKey, selectedTheme);
    } catch {
        // 存储受限时保留本次切换。
    }
    applyTheme(selectedTheme);
}
