import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import { test } from "node:test";
import { runInNewContext } from "node:vm";

import { loginUrl, postLoginUrl, request } from "../../main/resources/static/js/api.js";
import { launchConfetti } from "../../main/resources/static/js/confetti.js";
import { indentSelection } from "../../main/resources/static/js/problem-editor.js";
import { formatRate } from "../../main/resources/static/js/ui.js";

const staticRoot = new URL("../../main/resources/static/", import.meta.url);
const themeModule = new URL("js/theme.js", staticRoot);
const authorizationError = () => new Response(JSON.stringify({ code: 401, message: "请先登录" }), {
    status: 401,
    headers: { "Content-Type": "application/json" }
});

function installGlobals(t, globals) {
    for (const [name, value] of Object.entries(globals)) {
        const original = Object.getOwnPropertyDescriptor(globalThis, name);
        Object.defineProperty(globalThis, name, { value, configurable: true });
        t.after(() => {
            if (original) {
                Object.defineProperty(globalThis, name, original);
            } else {
                delete globalThis[name];
            }
        });
    }
}

test("every HTML page applies the saved or system theme before loading CSS", async () => {
    const pages = (await readdir(staticRoot)).filter(path => path.endsWith(".html"));
    for (const name of pages) {
        const html = await readFile(new URL(name, staticRoot), "utf8");
        const head = html.split("</head>")[0];
        const [, script] = head.match(/<script>([\s\S]*?)<\/script>/);
        assert.ok(head.indexOf(script) < head.indexOf('rel="stylesheet"'), name);
        for (const [saved, systemDark, expected] of [
            ["light", true, "light"], ["dark", false, "dark"], [null, true, "dark"], ["invalid", false, "light"]
        ]) {
            const document = { documentElement: { dataset: {} } };
            runInNewContext(script, {
                document,
                localStorage: { getItem: key => {
                    assert.equal(key, "mini-oj:theme");
                    return saved;
                } },
                window: { matchMedia: () => ({ matches: systemDark }) }
            });
            assert.equal(document.documentElement.dataset.theme, expected, name);
        }
    }
});

test("theme changes emit an event, follow system preferences and persist manual choice", async t => {
    const preference = new EventTarget();
    preference.matches = false;
    const window = new EventTarget();
    window.matchMedia = query => {
        assert.equal(query, "(prefers-color-scheme: dark)");
        return preference;
    };
    const dataset = { theme: "light" };
    const storage = new Map();
    const events = [];
    window.addEventListener("themechange", () => events.push(dataset.theme));

    installGlobals(t, {
        window,
        document: { documentElement: { dataset } },
        localStorage: {
            getItem: key => storage.get(key),
            setItem: (key, value) => storage.set(key, value)
        }
    });

    const { toggleTheme } = await import(themeModule.href);
    preference.matches = true;
    preference.dispatchEvent(new Event("change"));
    toggleTheme();
    preference.dispatchEvent(new Event("change"));

    assert.deepEqual(events, ["dark", "light"]);
    assert.equal(dataset.theme, "light");
    assert.equal(storage.get("mini-oj:theme"), "light");
});

test("401 preserves the protected page and the session probe avoids redirecting", async t => {
    const redirects = [];
    const location = {
        origin: "http://localhost",
        pathname: "/submissions.html",
        search: "?page=2",
        hash: "#history",
        assign: url => redirects.push(url)
    };
    installGlobals(t, { window: { location } });
    t.mock.method(globalThis, "fetch", async () => authorizationError());

    assert.equal(new URL(loginUrl(), location.origin).searchParams.get("redirect"),
        "/submissions.html?page=2#history");
    await assert.rejects(request("/api/auth/me", { redirectOnUnauthorized: false }), { status: 401 });
    assert.deepEqual(redirects, []);
    await assert.rejects(request("/api/submissions"), { status: 401 });
    assert.deepEqual(redirects, [loginUrl()]);

    location.search = new URL(loginUrl(), location.origin).search;
    assert.equal(postLoginUrl(), "/submissions.html?page=2#history");
    location.search = "?redirect=%2F%2Fevil.example";
    assert.equal(postLoginUrl(), "/problems.html");
});

test("acceptance rates use percent formatting with one fractional digit", () => {
    assert.equal(formatRate(0), "0%");
    assert.equal(formatRate(2 / 3), "66.7%");
});

test("Tab inserts four spaces at the caret and Shift+Tab preserves the caret within its line", () => {
    assert.deepEqual(indentSelection("abc", 1, 1, false),
        { text: "    ", from: 1, to: 1, start: 5, end: 5 });
    assert.deepEqual(indentSelection("a\n    abc", 7, 7, true),
        { text: "abc", from: 2, to: 9, start: 3, end: 3 });
    assert.equal(indentSelection("abc", 2, 2, true), null);
});

test("multiline indentation covers selected lines and excludes a following line at the selection boundary", () => {
    assert.deepEqual(indentSelection("one\ntwo\nthree", 1, 8, false),
        { text: "    one\n    two", from: 0, to: 7, start: 0, end: 15 });
    assert.deepEqual(indentSelection("  one\n\ttwo\nthree", 0, 11, true),
        { text: "one\ntwo", from: 0, to: 10, start: 0, end: 7 });
    assert.deepEqual(indentSelection("\none", 0, 1, false),
        { text: "    ", from: 0, to: 0, start: 0, end: 4 });
});

test("confetti draws 100 particles and cancels the next frame when it removes the canvas", t => {
    const frames = new Map();
    let nextFrame = 0;
    let remove;
    let rectangles = 0;
    const transforms = [];
    const context = {
        setTransform: (...values) => transforms.push(values),
        clearRect() {}, save() {}, translate() {}, rotate() {}, restore() {},
        fillRect: () => { rectangles++; }
    };
    const canvas = {
        getContext: () => context,
        setAttribute() {},
        remove: t.mock.fn()
    };
    const window = new EventTarget();
    Object.assign(window, {
        innerWidth: 800,
        innerHeight: 600,
        devicePixelRatio: 3,
        matchMedia: () => ({ matches: false }),
        requestAnimationFrame: callback => {
            frames.set(++nextFrame, callback);
            return nextFrame;
        },
        cancelAnimationFrame: frame => frames.delete(frame),
        setTimeout: (callback, milliseconds) => {
            assert.equal(milliseconds, 2200);
            remove = callback;
        }
    });
    const append = t.mock.fn();
    installGlobals(t, { window, document: { createElement: () => canvas, body: { append } } });

    launchConfetti();
    assert.equal(append.mock.calls[0].arguments[0], canvas);
    assert.equal(canvas.width, 1600);
    assert.equal(canvas.height, 1200);
    assert.deepEqual(transforms[0], [2, 0, 0, 2, 0, 0]);
    const draw = frames.get(1);
    frames.delete(1);
    draw(0);
    assert.equal(rectangles, 100);
    assert.equal(frames.size, 1);
    remove();
    assert.equal(canvas.remove.mock.callCount(), 1);
    assert.equal(frames.size, 0);
});

test("confetti honors reduced motion before creating a canvas", t => {
    const createElement = t.mock.fn();
    installGlobals(t, { window: { matchMedia: () => ({ matches: true }) }, document: { createElement } });
    launchConfetti();
    assert.equal(createElement.mock.callCount(), 0);
});
