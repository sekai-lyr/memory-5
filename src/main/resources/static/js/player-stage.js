/* ==========================================================================
   Sekai Song — 播放页五层动画舞台引擎（player-stage.js）
   --------------------------------------------------------------------------
   单条主时间线：所有动画（碟片匀速旋转 / 封面浮动 / 背景漂移 / 频谱固定规律
   跳动 / 歌词缓慢上滚 + 当前句金色放大高亮 / 进度同步）都由 applyStage(t)
   这一个函数驱动，t 来自同一条时间线：
     - 渲染模式（?render=1，用于 60fps 成片输出）：t = 帧号 / 60，完全确定；
     - 实时播放：t = audio.currentTime（暂停时冻结；无音频时保持静止）。
   渲染模式暴露 window.__stageSetFrame(i, fps)，配合逐帧截图输出 60fps 成片。
   ========================================================================== */
(() => {
    "use strict";

    const stage = document.querySelector(".player-stage");
    if (!stage) {
        return; // B 站视频等无舞台页面
    }

    // 通知 player.js：频谱画布与歌词滚动由舞台引擎接管
    window.__sekaiStageEngine = true;

    const audio = document.querySelector("audio.stage-audio");
    const lyricsBox = document.querySelector("[data-stage-lyrics]");
    const canvas = document.querySelector("[data-visualizer]");
    const disc = stage.querySelector(".stage-disc");
    const discGlow = stage.querySelector(".stage-disc-glow");
    const particles = Array.from(stage.querySelectorAll(".stage-particles i"));
    const bgImg = document.querySelector(".stage-bg-img"); // 全屏背景是固定层，不在舞台内部
    const seek = stage.querySelector("[data-stage-seek]");
    const timeCurrent = stage.querySelector("[data-stage-time-current]");
    const timeTotal = stage.querySelector("[data-stage-time-total]");
    const playBtn = stage.querySelector("[data-player-action='play']");
    const toolsToggle = stage.querySelector("[data-stage-tools-toggle]");
    const tools = stage.querySelector("[data-stage-tools]");

    const params = new URLSearchParams(window.location.search);
    const RENDER_MODE = params.has("render");
    const FPS = 60;

    // ---------- 歌词行 ----------
    let rows = Array.from(document.querySelectorAll("#lyrics p[data-time]"));
    let activeRow = null;

    // 渲染模式且歌曲没有歌词时，生成一段演示时间轴（不影响真实数据）
    if (RENDER_MODE && rows.length === 0) {
        const demoLines = [
            ["1.5", "♪ ～ ♪"],
            ["5.5", "让旋律在房间里慢慢流淌"],
            ["9.5", "此刻只属于这一首歌"],
            ["13.5", "闭上眼 跟随节拍轻轻摇摆"],
            ["17.5", "每一句歌词都落在心上"],
            ["21.5", "♪ ～ ♪"],
            ["25.5", "灯光暗下来 世界安静了"],
            ["29.5", "这一分钟 只为你循环"],
            ["33.5", "♪ ～ ♪"],
            ["37.5", "把心情写进每一段副歌"],
            ["41.5", "旋律不停 故事继续"],
            ["45.5", "♪ ～ ♪"],
            ["49.5", "下一次播放 我们还在这里"],
            ["53.5", "♪ ～ ♪"]
        ];
        demoLines.forEach(([time, text]) => {
            const p = document.createElement("p");
            p.dataset.time = time;
            const b = document.createElement("b");
            b.textContent = text;
            p.appendChild(b);
            lyricsBox.appendChild(p);
        });
        const emptyHint = lyricsBox.querySelector("p.empty");
        if (emptyHint) {
            emptyHint.remove();
        }
        rows = Array.from(lyricsBox.querySelectorAll("p[data-time]"));
    }

    const renderDuration = rows.length
        ? Math.max(60, Number(rows[rows.length - 1].dataset.time) + 14)
        : 90;

    // ---------- 时间线状态 ----------
    let renderT = 0;       // 渲染模式下的确定时间（帧号 / fps）
    let lastNow = performance.now();
    let raf = 0;
    let scrollPos = 0;
    let scrollTarget = 0;
    let seekDragging = false;
    const reducedMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches === true;

    function stageDuration() {
        if (audio && Number.isFinite(audio.duration) && audio.duration > 0) {
            return audio.duration;
        }
        return renderDuration;
    }

    // 单条主时间线的唯一取值处
    function stageTime() {
        if (RENDER_MODE) {
            return renderT;
        }
        if (audio && audio.duration > 0) {
            return audio.currentTime; // 播放中前进，暂停时冻结
        }
        return 0;
    }

    // ---------- 频谱：固定规律（纯函数，同一 t 结果完全一致） ----------
    const BAR_COUNT = 56;

    function barHeight(i, t) {
        const n = BAR_COUNT;
        const env = 1 - 0.42 * (i / (n - 1));                       // 低频段更高
        const beat = 0.6 + 0.4 * Math.pow(Math.max(0, Math.sin(Math.PI * ((t * 2.0) % 1))), 1.8); // 节拍脉冲 2Hz
        const w1 = 0.5 + 0.5 * Math.sin(t * 1.7 + i * 0.19);
        const w2 = 0.5 + 0.5 * Math.sin(t * 2.6 - i * 0.11 + 1.3);
        const w3 = 0.5 + 0.5 * Math.sin(t * 0.85 + i * 0.31 + 2.1);
        const w = w1 * 0.5 + w2 * 0.3 + w3 * 0.2;
        const h = (0.22 + 0.78 * w) * beat * env;
        return Math.max(0.1, Math.min(1, h));
    }

    function drawSpectrum(t) {
        if (!canvas) {
            return;
        }
        const ctx = canvas.getContext("2d");
        const w = canvas.width;
        const h = canvas.height;
        ctx.clearRect(0, 0, w, h);
        if (!w) {
            return;
        }
        const gap = Math.max(2, Math.floor(w / BAR_COUNT / 7));
        const barW = Math.max(2, (w - gap * (BAR_COUNT - 1)) / BAR_COUNT);
        const base = h - 18;
        for (let i = 0; i < BAR_COUNT; i++) {
            const k = barHeight(i, t);
            const bh = Math.max(3, k * (h - 28));
            const x = i * (barW + gap);
            const y = base - bh;
            const grad = ctx.createLinearGradient(0, y, 0, base);
            grad.addColorStop(0, "#fff3c4");
            grad.addColorStop(0.45, "#ffd166");
            grad.addColorStop(1, "#ff8bb8");
            ctx.fillStyle = grad;
            ctx.globalAlpha = 0.5 + 0.5 * k;
            ctx.beginPath();
            ctx.roundRect(x, y, barW, bh, Math.min(6, barW / 2));
            ctx.fill();
        }
        ctx.globalAlpha = 1;
    }

    // ---------- 歌词：当前句金色放大高亮 + 缓慢上滚 ----------
    function highlightLyric(seconds) {
        let active = null;
        for (const row of rows) {
            if (Number(row.dataset.time || "0") <= seconds) {
                active = row;
            }
        }
        if (active !== activeRow) {
            activeRow = active;
            rows.forEach((row) => row.classList.toggle("active", row === active));
        }
    }

    function scrollLyrics(t, dt) {
        if (!lyricsBox || !activeRow) {
            return;
        }
        const rowHeight = activeRow.getBoundingClientRect().height || 50;
        const target = Math.max(0, activeRow.offsetTop - (lyricsBox.clientHeight - rowHeight) / 2);
        scrollTarget = target;
        scrollPos += (scrollTarget - scrollPos) * (1 - Math.exp(-dt * 5));
        if (Math.abs(scrollTarget - scrollPos) < 0.2) {
            scrollPos = scrollTarget;
        }
        lyricsBox.scrollTop = scrollPos;
    }

    // ---------- 进度与时间 ----------
    function formatTime(seconds) {
        const s = Math.max(0, Math.floor(seconds));
        const m = Math.floor(s / 60);
        return `${m}:${String(s % 60).padStart(2, "0")}`;
    }

    function updateProgress(t) {
        const dur = stageDuration();
        const pct = Math.min(100, (t / dur) * 100);
        seek.style.setProperty("--seek", pct.toFixed(2) + "%");
        if (!seekDragging) {
            seek.value = String(Math.round(pct * 10));
        }
        timeCurrent.textContent = formatTime(t);
        timeTotal.textContent = formatTime(dur);
    }

    // ---------- 单条主时间线：所有动画都在这里 ----------
    function applyStage(t, dt) {
        // L3 碟片与封面圆盘匀速旋转：30°/s（12 秒一圈）——变量设在舞台，两层碟片共同继承
        const motionT = reducedMotion ? 0 : t;
        if (disc) {
            stage.style.setProperty("--cd-angle", ((motionT * 30) % 360).toFixed(3) + "deg");
        }
        // L2 封面圆盘轻微浮动
        if (disc) {
            disc.style.setProperty("--cover-float", (reducedMotion ? 0 : Math.sin(motionT * 0.9) * 7).toFixed(2) + "px");
        }
        // 圆盘光晕呼吸
        if (discGlow) {
            discGlow.style.setProperty("--glow-opacity", (0.55 + 0.25 * Math.sin(motionT * 1.3)).toFixed(3));
            discGlow.style.setProperty("--glow-scale", (1 + 0.035 * Math.sin(motionT * 1.3)).toFixed(4));
        }
        // 环绕光效粒子：沿圆盘外圈轨道旋转 + 呼吸
        for (let i = 0; i < particles.length; i++) {
            const p = particles[i];
            const ang = motionT * (0.9 + i * 0.07) + i * 1.15;
            const r = discRadius + 18 + (i % 3) * 14;
            p.style.transform = `translate(${(Math.cos(ang) * r).toFixed(2)}px, ${(Math.sin(ang) * r).toFixed(2)}px)`;
            p.style.opacity = reducedMotion ? "0" : (0.35 + 0.65 * (0.5 + 0.5 * Math.sin(motionT * 2.1 + i * 1.7))).toFixed(3);
        }
        // L1 背景缓慢漂移
        if (bgImg) {
            bgImg.style.setProperty("--bg-drift", (reducedMotion ? 0 : Math.sin(motionT * 0.12) * 10).toFixed(2) + "px");
        }
        // 频谱（固定规律）
        drawSpectrum(reducedMotion ? 0 : t);
        // L4 歌词高亮 + 缓慢上滚
        highlightLyric(t);
        scrollLyrics(t, dt);
        // L5 进度同步
        updateProgress(t);
    }

    function tick(now) {
        const dt = Math.min(0.1, Math.max(0.001, (now - lastNow) / 1000));
        lastNow = now;
        const timelineRunning = Boolean(audio && !audio.paused && !audio.ended);
        applyStage(stageTime(), timelineRunning ? dt : 0);
        raf = requestAnimationFrame(tick);
    }

    // ---------- 控件 ----------
    if (playBtn && audio) {
        const updatePlayIcon = () => {
            playBtn.textContent = audio.paused ? "▶" : "⏸";
            stage.classList.toggle("is-playing", !audio.paused && !audio.ended);
        };
        audio.addEventListener("play", updatePlayIcon);
        audio.addEventListener("pause", updatePlayIcon);
        playBtn.addEventListener("click", () => {
            if (audio.paused) {
                audio.play().catch(() => {});
            } else {
                audio.pause();
            }
        });
        updatePlayIcon();
    }

    if (seek && audio) {
        seek.addEventListener("input", () => {
            seekDragging = true;
            const pct = Math.min(100, Math.max(0, Number(seek.value) / 10));
            const t = (pct / 100) * stageDuration();
            seek.style.setProperty("--seek", pct.toFixed(2) + "%");
            timeCurrent.textContent = formatTime(t);
            if (audio) {
                audio.currentTime = t;
            }
        });
        seek.addEventListener("change", () => {
            seekDragging = false;
        });
    }

    if (toolsToggle && tools) {
        toolsToggle.addEventListener("click", () => {
            const willHide = !tools.hidden;
            tools.hidden = willHide;
            toolsToggle.textContent = willHide ? "更多 ▾" : "更多 ▴";
        });
    }

    // 右侧抽屉：播放队列 / 书签 / 最近播放
    const stageDrawer = document.querySelector("[data-stage-drawer]");
    const stageDrawerToggle = document.querySelector("[data-stage-queue-toggle]");
    const stageDrawerClose = document.querySelector("[data-stage-drawer-close]");
    if (stageDrawer && stageDrawerToggle) {
        stageDrawerToggle.addEventListener("click", () => {
            const open = stageDrawer.classList.toggle("open");
            stageDrawer.setAttribute("aria-hidden", String(!open));
        });
        if (stageDrawerClose) {
            stageDrawerClose.addEventListener("click", () => {
                stageDrawer.classList.remove("open");
                stageDrawer.setAttribute("aria-hidden", "true");
            });
        }
        // 点击舞台区域关闭抽屉（排除队列按钮本身，避免刚打开就被关闭）
        stage.addEventListener("click", (event) => {
            if (stageDrawer.classList.contains("open")
                && !stageDrawer.contains(event.target)
                && !event.target.closest("[data-stage-queue-toggle]")) {
                stageDrawer.classList.remove("open");
                stageDrawer.setAttribute("aria-hidden", "true");
            }
        });
    }

    // 画布按实际宽度保持清晰
    function resizeCanvas() {
        if (!canvas) {
            return;
        }
        const width = Math.max(320, Math.floor(canvas.clientWidth));
        if (canvas.width !== width) {
            canvas.width = width;
            canvas.height = 132;
            drawSpectrum(stageTime());
        }
    }
    if (canvas && typeof ResizeObserver !== "undefined") {
        const ro = new ResizeObserver(resizeCanvas);
        ro.observe(canvas);
    }

    // 粒子轨道半径：跟随圆盘实际尺寸
    let discRadius = 180;
    function measureDisc() {
        discRadius = Math.max(90, (disc.offsetWidth || 300) / 2);
    }
    if (disc && typeof ResizeObserver !== "undefined") {
        const ro = new ResizeObserver(measureDisc);
        ro.observe(disc);
    }
    measureDisc();

    // ---------- 启动 ----------
    if (RENDER_MODE) {
        // 渲染模式：铺满全屏、隐藏辅助 UI、音频静音冻结
        document.body.classList.add("render-mode");
        if (audio) {
            audio.pause();
            audio.muted = true;
        }
        window.__stageSetFrame = (i, fps = FPS) => {
            renderT = i / fps;
            applyStage(renderT, 1 / fps);
            return i;
        };
        window.__stageState = () => ({
            t: renderT,
            rows: rows.length,
            active: activeRow ? activeRow.dataset.time : null,
            duration: stageDuration(),
            cdAngle: stage.style.getPropertyValue("--cd-angle"),
            canvasPixels: canvas ? `${canvas.width}x${canvas.height}` : null
        });
        window.__stageReady = true;
        applyStage(0, 1 / FPS);
    } else {
        raf = requestAnimationFrame(tick);
    }

    // 调试：暴露时间线
    window.__stageTimeline = {
        now: stageTime,
        apply: applyStage
    };
})();
