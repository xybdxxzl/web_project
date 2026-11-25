import assert from "node:assert/strict";
import { test } from "node:test";
import { renderDifficultyChart, renderHeatCalendar, renderTrendChart } from "../../main/resources/static/js/charts.js";

function harness(t, width, height, reduced = false) {
    const frames = [];
    const calls = [];
    let theme = "light";
    const context = {};
    for (const method of ["scale", "clearRect", "fillText", "beginPath", "moveTo", "lineTo", "arc",
        "stroke", "closePath", "fill", "setLineDash", "fillRect", "strokeRect"]) {
        context[method] = (...args) => {
            assert.ok(args.every(arg => typeof arg !== "number" || Number.isFinite(arg)));
            calls.push({ method, args, fill: context.fillStyle, stroke: context.strokeStyle });
        };
    }
    context.createLinearGradient = () => ({ addColorStop() {} });
    const tooltip = { style: {}, offsetWidth: 125, offsetHeight: 50, setAttribute() {} };
    const bounds = () => ({ left: 12, top: 20, right: width + 12, width, height });
    const viewport = Object.assign(new EventTarget(), { getBoundingClientRect: bounds });
    const canvas = Object.assign(new EventTarget(), {
        getBoundingClientRect: bounds,
        getContext: () => context,
        parentElement: { parentElement: viewport, append: node => assert.equal(node, tooltip) }
    });
    const window = Object.assign(new EventTarget(), {
        devicePixelRatio: 2,
        innerWidth: width + 24,
        matchMedia: () => ({ matches: reduced }),
        requestAnimationFrame: callback => frames.push(callback)
    });
    const globals = {
        window,
        document: { createElement: () => tooltip },
        getComputedStyle: () => ({ getPropertyValue: key => theme + key })
    };
    for (const [name, value] of Object.entries(globals)) {
        const previous = Object.getOwnPropertyDescriptor(globalThis, name);
        Object.defineProperty(globalThis, name, { value, configurable: true });
        t.after(() => previous ? Object.defineProperty(globalThis, name, previous) : delete globalThis[name]);
    }
    return {
        canvas, calls, tooltip, window,
        paint: now => frames.shift()(now),
        dark: () => { theme = "dark"; window.dispatchEvent(new Event("themechange")); },
        move: (x, y) => canvas.dispatchEvent(Object.assign(new Event("mousemove"), {
            clientX: x + 12, clientY: y + 20
        }))
    };
}

test("difficulty arcs animate, find hover by angle, and reread the palette and DPR", t => {
    const chart = harness(t, 300, 240);
    renderDifficultyChart(chart.canvas, [
        { label: "简单", solved: 1, total: 3 },
        { label: "中等", solved: 1, total: 2 },
        { label: "困难", solved: 0, total: 1 }
    ], 2);
    chart.paint(10);
    assert.equal(chart.calls.filter(call => call.method === "arc").length, 1);
    assert.equal(chart.canvas.width, 600);
    assert.equal(chart.canvas.height, 480);
    chart.paint(700);
    chart.move(150 + 76.8, 120);
    chart.paint(710);
    assert.equal(chart.tooltip.textContent, "简单\n已通过 1 / 3 题");
    chart.dark();
    chart.paint(720);
    assert.ok(chart.calls.some(call => call.stroke === "dark--chart-easy"));
    chart.window.devicePixelRatio = 3;
    chart.window.dispatchEvent(new Event("resize"));
    chart.paint(730);
    assert.equal(chart.canvas.width, 900);
    assert.deepEqual(chart.calls.filter(call => call.method === "scale").at(-1).args, [3, 3]);
    assert.equal(chart.tooltip.hidden, true);
});

test("trend snaps to the nearest date and safely draws zero submissions", t => {
    const chart = harness(t, 360, 260);
    renderTrendChart(chart.canvas, [
        { date: "2025-01-01", total: 0, ac: 0 },
        { date: "2025-01-02", total: 0, ac: 0 },
        { date: "2025-01-03", total: 0, ac: 0 }
    ]);
    chart.paint(0);
    assert.ok(chart.calls.some(call => call.method === "fillText" && call.args[0] === "近 30 天暂无提交"));
    chart.move(197, 80);
    chart.paint(16);
    assert.equal(chart.tooltip.textContent, "2025-01-02\n总提交 0 · AC 0");
    assert.ok(chart.calls.some(call => call.method === "setLineDash" && String(call.args[0]) === "4,4"));
    chart.canvas.dispatchEvent(new Event("mouseleave"));
    chart.paint(32);
    assert.equal(chart.tooltip.hidden, true);
});

test("heatmap fills the weekly matrix and maps Sunday and Monday to adjacent columns", t => {
    const chart = harness(t, 735, 198);
    renderHeatCalendar(chart.canvas, [
        { date: "2024-03-03", count: 0 },
        { date: "2024-03-04", count: 1 },
        { date: "2024-03-05", count: 3 },
        { date: "2024-03-06", count: 6 },
        { date: "2024-03-07", count: 7 }
    ]);
    chart.paint(0);
    const cells = chart.calls.filter(call => call.method === "fillRect");
    assert.equal(cells.length, 53 * 7 + 5);
    assert.deepEqual(cells.slice(6, 11).map(cell => cell.fill),
        Array.from({ length: 5 }, (_, index) => "light--heat-" + index));
    chart.move(cells[6].args[0] + 2, cells[6].args[1] + 2);
    chart.paint(16);
    assert.equal(chart.tooltip.textContent, "2024-03-03\n提交 0 次");
    chart.move(cells[7].args[0] + 2, cells[7].args[1] + 2);
    chart.paint(32);
    assert.equal(chart.tooltip.textContent, "2024-03-04\n提交 1 次");
    assert.ok(chart.calls.some(call => call.method === "strokeRect"));
    chart.move(cells[0].args[0] + 2, cells[0].args[1] + 2);
    chart.paint(48);
    assert.equal(chart.tooltip.hidden, true);
});

test("an empty difficulty ring displays the zero count and honors reduced motion", t => {
    const chart = harness(t, 300, 240, true);
    renderDifficultyChart(chart.canvas, [{ label: "简单", solved: 0, total: 3 }], 0);
    chart.paint(0);
    assert.ok(chart.calls.some(call => call.method === "fillText" && call.args[0] === 0));
    assert.ok(chart.calls.some(call => call.method === "fillText" && call.args[0] === "暂无难度分布"));
    assert.equal(chart.calls.filter(call => call.method === "arc").length, 1);
});

test("heatmap keeps the new month label when the first week starts in a partial month", t => {
    const chart = harness(t, 735, 198);
    renderHeatCalendar(chart.canvas, [
        { date: "2025-11-30", count: 0 },
        { date: "2025-12-01", count: 2 }
    ]);
    chart.paint(0);
    const labels = chart.calls.filter(call => call.method === "fillText").map(call => call.args[0]);
    assert.ok(labels.includes("12月"));
    assert.ok(!labels.includes("11月"));
});
