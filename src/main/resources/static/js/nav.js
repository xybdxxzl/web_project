import { loginUrl, request } from "./api.js";

const nav = document.getElementById("top-nav");
const actions = document.createElement("div");
actions.className = "nav-actions";

function link(label, path) {
    const element = document.createElement("a");
    element.href = path;
    element.textContent = label;
    return element;
}

const siteLinks = document.createElement("div");
siteLinks.className = "site-links";
siteLinks.append(link("Mini OJ", "/"), link("题库", "/problems.html"));
nav.append(siteLinks, actions);

function showGuest() {
    actions.replaceChildren(link("登录", loginUrl()), link("注册", "/register.html"));
}

function showUser(user) {
    actions.append(link("我的提交", "/submissions.html"));
    const username = document.createElement("span");
    username.textContent = user.username;
    actions.append(username);

    if (user.role === "ADMIN") {
        actions.append(link("管理", "/admin.html"));
    }

    const logout = document.createElement("button");
    logout.type = "button";
    logout.textContent = "退出";
    actions.append(logout);

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
            status.textContent = error.message;
            logout.disabled = false;
        }
    });
}

const status = document.createElement("p");
status.className = "nav-status";
status.setAttribute("role", "status");
nav.after(status);

export const currentUser = request("/api/auth/me", { redirectOnUnauthorized: false })
    .then(user => {
        showUser(user);
        return user;
    })
    .catch(error => {
        if (error.status === 401) {
            showGuest();
            return null;
        }
        status.textContent = error.message;
        return null;
    });
