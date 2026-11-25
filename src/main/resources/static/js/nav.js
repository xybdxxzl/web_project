import { loginUrl, request } from "./api.js";
import { toggleTheme } from "./theme.js";
import { showToast } from "./ui.js";

const nav = document.getElementById("top-nav");
const mobile = window.matchMedia("(max-width: 768px)");
const { pathname } = window.location;

function link(label, path) {
    const element = document.createElement("a");
    element.href = path;
    element.textContent = label;
    if (path === pathname || path === "/index.html" && pathname === "/") {
        element.setAttribute("aria-current", "page");
    }
    return element;
}

const brand = link("Mini OJ", "/index.html");
brand.className = "site-brand";
brand.removeAttribute("aria-current");

const siteLinks = document.createElement("div");
siteLinks.id = "site-links";
siteLinks.className = "site-links";
siteLinks.append(link("首页", "/index.html"), link("题库", "/problems.html"),
    link("提交记录", "/submissions.html"), link("排行榜", "/rank.html"));

const menu = document.createElement("button");
menu.type = "button";
menu.className = "nav-menu-button";
menu.setAttribute("aria-controls", siteLinks.id);
menu.setAttribute("aria-label", "打开导航菜单");
menu.setAttribute("aria-expanded", "false");
const menuIcon = document.createElement("span");
menuIcon.className = "nav-menu-icon";
menuIcon.setAttribute("aria-hidden", "true");
menu.append(menuIcon);

const scrim = document.createElement("div");
scrim.className = "nav-scrim";
scrim.hidden = true;

function setMenuOpen(open) {
    nav.classList.toggle("menu-open", open);
    scrim.hidden = !open;
    menu.setAttribute("aria-expanded", String(open));
    menu.setAttribute("aria-label", open ? "关闭导航菜单" : "打开导航菜单");
}

menu.addEventListener("click", () => setMenuOpen(scrim.hidden));
scrim.addEventListener("click", () => {
    setMenuOpen(false);
    menu.focus();
});
siteLinks.addEventListener("click", event => {
    if (event.target.closest("a")) {
        setMenuOpen(false);
    }
});
document.addEventListener("keydown", event => {
    if (event.key === "Escape" && !scrim.hidden) {
        setMenuOpen(false);
        menu.focus();
    }
});
document.addEventListener("focusin", event => {
    if (!scrim.hidden && !nav.contains(event.target)) {
        setMenuOpen(false);
    }
});
mobile.addEventListener("change", () => setMenuOpen(false));

const actions = document.createElement("div");
actions.className = "nav-actions";
const theme = document.createElement("button");
theme.className = "theme-toggle";
theme.type = "button";
theme.textContent = "深色";
theme.setAttribute("aria-label", "深色主题");
function updateThemeButton() {
    theme.setAttribute("aria-pressed", String(document.documentElement.dataset.theme === "dark"));
}
updateThemeButton();
theme.addEventListener("click", toggleTheme);
window.addEventListener("themechange", updateThemeButton);

const account = document.createElement("div");
account.className = "nav-account";
actions.append(theme, account);
nav.append(brand, menu, siteLinks, actions);
nav.after(scrim);

function showGuest() {
    account.replaceChildren(link("登录", loginUrl()), link("注册", "/register.html"));
}

function showUser(user) {
    siteLinks.append(link("个人中心", "/profile.html"));
    if (user.role === "ADMIN") {
        siteLinks.append(link("管理后台", "/admin.html"));
    }

    const username = document.createElement("span");
    username.className = "nav-username";
    username.textContent = user.username;
    username.title = user.username;
    const logout = document.createElement("button");
    logout.type = "button";
    logout.textContent = "退出";
    account.replaceChildren(username, logout);

    logout.addEventListener("click", async () => {
        logout.disabled = true;
        try {
            await request("/api/auth/logout", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: "{}"
            });
            window.location.assign("/login.html");
        } catch (error) {
            showToast(error.message, "error");
            logout.disabled = false;
        }
    });
}

export const currentUser = request("/api/auth/me", { redirectOnUnauthorized: false })
    .then(user => {
        showUser(user);
        return user;
    })
    .catch(error => {
        showGuest();
        if (error.status !== 401) {
            showToast(error.message, "error");
        }
        return null;
    });
