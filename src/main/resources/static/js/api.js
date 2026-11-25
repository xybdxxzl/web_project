export function loginUrl() {
    const { pathname, search, hash } = window.location;
    return "/login.html?" + new URLSearchParams({ redirect: pathname + search + hash });
}

export function postLoginUrl() {
    const fallback = "/problems.html";
    const target = new URLSearchParams(window.location.search).get("redirect") ?? "";
    if (!/^\/(?!\/|%2f|%5c|[a-z][a-z\d+.-]*:)/i.test(target) || /[\\\x00-\x20\x7f]/.test(target)) {
        return fallback;
    }
    const url = new URL(target, window.location.origin);
    if (url.origin !== window.location.origin || ["/login.html", "/register.html"].includes(url.pathname)) {
        return fallback;
    }
    return target;
}

export async function request(url, { redirectOnUnauthorized = true, ...options } = {}) {
    const response = await fetch(url, options).catch(() => {
        throw new Error("无法连接服务器，请稍后重试");
    });
    if (response.status === 401 && redirectOnUnauthorized) {
        window.location.assign(loginUrl());
    }

    const result = await response.json().catch(() => {
        throw new Error("服务器返回了无法读取的响应");
    });
    if (!response.ok || result.code !== 0) {
        const error = new Error(result.message);
        error.status = response.status;
        throw error;
    }
    return result.data;
}
