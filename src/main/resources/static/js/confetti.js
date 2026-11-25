const duration = 2200;
const colors = ["#ffc857", "#ff5378", "#55dbaf", "#67a7ff", "#c58cff", "#ff914d"];

export function launchConfetti() {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        return;
    }

    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d");
    if (!context) {
        return;
    }
    canvas.className = "ac-confetti";
    canvas.setAttribute("aria-hidden", "true");
    document.body.append(canvas);

    let width;
    let height;
    function resize() {
        const ratio = Math.min(window.devicePixelRatio, 2);
        width = window.innerWidth;
        height = window.innerHeight;
        canvas.width = Math.round(width * ratio);
        canvas.height = Math.round(height * ratio);
        context.setTransform(ratio, 0, 0, ratio, 0, 0);
    }
    resize();
    window.addEventListener("resize", resize);

    const particles = Array.from({ length: 100 }, (_, index) => {
        const direction = index % 2 === 0 ? 1 : -1;
        return {
            x: width * (direction > 0 ? .12 : .88),
            y: height * .68,
            vx: direction * (120 + Math.random() * 400),
            vy: -260 - Math.random() * 440,
            angle: Math.random() * Math.PI * 2,
            spin: (Math.random() - .5) * 16,
            width: 5 + Math.random() * 5,
            height: 9 + Math.random() * 8,
            color: colors[index % colors.length]
        };
    });

    let started;
    let previous;
    function draw(now) {
        started ??= now;
        const delta = Math.min((now - (previous ?? now)) / 1000, .05);
        previous = now;
        const drag = Math.exp(-1.1 * delta);
        context.clearRect(0, 0, width, height);
        context.globalAlpha = Math.max(0, Math.min(1, (duration - (now - started)) / 600));
        for (const particle of particles) {
            particle.vx *= drag;
            particle.vy = particle.vy * drag + 680 * delta;
            particle.x += particle.vx * delta;
            particle.y += particle.vy * delta;
            particle.angle += particle.spin * delta;
            context.save();
            context.translate(particle.x, particle.y);
            context.rotate(particle.angle);
            context.fillStyle = particle.color;
            context.fillRect(-particle.width / 2, -particle.height / 2, particle.width, particle.height);
            context.restore();
        }
        frame = window.requestAnimationFrame(draw);
    }
    let frame = window.requestAnimationFrame(draw);
    window.setTimeout(() => {
        window.cancelAnimationFrame(frame);
        window.removeEventListener("resize", resize);
        canvas.remove();
    }, duration);
}
