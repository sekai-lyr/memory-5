/* Shared auth showcase. One clock drives the screen, parallel layers and fragments. */
(() => {
    "use strict";
    const showcase = document.querySelector("[data-product-showcase]");
    if (!showcase) return;
    const stack = showcase.querySelector("[data-panel-stack]");
    const panels = [...stack.querySelectorAll("[data-product-panel]")];
    const monitor = showcase.querySelector("[data-login-monitor]");
    const toggle = showcase.querySelector("[data-showcase-toggle]");
    const indexLabel = showcase.querySelector("[data-showcase-index]");
    const titleLabel = showcase.querySelector("[data-showcase-title]");
    const motionPreference = window.matchMedia("(prefers-reduced-motion: reduce)");
    const base = panels[0].cloneNode(true);
    base.classList.add("is-screen-base");
    base.removeAttribute("data-product-panel");
    stack.prepend(base);

    const REST = 900;
    const EXPAND = 950;
    const HOLD = 1100;
    const STEP = 680;
    const EXIT = 860;
    const EXIT_START = REST + EXPAND + HOLD;
    const DURATION = EXIT_START + (panels.length - 1) * STEP + EXIT + 450;
    const colors = ["#7fe0d4", "#8bb4ef", "#b7cde9", "#e2aac8", "#e4c398"];
    const clamp = (value) => Math.max(0, Math.min(1, value));
    const ease = (value) => 1 - Math.pow(1 - clamp(value), 3);

    const dissolveClip = (progress) => {
        const points = ["100% 0%", "100% 100%"];
        for (let row = 40; row > 0; row -= 1) {
            const jitter = ((row * 13) % 7 - 3) * .5 * Math.sin(progress * Math.PI);
            const edge = Math.max(0, Math.min(100, progress * 100 + jitter)).toFixed(2);
            points.push(`${edge}% ${row * 2.5}%`, `${edge}% ${(row - 1) * 2.5}%`);
        }
        return `polygon(${points.join(",")})`;
    };

    // Deterministic fragments stay next to the moving dissolve boundary.
    const layers = panels.map((panel, layerIndex) => {
        const host = document.createElement("div");
        host.className = "pixel-fragments";
        const fragments = Array.from({length: 36}, (_, index) => {
            const fragment = document.createElement("i");
            fragment.className = "pixel-fragment";
            fragment.style.top = `${3 + ((index * 29) % 94)}%`;
            fragment.style.width = `${2 + (index % 5) * 2}px`;
            fragment.style.height = `${1 + (index % 3)}px`;
            fragment.style.background = colors[(index + layerIndex) % colors.length];
            host.append(fragment);
            return fragment;
        });
        panel.append(host);
        return {panel, surface: panel.querySelector(".panel-surface"), host, fragments};
    });
    const decorations = [...showcase.querySelectorAll(".showcase-ring, .showcase-orb, .showcase-bolt")];
    let width = stack.clientWidth;
    let time = 0;
    let previousTime = null;
    let frame = 0;
    let paused = false;
    let inView = true;
    let activePage = -1;

    const render = (elapsed) => {
        const t = elapsed % DURATION;
        const expanded = ease((t - REST) / EXPAND);
        const staticScene = motionPreference.matches;
        const spread = staticScene ? .65 : expanded;
        const float = staticScene ? 0 : Math.sin(elapsed / 1800) * 4;
        monitor.style.transform = `translateY(${float.toFixed(2)}px) rotateX(7deg) rotateY(-17deg) rotateZ(-4deg)`;

        let nextActive = 0;
        layers.forEach(({panel, surface, host, fragments}, index) => {
            const depth = panels.length - index;
            const progress = staticScene ? 0 : clamp((t - EXIT_START - index * STEP) / EXIT);
            const leaving = progress > 0 && progress < 1;
            const x = -width * .035 * depth * spread - width * .29 * ease(progress);
            const y = -width * .018 * depth * spread;
            const z = depth * .2 + width * .048 * depth * spread;
            panel.style.transform = `translate3d(${x.toFixed(2)}px, ${y.toFixed(2)}px, ${z.toFixed(2)}px)`;
            panel.style.opacity = progress >= 1 ? "0" : "1";
            surface.style.clipPath = leaving ? dissolveClip(progress) : "none";
            host.style.visibility = leaving ? "visible" : "hidden";
            if (leaving) {
                fragments.forEach((fragment, shard) => {
                    const age = (progress * 2.8 + shard * .173) % 1;
                    const trail = width * (.035 + (shard % 7) * .014) * age;
                    const dx = width * progress - trail;
                    const dy = ((shard % 5) - 2) * age * 5;
                    fragment.style.transform = `translate(${dx.toFixed(1)}px, ${dy.toFixed(1)}px) scaleX(${(1.8 - age).toFixed(2)})`;
                    fragment.style.opacity = String(Math.sin(age * Math.PI) * Math.sin(progress * Math.PI) * .85);
                });
            }
            if (progress > .6) nextActive = (index + 1) % panels.length;
        });
        decorations.forEach((item, index) => {
            const drift = staticScene ? 0 : Math.sin(elapsed / 1400 + index * 1.8) * (index < 2 ? 6 : 4);
            item.style.translate = `0 ${drift.toFixed(2)}px`;
            if (item.classList.contains("showcase-ring")) {
                const angle = staticScene ? 0 : Math.sin(elapsed / 2200 + index) * 16;
                item.style.transform = `rotateX(${52 + angle}deg) rotateY(${index ? 25 : -15}deg) rotateZ(${index ? 20 : -20}deg)`;
            }
        });
        if (activePage !== nextActive) {
            activePage = nextActive;
            indexLabel.textContent = `${String(nextActive + 1).padStart(2, "0")} / 0${panels.length}`;
            titleLabel.textContent = panels[nextActive].dataset.title;
        }
    };
    const canPlay = () => !paused && !motionPreference.matches && !document.hidden && inView;
    const tick = (now) => {
        frame = 0;
        if (!canPlay()) { previousTime = null; return; }
        if (previousTime !== null) time += Math.min(now - previousTime, 64);
        previousTime = now;
        render(time);
        frame = window.requestAnimationFrame(tick);
    };
    const syncPlayback = () => {
        if (frame) window.cancelAnimationFrame(frame);
        frame = 0;
        previousTime = null;
        render(time);
        if (canPlay()) frame = window.requestAnimationFrame(tick);
    };
    toggle.addEventListener("click", () => {
        paused = !paused;
        toggle.setAttribute("aria-pressed", String(paused));
        toggle.setAttribute("aria-label", paused ? "播放展示动画" : "暂停展示动画");
        toggle.firstElementChild.textContent = paused ? "▶" : "Ⅱ";
        syncPlayback();
    });
    document.addEventListener("visibilitychange", syncPlayback);
    motionPreference.addEventListener("change", syncPlayback);
    const resizeObserver = new ResizeObserver(() => { width = stack.clientWidth; render(time); });
    resizeObserver.observe(stack);
    const intersectionObserver = new IntersectionObserver(([entry]) => {
        inView = entry.isIntersecting;
        syncPlayback();
    });
    intersectionObserver.observe(showcase);
    window.addEventListener("pagehide", () => {
        if (frame) window.cancelAnimationFrame(frame);
        frame = 0;
        previousTime = null;
    });
    window.addEventListener("pageshow", syncPlayback);
    syncPlayback();
})();
