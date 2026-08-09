const bilibiliNativePlayer = document.querySelector("[data-bilibili-native-player]");
const audio = document.querySelector("audio") || bilibiliNativePlayer;
const lyricRows = Array.from(document.querySelectorAll("#lyrics p[data-time]"));
const queueLinks = Array.from(document.querySelectorAll(".queue-list a"));
const modeButton = document.querySelector("[data-player-action='mode']");
const modeLabel = document.querySelector("[data-play-mode-label]");
const nextButton = document.querySelector("[data-player-action='next']");
const previousButton = document.querySelector("[data-player-action='previous']");
const currentTrackPanel = document.querySelector(".now-playing");
const queueSearch = document.querySelector("[data-queue-search]");
const historyList = document.querySelector("[data-play-history]");
const clearHistoryButton = document.querySelector("[data-clear-history]");
const sleepTimerSelect = document.querySelector("[data-sleep-timer]");
const sleepStatus = document.querySelector("[data-sleep-status]");
const rateSelect = document.querySelector("[data-playback-rate]");
const volumeControl = document.querySelector("[data-volume-control]");
const loopButtons = Array.from(document.querySelectorAll("[data-loop-action]"));
const loopStatus = document.querySelector("[data-loop-status]");
const visualizerCanvas = document.querySelector("[data-visualizer]");
const eqPresetSelect = document.querySelector("[data-eq-preset]");
const eqStatus = document.querySelector("[data-eq-status]");
const addBookmarkButton = document.querySelector("[data-add-bookmark]");
const bookmarkList = document.querySelector("[data-bookmark-list]");
const bilibiliEmbed = document.querySelector(".bilibili-embed");
const bilibiliPlayer = document.querySelector(".bilibili-player");
const bilibiliUsesLocalVideo = bilibiliNativePlayer?.dataset.bilibiliLocalVideo === "true";
const shouldStartAutoplay = currentTrackPanel?.dataset.startAutoplay === "true"
    || new URLSearchParams(window.location.search).get("autoplay") === "1";
let activeLyricRow = null;

const allPlayModes = [
    { key: "sequence", label: "\u987a\u5e8f\u64ad\u653e" },
    { key: "shuffle", label: "\u968f\u673a\u64ad\u653e" },
    { key: "repeat-one", label: "\u5355\u66f2\u5faa\u73af" }
];
const bilibiliPlayModes = [
    { key: "sequence", label: "\u987a\u5e8f\u64ad\u653e" },
    { key: "shuffle", label: "\u968f\u673a\u64ad\u653e" },
    { key: "repeat-one", label: "\u5faa\u73af\u64ad\u653e" }
];
const playModeKey = "sekai.player.playMode";
const autoPlayKey = "sekai.player.autoPlayAfterGesture";
const historyKey = "sekai.player.history";
const sleepEndKey = "sekai.player.sleepEndAt";
const playbackRateKey = "sekai.player.playbackRate";
const volumeKey = "sekai.player.volume";
const loopKeyPrefix = "sekai.player.loop";
const eqPresetKey = "sekai.player.eqPreset";
const bookmarkKeyPrefix = "sekai.player.bookmarks";
let navigatingToTrack = false;
let audioGraph = null;
let visualizerFrame = null;
let bilibiliAdvanceTimer = null;
let bilibiliAdvanceInterval = null;
let bilibiliAdvanceDueAt = 0;
let bilibiliAdvanceHandled = false;
let bilibiliAdvanceWorker = null;
let bilibiliAdvanceWorkerUrl = null;
let bilibiliStallTimer = null;
let bilibiliRecoveryStep = 0;
let bilibiliRecovering = false;
let bilibiliLastMoveAt = Date.now();
let bilibiliLastTime = 0;

function syncLyric(seconds) {
    let active = null;
    for (const row of lyricRows) {
        const time = Number(row.dataset.time || "0");
        if (time <= seconds) {
            active = row;
        }
    }
    if (active === activeLyricRow) {
        return;
    }
    activeLyricRow = active;
    lyricRows.forEach((row) => row.classList.toggle("active", row === active));
    if (active) {
        active.scrollIntoView({ block: "center", behavior: "smooth" });
    }
}

function getPlayMode() {
    const playModes = currentPlayModes();
    const storedMode = localStorage.getItem(playModeKey);
    return playModes.some((mode) => mode.key === storedMode) ? storedMode : "sequence";
}

function setPlayMode(modeKey) {
    const playModes = currentPlayModes();
    const safeModeKey = playModes.some((mode) => mode.key === modeKey) ? modeKey : "sequence";
    localStorage.setItem(playModeKey, safeModeKey);
    const mode = playModes.find((item) => item.key === safeModeKey) || playModes[0];
    if (modeLabel) {
        modeLabel.textContent = mode.label;
    }
    if (modeButton) {
        modeButton.dataset.mode = mode.key;
        modeButton.title = `\u5f53\u524d\uff1a${mode.label}`;
    }
}

function currentPlayModes() {
    return bilibiliEmbed ? bilibiliPlayModes : allPlayModes;
}

function currentQueueIndex() {
    const activeIndex = queueLinks.findIndex((link) => link.classList.contains("active"));
    if (activeIndex >= 0) {
        return activeIndex;
    }
    const currentUrl = new URL(window.location.href);
    return queueLinks.findIndex((link) => new URL(link.href, window.location.origin).pathname === currentUrl.pathname);
}

function openQueueItem(index) {
    if (!queueLinks.length) {
        return;
    }
    const safeIndex = (index + queueLinks.length) % queueLinks.length;
    rememberAutoPlayIntent();
    navigatingToTrack = true;
    const targetUrl = new URL(queueLinks[safeIndex].href, window.location.origin);
    if (bilibiliEmbed || autoPlayEnabled()) {
        targetUrl.searchParams.set("autoplay", "1");
    }
    window.location.href = targetUrl.toString();
}

function autoPlayEnabled() {
    return sessionStorage.getItem(autoPlayKey) === "1";
}

function enableBilibiliAutoplay() {
    if (!bilibiliPlayer) {
        return;
    }
    if (bilibiliPlayer instanceof HTMLMediaElement) {
        bilibiliPlayer.autoplay = true;
        bilibiliPlayer.play().catch(() => {});
        return;
    }
    const url = new URL(bilibiliPlayer.src, window.location.origin);
    if (url.searchParams.get("autoplay") === "1") {
        return;
    }
    url.searchParams.set("autoplay", "1");
    bilibiliPlayer.src = url.toString();
}

function startBilibiliAutoAdvance(activateEmbed = false) {
    if (!bilibiliEmbed) {
        return;
    }
    const duration = Number(bilibiliEmbed.dataset.bilibiliDuration || "0");
    const safeDuration = Number.isFinite(duration) && duration > 0 ? duration : 270;
    const delaySeconds = safeDuration > 900 ? 360 : safeDuration;
    sessionStorage.setItem(autoPlayKey, "1");
    if (activateEmbed) {
        enableBilibiliAutoplay();
    }
    const delay = Math.max(10, delaySeconds + 2) * 1000;
    bilibiliAdvanceDueAt = Date.now() + delay;
    bilibiliAdvanceHandled = false;
    clearTimeout(bilibiliAdvanceTimer);
    clearInterval(bilibiliAdvanceInterval);
    stopBilibiliAdvanceWorker();
    bilibiliAdvanceTimer = setTimeout(checkBilibiliAutoAdvance, delay + 250);
    bilibiliAdvanceInterval = setInterval(checkBilibiliAutoAdvance, 1000);
    startBilibiliAdvanceWorker(delay + 250);
}

function checkBilibiliAutoAdvance(force = false) {
    if (!bilibiliEmbed || bilibiliAdvanceHandled) {
        return;
    }
    if (!force && (!bilibiliAdvanceDueAt || Date.now() < bilibiliAdvanceDueAt)) {
        return;
    }
    bilibiliAdvanceHandled = true;
    clearTimeout(bilibiliAdvanceTimer);
    clearInterval(bilibiliAdvanceInterval);
    stopBilibiliAdvanceWorker();
    playNext();
}

function startBilibiliAdvanceWorker(delay) {
    if (!window.Worker || !window.Blob || !window.URL) {
        return;
    }
    try {
        const source = "let timer=null;onmessage=e=>{clearTimeout(timer);if(e.data&&e.data.type==='start'){timer=setTimeout(()=>postMessage({type:'done'}),e.data.delay)}}";
        bilibiliAdvanceWorkerUrl = URL.createObjectURL(new Blob([source], { type: "text/javascript" }));
        bilibiliAdvanceWorker = new Worker(bilibiliAdvanceWorkerUrl);
        bilibiliAdvanceWorker.onmessage = () => {
            checkBilibiliAutoAdvance(true);
        };
        bilibiliAdvanceWorker.postMessage({ type: "start", delay });
    } catch (error) {
        stopBilibiliAdvanceWorker();
    }
}

function stopBilibiliAdvanceWorker() {
    if (bilibiliAdvanceWorker) {
        bilibiliAdvanceWorker.terminate();
        bilibiliAdvanceWorker = null;
    }
    if (bilibiliAdvanceWorkerUrl) {
        URL.revokeObjectURL(bilibiliAdvanceWorkerUrl);
        bilibiliAdvanceWorkerUrl = null;
    }
}

function listenForBilibiliEndMessage() {
    if (!bilibiliEmbed) {
        return;
    }
    window.addEventListener("message", (event) => {
        if (!String(event.origin || "").includes("bilibili.com")) {
            return;
        }
        const value = typeof event.data === "string" ? event.data : JSON.stringify(event.data || {});
        if (/video[_-]?end|play[_-]?end|ended|player[_-]?ended/i.test(value)) {
            checkBilibiliAutoAdvance(true);
        }
    });
}

function setupBilibiliStreamFallback() {
    if (!bilibiliNativePlayer || bilibiliUsesLocalVideo) {
        return;
    }
    bilibiliNativePlayer.addEventListener("error", () => recoverBilibiliPlayback());
    bilibiliNativePlayer.addEventListener("waiting", scheduleBilibiliStallRecovery);
    bilibiliNativePlayer.addEventListener("stalled", scheduleBilibiliStallRecovery);
    bilibiliNativePlayer.addEventListener("playing", clearBilibiliStallRecovery);
    bilibiliNativePlayer.addEventListener("canplay", clearBilibiliStallRecovery);
    bilibiliNativePlayer.addEventListener("progress", clearBilibiliStallRecovery);
    bilibiliNativePlayer.addEventListener("timeupdate", trackBilibiliProgress);
    setInterval(checkBilibiliFrozenPlayback, 1200);
}

function bilibiliRecoveryPlan() {
    return [
        { mode: "proxy", quality: 80 },
        { mode: "direct", quality: 80, refresh: true },
        { mode: "direct", quality: 64, refresh: true },
        { mode: "proxy", quality: 64, refresh: true },
        { mode: "direct", quality: 32, refresh: true },
        { mode: "proxy", quality: 32, refresh: true },
        { mode: "proxy", quality: 16, refresh: true }
    ];
}

function bilibiliSourceFor(step) {
    const template = step.mode === "proxy"
        ? bilibiliNativePlayer.dataset.bilibiliFallbackSrc
        : bilibiliNativePlayer.dataset.bilibiliDirectSrc || bilibiliNativePlayer.getAttribute("src");
    if (!template) {
        return "";
    }
    const url = new URL(template, window.location.origin);
    url.searchParams.set("quality", String(step.quality));
    if (step.refresh) {
        url.searchParams.set("refresh", String(Date.now()));
    }
    return url.toString();
}

function scheduleBilibiliStallRecovery() {
    if (!bilibiliNativePlayer || bilibiliRecovering || bilibiliNativePlayer.paused || bilibiliNativePlayer.ended) {
        return;
    }
    clearTimeout(bilibiliStallTimer);
    bilibiliStallTimer = setTimeout(() => {
        if (!bilibiliNativePlayer.paused && !bilibiliNativePlayer.ended
            && bilibiliNativePlayer.readyState < HTMLMediaElement.HAVE_FUTURE_DATA) {
            recoverBilibiliPlayback();
        }
    }, 2600);
}

function clearBilibiliStallRecovery() {
    clearTimeout(bilibiliStallTimer);
}

function trackBilibiliProgress() {
    clearBilibiliStallRecovery();
    if (Math.abs(bilibiliNativePlayer.currentTime - bilibiliLastTime) > 0.2) {
        bilibiliLastTime = bilibiliNativePlayer.currentTime;
        bilibiliLastMoveAt = Date.now();
    }
}

function checkBilibiliFrozenPlayback() {
    if (!bilibiliNativePlayer || bilibiliRecovering || bilibiliNativePlayer.paused || bilibiliNativePlayer.ended) {
        return;
    }
    const frozenFor = Date.now() - bilibiliLastMoveAt;
    if (bilibiliNativePlayer.currentTime > 1
        && frozenFor > 4500
        && bilibiliNativePlayer.readyState < HTMLMediaElement.HAVE_FUTURE_DATA) {
        recoverBilibiliPlayback();
    }
}

function recoverBilibiliPlayback() {
    if (!bilibiliNativePlayer || bilibiliRecovering) {
        return;
    }
    const plan = bilibiliRecoveryPlan();
    const step = plan[Math.min(bilibiliRecoveryStep, plan.length - 1)];
    const nextSrc = bilibiliSourceFor(step);
    if (!nextSrc) {
        return;
    }
    bilibiliRecoveryStep += 1;
    bilibiliRecovering = true;
    clearBilibiliStallRecovery();

    const resumeAt = Number.isFinite(bilibiliNativePlayer.currentTime)
        ? Math.max(0, bilibiliNativePlayer.currentTime - 0.4)
        : 0;
    const shouldPlay = !bilibiliNativePlayer.paused || autoPlayEnabled() || shouldStartAutoplay || bilibiliNativePlayer.autoplay;
    let resumed = false;
    const resume = () => {
        if (resumed) {
            return;
        }
        resumed = true;
        try {
            if (resumeAt > 0 && Number.isFinite(bilibiliNativePlayer.duration)) {
                bilibiliNativePlayer.currentTime = Math.min(resumeAt, Math.max(0, bilibiliNativePlayer.duration - 0.8));
            }
        } catch (error) {
            // Some CDNs do not allow seeking until enough metadata arrives.
        }
        if (shouldPlay) {
            bilibiliNativePlayer.play().catch(() => {});
        }
        bilibiliRecovering = false;
        bilibiliLastMoveAt = Date.now();
    };

    bilibiliNativePlayer.addEventListener("loadedmetadata", resume, { once: true });
    bilibiliNativePlayer.addEventListener("canplay", resume, { once: true });
    bilibiliNativePlayer.src = nextSrc;
    bilibiliNativePlayer.load();
    setTimeout(() => {
        if (!resumed) {
            resume();
        }
    }, 5000);
}

function restartBilibiliVideo() {
    if (!bilibiliPlayer) {
        return;
    }
    if (bilibiliPlayer instanceof HTMLMediaElement) {
        bilibiliPlayer.currentTime = 0;
        bilibiliPlayer.play().catch(() => {});
        startBilibiliAutoAdvance(false);
        return;
    }
    const url = new URL(bilibiliPlayer.src, window.location.origin);
    url.searchParams.set("autoplay", "1");
    url.searchParams.set("_restart", String(Date.now()));
    bilibiliPlayer.src = url.toString();
    startBilibiliAutoAdvance(false);
}

function rememberAutoPlayIntent() {
    if (!audio || !audio.paused || autoPlayEnabled()) {
        sessionStorage.setItem(autoPlayKey, "1");
    }
}

function currentTrack() {
    const activeLink = queueLinks.find((link) => link.classList.contains("active"));
    return {
        id: currentTrackPanel?.dataset.songId || "",
        title: currentTrackPanel?.dataset.songTitle || activeLink?.querySelector("strong")?.textContent?.trim() || "\u672a\u77e5\u6b4c\u66f2",
        artist: currentTrackPanel?.dataset.songArtist || activeLink?.querySelector("span")?.textContent?.trim() || "",
        url: currentTrackPanel?.dataset.songUrl || activeLink?.getAttribute("href") || window.location.pathname
    };
}

function progressKey() {
    const track = currentTrack();
    return track.id ? `sekai.player.progress.${track.id}` : "";
}

function saveProgress() {
    if (bilibiliEmbed || !audio || !Number.isFinite(audio.duration) || audio.duration <= 0) {
        return;
    }
    const key = progressKey();
    if (!key) {
        return;
    }
    if (audio.currentTime > 5 && audio.currentTime < audio.duration - 8) {
        localStorage.setItem(key, String(Math.floor(audio.currentTime)));
    }
}

function restoreProgress() {
    if (bilibiliEmbed || !audio) {
        return;
    }
    const key = progressKey();
    const savedSeconds = Number(localStorage.getItem(key) || "0");
    if (savedSeconds > 5 && Number.isFinite(audio.duration) && savedSeconds < audio.duration - 8) {
        audio.currentTime = savedSeconds;
    }
}

function clearProgress() {
    const key = progressKey();
    if (key) {
        localStorage.removeItem(key);
    }
}

function loopKey() {
    const track = currentTrack();
    return track.id ? `${loopKeyPrefix}.${track.id}` : "";
}

function readLoop() {
    const key = loopKey();
    if (!key) {
        return {};
    }
    try {
        return JSON.parse(localStorage.getItem(key) || "{}");
    } catch (error) {
        return {};
    }
}

function writeLoop(loop) {
    const key = loopKey();
    if (key) {
        localStorage.setItem(key, JSON.stringify(loop));
    }
    updateLoopStatus();
}

function clearLoop() {
    const key = loopKey();
    if (key) {
        localStorage.removeItem(key);
    }
    updateLoopStatus();
}

function setLoopPoint(point) {
    if (!audio) {
        return;
    }
    const loop = readLoop();
    loop[point] = Math.max(0, audio.currentTime);
    if (Number.isFinite(loop.a) && Number.isFinite(loop.b) && loop.b <= loop.a + 1) {
        if (point === "a") {
            delete loop.b;
        } else {
            delete loop.a;
        }
    }
    writeLoop(loop);
}

function updateLoopStatus() {
    if (!loopStatus) {
        return;
    }
    const loop = readLoop();
    if (Number.isFinite(loop.a) && Number.isFinite(loop.b)) {
        loopStatus.textContent = `A-B \u5faa\u73af\uff1a${formatTime(loop.a)} - ${formatTime(loop.b)}`;
    } else if (Number.isFinite(loop.a)) {
        loopStatus.textContent = `A \u70b9\uff1a${formatTime(loop.a)}\uff0c\u7ee7\u7eed\u8bbe\u7f6e B \u70b9`;
    } else if (Number.isFinite(loop.b)) {
        loopStatus.textContent = `B \u70b9\uff1a${formatTime(loop.b)}\uff0c\u7ee7\u7eed\u8bbe\u7f6e A \u70b9`;
    } else {
        loopStatus.textContent = "\u672a\u8bbe\u7f6e A-B \u5faa\u73af";
    }
}

function enforceLoop() {
    if (!audio) {
        return;
    }
    const loop = readLoop();
    if (Number.isFinite(loop.a) && Number.isFinite(loop.b) && audio.currentTime >= loop.b) {
        audio.currentTime = loop.a;
        if (!audio.paused) {
            audio.play().catch(() => {});
        }
    }
}

function formatTime(seconds) {
    const safeSeconds = Math.max(0, Math.floor(seconds || 0));
    const minutes = String(Math.floor(safeSeconds / 60)).padStart(2, "0");
    const rest = String(safeSeconds % 60).padStart(2, "0");
    return `${minutes}:${rest}`;
}

function applyPlaybackPreferences() {
    if (!audio || bilibiliEmbed) {
        return;
    }
    const rate = Number(localStorage.getItem(playbackRateKey) || "1");
    audio.playbackRate = Number.isFinite(rate) && rate > 0 ? rate : 1;
    if (rateSelect) {
        rateSelect.value = String(audio.playbackRate);
    }
    const volume = Number(localStorage.getItem(volumeKey) || "1");
    audio.volume = Math.min(1, Math.max(0, Number.isFinite(volume) ? volume : 1));
    if (volumeControl) {
        volumeControl.value = String(Math.round(audio.volume * 100));
    }
}

function bookmarkKey() {
    const track = currentTrack();
    return track.id ? `${bookmarkKeyPrefix}.${track.id}` : "";
}

function readBookmarks() {
    const key = bookmarkKey();
    if (!key) {
        return [];
    }
    try {
        return JSON.parse(localStorage.getItem(key) || "[]");
    } catch (error) {
        return [];
    }
}

function lyricBookmarks() {
    const seen = new Set();
    return lyricRows
        .map((row) => {
            const time = Math.floor(Number(row.dataset.time || "0"));
            const label = (row.querySelector("b")?.textContent || "").trim();
            return { time, label, generated: true };
        })
        .filter((item) => {
            if (!Number.isFinite(item.time) || item.time <= 0 || !item.label || seen.has(item.time)) {
                return false;
            }
            seen.add(item.time);
            return true;
        })
        .slice(0, 30);
}

function writeBookmarks(bookmarks) {
    const key = bookmarkKey();
    if (key) {
        const sortedBookmarks = bookmarks
            .filter((item) => Number.isFinite(item.time))
            .sort((left, right) => left.time - right.time);
        localStorage.setItem(key, JSON.stringify(sortedBookmarks.slice(0, 30)));
    }
    renderBookmarks();
}

function addBookmark() {
    if (!audio) {
        return;
    }
    const time = Math.floor(audio.currentTime);
    const bookmarks = readBookmarks();
    if (bookmarks.some((item) => Math.abs(item.time - time) < 2)) {
        return;
    }
    writeBookmarks([
        ...bookmarks,
        {
            time,
            label: `\u4e66\u7b7e ${formatTime(time)}`
        }
    ]);
}

function renderBookmarks() {
    if (!bookmarkList) {
        return;
    }
    const manualBookmarks = readBookmarks();
    const bookmarks = manualBookmarks.length ? manualBookmarks : lyricBookmarks();
    if (!bookmarks.length) {
        bookmarkList.innerHTML = '<p class="empty">\u8fd8\u6ca1\u6709\u65f6\u95f4\u4e66\u7b7e</p>';
        return;
    }
    bookmarkList.innerHTML = bookmarks.map((item, index) => `
        <div class="bookmark-item${item.generated ? " generated" : ""}">
            <button type="button" data-bookmark-jump="${index}">
                <strong>${formatTime(item.time)}</strong>
                <span>${escapeHtml(item.label || "\u65f6\u95f4\u4e66\u7b7e")}</span>
            </button>
            ${item.generated ? "" : `<button type="button" data-bookmark-delete="${index}">\u5220\u9664</button>`}
        </div>
    `).join("");
}

function setupAudioGraph() {
    if (!audio || audioGraph) {
        return audioGraph;
    }
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) {
        return null;
    }
    try {
        const context = new AudioContextClass();
        const source = context.createMediaElementSource(audio);
        const low = context.createBiquadFilter();
        const mid = context.createBiquadFilter();
        const high = context.createBiquadFilter();
        const analyser = context.createAnalyser();
        low.type = "lowshelf";
        low.frequency.value = 160;
        mid.type = "peaking";
        mid.frequency.value = 1000;
        mid.Q.value = 0.8;
        high.type = "highshelf";
        high.frequency.value = 4200;
        analyser.fftSize = 128;
        source.connect(low);
        low.connect(mid);
        mid.connect(high);
        high.connect(analyser);
        analyser.connect(context.destination);
        audioGraph = { context, low, mid, high, analyser };
        applyEqPreset(localStorage.getItem(eqPresetKey) || "flat");
        return audioGraph;
    } catch (error) {
        return null;
    }
}

function applyEqPreset(presetName) {
    const preset = {
        flat: { label: "\u6807\u51c6", low: 0, mid: 0, high: 0 },
        bass: { label: "\u4f4e\u97f3\u589e\u5f3a", low: 7, mid: -1, high: 0 },
        vocal: { label: "\u4eba\u58f0\u7a81\u51fa", low: -1, mid: 5, high: 2 },
        bright: { label: "\u660e\u4eae", low: -2, mid: 0, high: 6 },
        night: { label: "\u591c\u95f4\u67d4\u548c", low: -5, mid: -2, high: -4 }
    }[presetName] || { label: "\u6807\u51c6", low: 0, mid: 0, high: 0 };
    localStorage.setItem(eqPresetKey, presetName);
    if (eqPresetSelect) {
        eqPresetSelect.value = presetName;
    }
    if (eqStatus) {
        eqStatus.textContent = preset.label;
    }
    if (audioGraph) {
        audioGraph.low.gain.value = preset.low;
        audioGraph.mid.gain.value = preset.mid;
        audioGraph.high.gain.value = preset.high;
    }
}

function startVisualizer() {
    if (!visualizerCanvas) {
        return;
    }
    const graph = setupAudioGraph();
    if (!graph) {
        return;
    }
    graph.context.resume().catch(() => {});
    const context = visualizerCanvas.getContext("2d");
    const data = new Uint8Array(graph.analyser.frequencyBinCount);
    const width = visualizerCanvas.width;
    const height = visualizerCanvas.height;
    const draw = () => {
        graph.analyser.getByteFrequencyData(data);
        context.clearRect(0, 0, width, height);
        context.fillStyle = "rgba(12, 22, 38, 0.82)";
        context.fillRect(0, 0, width, height);
        const gap = 3;
        const barWidth = width / data.length - gap;
        data.forEach((value, index) => {
            const barHeight = Math.max(4, (value / 255) * (height - 14));
            const x = index * (barWidth + gap);
            const y = height - barHeight;
            const gradient = context.createLinearGradient(0, y, 0, height);
            gradient.addColorStop(0, "#8df3e4");
            gradient.addColorStop(1, "#ff8bb8");
            context.fillStyle = gradient;
            context.fillRect(x, y, Math.max(2, barWidth), barHeight);
        });
        visualizerFrame = requestAnimationFrame(draw);
    };
    if (!visualizerFrame) {
        draw();
    }
}

function stopVisualizer() {
    if (visualizerFrame) {
        cancelAnimationFrame(visualizerFrame);
        visualizerFrame = null;
    }
}

function readHistory() {
    try {
        return JSON.parse(localStorage.getItem(historyKey) || "[]");
    } catch (error) {
        return [];
    }
}

function writeHistory(history) {
    localStorage.setItem(historyKey, JSON.stringify(history.slice(0, 20)));
}

function addCurrentTrackToHistory() {
    const track = currentTrack();
    if (!track.id && !track.url) {
        return;
    }
    const nextHistory = [
        { ...track, playedAt: Date.now() },
        ...readHistory().filter((item) => item.id !== track.id || item.url !== track.url)
    ];
    writeHistory(nextHistory);
    renderHistory();
}

function renderHistory() {
    if (!historyList) {
        return;
    }
    const history = readHistory();
    if (!history.length) {
        historyList.innerHTML = '<p class="empty">\u64ad\u653e\u540e\u4f1a\u8bb0\u5f55\u5728\u8fd9\u91cc</p>';
        return;
    }
    historyList.innerHTML = history.map((item) => `
        <a href="${item.url}">
            <strong>${escapeHtml(item.title)}</strong>
            <span>${escapeHtml(item.artist || "\u672a\u77e5\u6b4c\u624b")}</span>
        </a>
    `).join("");
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function filterQueue(keyword) {
    const normalized = keyword.trim().toLowerCase();
    queueLinks.forEach((link) => {
        link.hidden = normalized.length > 0 && !link.textContent.toLowerCase().includes(normalized);
    });
}

function setSleepTimer(minutes) {
    const value = Number(minutes);
    if (!value) {
        localStorage.removeItem(sleepEndKey);
    } else {
        localStorage.setItem(sleepEndKey, String(Date.now() + value * 60 * 1000));
    }
    updateSleepTimer();
}

function updateSleepTimer() {
    if (!sleepStatus || !sleepTimerSelect) {
        return;
    }
    const endAt = Number(localStorage.getItem(sleepEndKey) || "0");
    if (!endAt) {
        sleepStatus.textContent = "\u672a\u5f00\u542f";
        sleepTimerSelect.value = "0";
        return;
    }
    const remaining = endAt - Date.now();
    if (remaining <= 0) {
        localStorage.removeItem(sleepEndKey);
        sessionStorage.setItem(autoPlayKey, "0");
        if (audio) {
            audio.pause();
        }
        sleepStatus.textContent = "\u5df2\u6682\u505c";
        sleepTimerSelect.value = "0";
        return;
    }
    const minutes = Math.ceil(remaining / 60000);
    sleepStatus.textContent = `${minutes} \u5206\u949f\u540e\u6682\u505c`;
}

function randomQueueIndex(currentIndex) {
    if (queueLinks.length <= 1) {
        return currentIndex;
    }
    let nextIndex = currentIndex;
    while (nextIndex === currentIndex) {
        nextIndex = Math.floor(Math.random() * queueLinks.length);
    }
    return nextIndex;
}

function playNext() {
    const currentIndex = currentQueueIndex();
    const mode = getPlayMode();
    if (mode === "repeat-one") {
        sessionStorage.setItem(autoPlayKey, "1");
        if (audio) {
            audio.currentTime = 0;
            audio.play().catch(() => {});
        } else if (bilibiliEmbed) {
            restartBilibiliVideo();
        } else {
            openQueueItem(currentIndex < 0 ? 0 : currentIndex);
        }
        return;
    }
    if (mode === "shuffle") {
        openQueueItem(randomQueueIndex(currentIndex));
        return;
    }
    openQueueItem(currentIndex + 1);
}

function playPrevious() {
    const currentIndex = currentQueueIndex();
    if (getPlayMode() === "shuffle") {
        openQueueItem(randomQueueIndex(currentIndex));
        return;
    }
    openQueueItem(currentIndex - 1);
}

if (shouldStartAutoplay || bilibiliEmbed) {
    sessionStorage.setItem(autoPlayKey, "1");
}
setPlayMode(getPlayMode());
listenForBilibiliEndMessage();
setupBilibiliStreamFallback();
if (bilibiliEmbed && !bilibiliUsesLocalVideo) {
    startBilibiliAutoAdvance(true);
} else if (bilibiliEmbed) {
    enableBilibiliAutoplay();
}
renderHistory();
renderBookmarks();
updateSleepTimer();
updateLoopStatus();
applyPlaybackPreferences();
applyEqPreset(localStorage.getItem(eqPresetKey) || "flat");
setInterval(updateSleepTimer, 1000);

if (modeButton) {
    modeButton.addEventListener("click", () => {
        const playModes = currentPlayModes();
        const currentMode = getPlayMode();
        const currentModeIndex = playModes.findIndex((mode) => mode.key === currentMode);
        const nextMode = playModes[(currentModeIndex + 1) % playModes.length];
        setPlayMode(nextMode.key);
    });
}

if (nextButton) {
    nextButton.addEventListener("click", playNext);
}

if (previousButton) {
    previousButton.addEventListener("click", playPrevious);
}

if (queueSearch) {
    queueSearch.addEventListener("input", () => filterQueue(queueSearch.value));
}

if (sleepTimerSelect) {
    sleepTimerSelect.addEventListener("change", () => setSleepTimer(sleepTimerSelect.value));
}

if (rateSelect) {
    rateSelect.addEventListener("change", () => {
        if (!audio) {
            return;
        }
        const rate = Number(rateSelect.value);
        audio.playbackRate = Number.isFinite(rate) && rate > 0 ? rate : 1;
        localStorage.setItem(playbackRateKey, String(audio.playbackRate));
    });
}

if (volumeControl) {
    volumeControl.addEventListener("input", () => {
        if (!audio) {
            return;
        }
        audio.volume = Math.min(1, Math.max(0, Number(volumeControl.value) / 100));
        localStorage.setItem(volumeKey, String(audio.volume));
    });
}

if (eqPresetSelect) {
    eqPresetSelect.addEventListener("change", () => {
        setupAudioGraph();
        applyEqPreset(eqPresetSelect.value);
    });
}

if (addBookmarkButton) {
    addBookmarkButton.addEventListener("click", addBookmark);
}

if (bookmarkList) {
    bookmarkList.addEventListener("click", (event) => {
        const jumpButton = event.target.closest("[data-bookmark-jump]");
        const deleteButton = event.target.closest("[data-bookmark-delete]");
        const manualBookmarks = readBookmarks();
        const visibleBookmarks = manualBookmarks.length ? manualBookmarks : lyricBookmarks();
        if (jumpButton && audio) {
            const bookmark = visibleBookmarks[Number(jumpButton.dataset.bookmarkJump)];
            if (bookmark) {
                audio.currentTime = bookmark.time;
                audio.play().catch(() => {});
            }
        }
        if (deleteButton) {
            manualBookmarks.splice(Number(deleteButton.dataset.bookmarkDelete), 1);
            writeBookmarks(manualBookmarks);
        }
    });
}

loopButtons.forEach((button) => {
    button.addEventListener("click", () => {
        const action = button.dataset.loopAction;
        if (action === "set-a") {
            setLoopPoint("a");
        } else if (action === "set-b") {
            setLoopPoint("b");
        } else if (action === "clear") {
            clearLoop();
        }
    });
});

if (clearHistoryButton) {
    clearHistoryButton.addEventListener("click", () => {
        localStorage.removeItem(historyKey);
        renderHistory();
    });
}

if (historyList) {
    historyList.addEventListener("click", (event) => {
        const link = event.target.closest("a");
        if (link) {
            rememberAutoPlayIntent();
            navigatingToTrack = true;
        }
    });
}

queueLinks.forEach((link) => {
    link.addEventListener("click", () => {
        rememberAutoPlayIntent();
        navigatingToTrack = true;
    });
});

lyricRows.forEach((row) => {
    row.addEventListener("click", () => {
        if (!audio) {
            return;
        }
        const time = Number(row.dataset.time || "0");
        if (Number.isFinite(time)) {
            audio.currentTime = time;
            audio.play().catch(() => {});
        }
    });
});

document.addEventListener("keydown", (event) => {
    const target = event.target;
    const isTyping = target instanceof HTMLElement
        && ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName);
    if (isTyping) {
        return;
    }
    if (event.code === "Space" && audio) {
        event.preventDefault();
        if (audio.paused) {
            audio.play().catch(() => {});
        } else {
            audio.pause();
        }
    } else if (event.key === "ArrowRight" && audio) {
        audio.currentTime = Math.min(audio.duration || Number.MAX_SAFE_INTEGER, audio.currentTime + 10);
    } else if (event.key === "ArrowLeft" && audio) {
        audio.currentTime = Math.max(0, audio.currentTime - 10);
    } else if (event.key.toLowerCase() === "n") {
        playNext();
    } else if (event.key.toLowerCase() === "p") {
        playPrevious();
    } else if (event.key.toLowerCase() === "m" && modeButton) {
        modeButton.click();
    } else if (event.key.toLowerCase() === "a") {
        setLoopPoint("a");
    } else if (event.key.toLowerCase() === "b") {
        setLoopPoint("b");
    } else if (event.key === "Escape") {
        clearLoop();
    }
});

if (audio) {
    audio.addEventListener("loadedmetadata", restoreProgress, { once: true });
    audio.addEventListener("timeupdate", () => {
        syncLyric(audio.currentTime);
        saveProgress();
        enforceLoop();
    });
    audio.addEventListener("play", () => {
        sessionStorage.setItem(autoPlayKey, "1");
        if (!bilibiliEmbed) {
            addCurrentTrackToHistory();
            startVisualizer();
        }
    });
    audio.addEventListener("pause", () => {
        if (!audio.ended && !navigatingToTrack) {
            sessionStorage.setItem(autoPlayKey, "0");
        }
        if (!bilibiliEmbed) {
            stopVisualizer();
        }
    });
    audio.addEventListener("ended", () => {
        clearProgress();
        playNext();
    });
    if (autoPlayEnabled()) {
        audio.autoplay = true;
        audio.play().catch(() => {
            // Browsers may still require one manual click before allowing sound.
        });
    }
} else {
    let current = 0;
    setInterval(() => {
        current += 1;
        syncLyric(current);
    }, 1000);
    if (bilibiliEmbed) {
        bilibiliEmbed.addEventListener("pointerdown", () => {
            sessionStorage.setItem(autoPlayKey, "1");
            startBilibiliAutoAdvance(false);
        });
    }
    if (bilibiliPlayer) {
        window.addEventListener("blur", () => {
            if (document.activeElement === bilibiliPlayer) {
                sessionStorage.setItem(autoPlayKey, "1");
                startBilibiliAutoAdvance(false);
            }
        });
    }
}
