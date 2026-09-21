(() => {
    "use strict";

    const root = document.documentElement;
    const reduceMotionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
    const revealSelectors = [
        ".content > .section-title",
        ".content > .hero-panel",
        ".content > .stats-grid",
        ".content > .home-tools-grid",
        ".content > .two-column",
        ".content > .editor-panel",
        ".content > .card-grid",
        ".content > .pager",
        ".content > .search-bar",
        ".content > .playlist-hero",
        ".content > .lyrics-panel",
        ".content > .queue-panel",
        ".content > .message",
        ".content > .server-message"
    ];
    const itemContainerSelectors = [
        ".card-grid",
        ".song-list",
        ".compact-list",
        ".home-action-list",
        ".queue-list",
        ".bookmark-list",
        ".history-list"
    ];
    const pointerGlowSelectors = [
        ".home-tool-panel",
        ".song-card",
        ".playlist-card",
        ".stats-grid article",
        ".editor-panel"
    ];

    root.classList.add("js-motion");

    const revealTargets = new Set();
    revealSelectors.forEach((selector) => {
        document.querySelectorAll(selector).forEach((element) => revealTargets.add(element));
    });

    itemContainerSelectors.forEach((selector) => {
        document.querySelectorAll(selector).forEach((container) => {
            Array.from(container.children)
                .filter((element) => !element.classList.contains("empty") && !element.classList.contains("header"))
                .forEach((element, index) => {
                    element.classList.add("motion-item");
                    element.style.setProperty("--motion-delay", `${Math.min(index * 55, 385)}ms`);
                    revealTargets.add(element);
                });
        });
    });

    revealTargets.forEach((element) => element.classList.add("motion-reveal"));

    const revealImmediately = () => {
        revealTargets.forEach((element) => element.classList.add("is-visible"));
    };

    if (reduceMotionQuery.matches || !("IntersectionObserver" in window)) {
        revealImmediately();
    } else {
        const observer = new IntersectionObserver((entries) => {
            entries.forEach((entry) => {
                if (!entry.isIntersecting) {
                    return;
                }
                entry.target.classList.add("is-visible");
                observer.unobserve(entry.target);
            });
        }, {threshold: 0.1, rootMargin: "0px 0px -8% 0px"});

        revealTargets.forEach((element) => observer.observe(element));
    }

    const pointerTargets = document.querySelectorAll(pointerGlowSelectors.join(","));
    pointerTargets.forEach((element) => {
        element.classList.add("pointer-glow");
        element.addEventListener("pointermove", (event) => {
            const bounds = element.getBoundingClientRect();
            const x = ((event.clientX - bounds.left) / bounds.width) * 100;
            const y = ((event.clientY - bounds.top) / bounds.height) * 100;
            element.style.setProperty("--pointer-x", `${Math.max(0, Math.min(100, x))}%`);
            element.style.setProperty("--pointer-y", `${Math.max(0, Math.min(100, y))}%`);
        });
        element.addEventListener("pointerleave", () => {
            element.style.setProperty("--pointer-x", "50%");
            element.style.setProperty("--pointer-y", "0%");
        });
    });

    const pressedAnimations = new WeakMap();
    document.addEventListener("pointerdown", (event) => {
        if (reduceMotionQuery.matches || !(event.target instanceof Element)) {
            return;
        }
        const target = event.target.closest(
            "button, .pill-btn, .hero-action, .playlist-edit-link, .file-picker-button, .text-action"
        );
        if (!target || target.hasAttribute("disabled")) {
            return;
        }
        pressedAnimations.get(target)?.cancel();
        const animation = target.animate([
            {transform: "scale(1)"},
            {transform: "scale(.975)", offset: 0.45},
            {transform: "scale(1)"}
        ], {
            duration: 260,
            easing: "cubic-bezier(.2,.8,.2,1)"
        });
        pressedAnimations.set(target, animation);
    });

    const animateStats = () => {
        if (reduceMotionQuery.matches) {
            return;
        }
        document.querySelectorAll(".stats-grid article > span").forEach((element) => {
            const value = element.textContent.trim();
            if (!/^\d+$/.test(value)) {
                return;
            }
            const target = Number(value);
            if (!Number.isSafeInteger(target) || target <= 0) {
                return;
            }
            const startedAt = performance.now();
            const duration = 720;
            const tick = (now) => {
                const progress = Math.min(1, (now - startedAt) / duration);
                const eased = 1 - Math.pow(1 - progress, 3);
                element.textContent = String(Math.round(target * eased));
                if (progress < 1) {
                    window.requestAnimationFrame(tick);
                }
            };
            element.textContent = "0";
            window.requestAnimationFrame(tick);
        });
    };

    const stage = document.querySelector(".player-stage");
    const audio = document.querySelector("audio.stage-audio");
    if (stage && audio) {
        const syncPlayingState = () => {
            stage.classList.toggle("is-playing", !audio.paused && !audio.ended);
        };
        ["play", "pause", "ended"].forEach((eventName) => audio.addEventListener(eventName, syncPlayingState));
        syncPlayingState();
    }

    window.requestAnimationFrame(animateStats);
})();
