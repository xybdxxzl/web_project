const TAU = Math.PI * 2;
const DAYS_PER_WEEK = 7;
const CALENDAR_COLUMNS = 53;
const font = "system-ui, sans-serif";
const compactNumber = new Intl.NumberFormat("zh-CN", { notation: "compact", maximumFractionDigits: 1 });

function readPalette(canvas) {
    const styles = getComputedStyle(canvas);
    const color = name => styles.getPropertyValue("--" + name).trim();
    return {
        text: color("text"),
        muted: color("muted"),
        card: color("card-bg"),
        track: color("surface-accent"),
        primary: color("primary"),
        total: color("chart-total"),
        area: color("chart-area"),
        grid: color("chart-grid"),
        difficulties: ["easy", "medium", "hard"].map(level => color("chart-" + level)),
        heat: Array.from({ length: 5 }, (_, index) => color("heat-" + index))
    };
}

function mount(canvas, draw, duration = 0) {
    const context = canvas.getContext("2d");
    if (!context) {
        return;
    }
    const stage = canvas.parentElement;
    const tooltip = document.createElement("div");
    tooltip.className = "chart-tooltip";
    tooltip.setAttribute("role", "status");
    tooltip.hidden = true;
    stage.append(tooltip);

    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        duration = 0;
    }
    let width;
    let height;
    let colors;
    let point = null;
    let frame = null;
    let started;

    function showTooltip(text) {
        tooltip.hidden = !text;
        if (!text) {
            return;
        }
        if (tooltip.textContent !== text) {
            tooltip.textContent = text;
        }
        const canvasLeft = canvas.getBoundingClientRect().left;
        const viewport = stage.parentElement.getBoundingClientRect();
        const left = Math.max(0, Math.max(0, viewport.left) - canvasLeft);
        const right = Math.min(width, Math.min(window.innerWidth, viewport.right) - canvasLeft);
        const preferredX = point.x + 12 + tooltip.offsetWidth <= right
            ? point.x + 12 : point.x - tooltip.offsetWidth - 12;
        tooltip.style.left = Math.max(left + 4, Math.min(preferredX, right - tooltip.offsetWidth - 4)) + "px";
        tooltip.style.top = Math.max(4, Math.min(point.y + 10, height - tooltip.offsetHeight - 4)) + "px";
    }

    function queue() {
        if (frame === null) {
            frame = window.requestAnimationFrame(paint);
        }
    }

    function paint(now) {
        frame = null;
        started ??= now;
        const fraction = duration ? Math.min(1, (now - started) / duration) : 1;
        context.clearRect(0, 0, width, height);
        showTooltip(draw(context, { width, height, colors, point, progress: 1 - (1 - fraction) ** 3 }));
        if (fraction < 1) {
            queue();
        }
    }

    function themeChanged() {
        colors = readPalette(canvas);
        queue();
    }

    function resize() {
        const bounds = canvas.getBoundingClientRect();
        width = bounds.width;
        height = bounds.height;
        const dpr = window.devicePixelRatio;
        canvas.width = Math.round(width * dpr);
        canvas.height = Math.round(height * dpr);
        context.scale(dpr, dpr);
        point = null;
        themeChanged();
    }

    function move(event) {
        const bounds = canvas.getBoundingClientRect();
        point = {
            x: (event.clientX - bounds.left) * width / bounds.width,
            y: (event.clientY - bounds.top) * height / bounds.height
        };
        queue();
    }

    function clearHover() {
        point = null;
        queue();
    }

    canvas.addEventListener("mousemove", move);
    canvas.addEventListener("click", move);
    canvas.addEventListener("mouseleave", clearHover);
    stage.parentElement.addEventListener("scroll", clearHover, { passive: true });
    window.addEventListener("resize", resize);
    window.addEventListener("themechange", themeChanged);
    resize();
}

function label(context, text, x, y, color, size = 12, align = "center", weight = 400) {
    context.fillStyle = color;
    context.font = `${weight} ${size}px ${font}`;
    context.textAlign = align;
    context.textBaseline = "middle";
    context.fillText(text, x, y);
}

function ringArc(context, x, y, radius, from, to, color, thickness) {
    context.beginPath();
    context.strokeStyle = color;
    context.lineWidth = thickness;
    context.arc(x, y, radius, from - Math.PI / 2, to - Math.PI / 2);
    context.stroke();
}

export function renderDifficultyChart(canvas, difficulties, acceptedCount) {
    const solved = difficulties.reduce((sum, item) => sum + item.solved, 0);
    const sectors = [];
    let angle = 0;
    for (const [index, item] of difficulties.entries()) {
        if (item.solved > 0) {
            const end = angle + TAU * item.solved / solved;
            sectors.push({ start: angle, end, colorIndex: index, item });
            angle = end;
        }
    }

    mount(canvas, (context, { width, height, colors, point, progress }) => {
        const x = width / 2;
        const y = height / 2;
        const radius = Math.min(width, height) * .32;
        const thickness = radius * .28;
        const sweep = TAU * progress;
        let hovered;
        if (point) {
            const distance = Math.hypot(point.x - x, point.y - y);
            if (Math.abs(distance - radius) <= thickness / 2) {
                const cursor = (Math.atan2(point.y - y, point.x - x) + Math.PI / 2 + TAU) % TAU;
                hovered = sectors.find(sector => cursor >= sector.start && cursor < Math.min(sweep, sector.end));
            }
        }

        ringArc(context, x, y, radius, 0, TAU, colors.track, thickness);
        for (const sector of sectors) {
            const end = Math.min(sweep, sector.end);
            if (end > sector.start) {
                ringArc(context, x, y, radius, sector.start, end,
                    colors.difficulties[sector.colorIndex], thickness + (sector === hovered ? 4 : 0));
            }
        }
        label(context, acceptedCount, x, y - 8, colors.text, 32, "center", 750);
        label(context, "已通过题目", x, y + 21, colors.muted);
        if (!solved) {
            label(context, "暂无难度分布", x, y + radius + thickness / 2 + 20, colors.muted);
        }
        return hovered ? `${hovered.item.label}\n已通过 ${hovered.item.solved} / ${hovered.item.total} 题` : null;
    }, 650);
}

function pathSeries(context, days, key, x, y) {
    context.beginPath();
    context.moveTo(x(0), y(days[0][key]));
    for (let index = 1; index < days.length; index++) {
        context.lineTo(x(index), y(days[index][key]));
    }
}

function trendMarker(context, x, y, color, border) {
    context.beginPath();
    context.arc(x, y, 4, 0, TAU);
    context.fillStyle = color;
    context.fill();
    context.lineWidth = 1.5;
    context.strokeStyle = border;
    context.stroke();
}

export function renderTrendChart(canvas, days) {
    const maximum = Math.max(0, ...days.map(day => day.total));
    const step = Math.max(1, Math.ceil(maximum / 4));
    const ceiling = Math.max(1, Math.ceil(maximum / step) * step);

    mount(canvas, (context, { width, height, colors, point }) => {
        if (!days.length) {
            label(context, "近 30 天暂无提交", width / 2, height / 2, colors.muted);
            return null;
        }
        const left = 38;
        const right = width - 15;
        const top = 16;
        const bottom = height - 30;
        const last = days.length - 1;
        const spacing = (right - left) / Math.max(1, last);
        const x = index => left + index * spacing;
        const y = count => bottom - (bottom - top) * count / ceiling;

        context.lineWidth = 1;
        context.strokeStyle = colors.grid;
        context.beginPath();
        for (let tick = 0; tick <= ceiling; tick += step) {
            const line = y(tick);
            context.moveTo(left, line);
            context.lineTo(right, line);
            label(context, compactNumber.format(tick), left - 8, line, colors.muted, 11, "right");
        }
        context.stroke();
        context.strokeStyle = colors.muted;
        context.beginPath();
        context.moveTo(left, top);
        context.lineTo(left, bottom);
        context.lineTo(right, bottom);
        context.stroke();

        const intervals = Math.min(last, width < 400 ? 3 : 5);
        for (let tick = 0; tick <= intervals; tick++) {
            const index = intervals ? Math.round(last * tick / intervals) : 0;
            const [, month, day] = days[index].date.split("-");
            label(context, Number(month) + "/" + Number(day), x(index), bottom + 16, colors.muted, 11);
        }

        pathSeries(context, days, "ac", x, y);
        context.lineTo(x(last), bottom);
        context.lineTo(left, bottom);
        context.closePath();
        const gradient = context.createLinearGradient(0, top, 0, bottom);
        gradient.addColorStop(0, colors.area);
        gradient.addColorStop(1, "transparent");
        context.fillStyle = gradient;
        context.fill();

        pathSeries(context, days, "total", x, y);
        context.lineWidth = 2;
        context.strokeStyle = colors.total;
        context.setLineDash([5, 4]);
        context.stroke();
        context.setLineDash([]);
        pathSeries(context, days, "ac", x, y);
        context.lineWidth = 2.5;
        context.strokeStyle = colors.primary;
        context.stroke();

        if (!maximum) {
            label(context, "近 30 天暂无提交", (left + right) / 2, (top + bottom) / 2, colors.muted);
        }
        if (!point || point.x < left || point.x > right || point.y < top || point.y > bottom) {
            return null;
        }
        const index = Math.min(last, Math.round((point.x - left) / spacing));
        const day = days[index];
        const line = x(index);
        context.setLineDash([4, 4]);
        context.lineWidth = 1;
        context.strokeStyle = colors.muted;
        context.beginPath();
        context.moveTo(line, top);
        context.lineTo(line, bottom);
        context.stroke();
        context.setLineDash([]);
        trendMarker(context, line, y(day.total), colors.total, colors.card);
        trendMarker(context, line, y(day.ac), colors.primary, colors.card);
        return `${day.date}\n总提交 ${day.total} · AC ${day.ac}`;
    });
}

function heatIndex(count) {
    if (count < 2) {
        return count;
    }
    if (count < 4) {
        return 2;
    }
    return count < 7 ? 3 : 4;
}

export function renderHeatCalendar(canvas, days) {
    // 接口返回没有时区的日期，用 UTC 取星期，避免浏览器所在时区移动格子。
    const offset = days.length ? (new Date(days[0].date).getUTCDay() + 6) % DAYS_PER_WEEK : 0;
    const weekdays = ["一", "二", "三", "四", "五", "六", "日"];
    const hasActivity = days.some(day => day.count > 0);
    const months = [];
    for (const [index, day] of days.entries()) {
        if (index === 0 || day.date.endsWith("-01")) {
            const column = Math.floor((index + offset) / DAYS_PER_WEEK);
            if (!months.length || column - months.at(-1).column >= 2) {
                months.push({ column, month: Number(day.date.slice(5, 7)) });
            }
        }
    }

    mount(canvas, (context, { width, height, colors, point }) => {
        if (!days.length) {
            label(context, "过去一年暂无提交", width / 2, height / 2, colors.muted);
            return null;
        }
        const left = 26;
        const top = 25;
        const step = Math.min(20, (width - left - 8) / CALENDAR_COLUMNS);
        const cell = step - 3;
        for (const { column, month } of months) {
            const x = Math.min(left + column * step, width - 29);
            label(context, month + "月", x, 11, colors.muted, 11, "left");
        }
        for (const [row, weekday] of weekdays.entries()) {
            label(context, weekday, left - 9, top + row * step + cell / 2, colors.muted, 10, "right");
        }
        for (let position = 0; position < CALENDAR_COLUMNS * DAYS_PER_WEEK; position++) {
            const day = days[position - offset];
            const column = Math.floor(position / DAYS_PER_WEEK);
            const row = position % DAYS_PER_WEEK;
            context.fillStyle = colors.heat[day ? heatIndex(day.count) : 0];
            context.globalAlpha = day ? 1 : .35;
            context.fillRect(left + column * step, top + row * step, cell, cell);
        }
        context.globalAlpha = 1;

        const bottom = top + DAYS_PER_WEEK * step + 16;
        const legend = left + CALENDAR_COLUMNS * step - 92;
        label(context, "少", legend - 8, bottom, colors.muted, 11, "right");
        for (const [index, color] of colors.heat.entries()) {
            context.fillStyle = color;
            context.fillRect(legend + index * 15, bottom - 6, 12, 12);
        }
        label(context, "多", legend + 78, bottom, colors.muted, 11, "left");
        if (!hasActivity) {
            label(context, "过去一年暂无提交", left, bottom, colors.muted, 12, "left");
        }

        if (!point) {
            return null;
        }
        const column = Math.floor((point.x - left) / step);
        const row = Math.floor((point.y - top) / step);
        const x = left + column * step;
        const y = top + row * step;
        if (column < 0 || column >= CALENDAR_COLUMNS || row < 0 || row >= DAYS_PER_WEEK ||
            point.x - x >= cell || point.y - y >= cell) {
            return null;
        }
        const day = days[column * DAYS_PER_WEEK + row - offset];
        if (!day) {
            return null;
        }
        context.strokeStyle = colors.text;
        context.lineWidth = 1.5;
        context.strokeRect(x - .5, y - .5, cell + 1, cell + 1);
        return `${day.date}\n提交 ${day.count} 次`;
    });
}
