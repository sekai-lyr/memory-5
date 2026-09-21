(() => {
    "use strict";

    const page = document.querySelector(".song-selection-page");
    const trackButtons = Array.from(document.querySelectorAll("[data-selection-track]"));
    const background = document.querySelector("[data-selection-background]");
    const featureCover = document.querySelector("[data-selection-feature-cover]");
    const featureTitle = document.querySelector("[data-selection-title]");
    const featureAnime = document.querySelector("[data-selection-anime]");
    const featureSub = document.querySelector("[data-selection-sub]");
    const playAction = document.querySelector("[data-selection-play]");
    const rail = document.querySelector("[data-selection-rail]");
    const trackList = document.querySelector("[data-selection-track-list]");
    const player = document.querySelector("[data-selection-player]");
    const playerBack = document.querySelector("[data-selection-player-back]");
    const playerToggle = document.querySelector("[data-selection-player-toggle]");
    const playerOpen = document.querySelector("[data-selection-player-open]");
    const playerCover = document.querySelector("[data-selection-player-cover]");
    const playerDiscCover = document.querySelector("[data-selection-player-disc-cover]");
    const playerTitle = document.querySelector("[data-selection-player-title]");
    const playerSub = document.querySelector("[data-selection-player-sub]");
    const playerStatus = document.querySelector("[data-selection-player-status]");
    const audio = document.querySelector("[data-selection-audio]");

    if (!page || !trackButtons.length || !rail || !trackList || !player) {
        return;
    }

    let selected = trackButtons[0];
    let isPlayerOpen = false;
    let backgroundSwapTimer = 0;
    let selectionTransitionTimer = 0;

    function triggerSelectionTransition(button) {
        const previousIndex = trackButtons.indexOf(selected);
        const nextIndex = trackButtons.indexOf(button);
        if (previousIndex === -1 || previousIndex === nextIndex) {
            return;
        }
        page.classList.remove("is-selection-next", "is-selection-prev");
        void page.offsetWidth;
        page.classList.add(nextIndex > previousIndex ? "is-selection-next" : "is-selection-prev");
        window.clearTimeout(selectionTransitionTimer);
        selectionTransitionTimer = window.setTimeout(() => {
            page.classList.remove("is-selection-next", "is-selection-prev");
        }, 680);
    }

    function rgbString(color) {
        return color.map((channel) => Math.round(channel)).join(" ");
    }

    function deriveCoverPalette() {
        if (!background || !background.complete || !background.naturalWidth) {
            return;
        }
        try {
            const canvas = document.createElement("canvas");
            canvas.width = 32;
            canvas.height = 32;
            const context = canvas.getContext("2d", { willReadFrequently: true });
            context.drawImage(background, 0, 0, canvas.width, canvas.height);
            const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
            const samples = [];

            for (let index = 0; index < pixels.length; index += 16) {
                const red = pixels[index];
                const green = pixels[index + 1];
                const blue = pixels[index + 2];
                const brightness = (red + green + blue) / 3;
                if (brightness < 18) {
                    continue;
                }
                const saturation = (Math.max(red, green, blue) - Math.min(red, green, blue))
                    / Math.max(Math.max(red, green, blue), 1);
                samples.push({
                    color: [red, green, blue],
                    score: 0.35 + saturation * 1.65 + Math.max(0, (brightness - 36) / 220) * 0.2
                });
            }

            if (!samples.length) {
                return;
            }
            samples.sort((left, right) => right.score - left.score);
            const primarySamples = samples.slice(0, 32);
            const secondarySamples = samples.slice(32, 64);
            const average = (items) => {
                const total = items.reduce((sum, sample) => sum + sample.score, 0);
                return items.reduce((color, sample) => color.map((channel, index) =>
                    channel + sample.color[index] * sample.score), [0, 0, 0])
                    .map((channel) => channel / total);
            };
            const primary = average(primarySamples);
            const secondary = average(secondarySamples.length ? secondarySamples : primarySamples);
            const accent = primary.map((channel) => channel * 0.52 + 255 * 0.48);

            page.style.setProperty("--selection-color-a", rgbString(primary));
            page.style.setProperty("--selection-color-b", rgbString(secondary));
            page.style.setProperty("--selection-accent", rgbString(accent));
        } catch (error) {
            // Remote covers without CORS keep the calm fallback palette from CSS.
        }
    }

    function value(button, name) {
        return button.dataset[name] || "";
    }

    function centerSelection(button) {
        if (!page.classList.contains("selection-carousel-page")) {
            return;
        }
        const track = button.closest(".selection-track");
        if (track) {
            track.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "center" });
        }
    }

    function updateAudioSource(button) {
        if (!audio) {
            return;
        }
        const source = value(button, "localAudio");
        if (audio.dataset.source === source) {
            return;
        }
        audio.pause();
        audio.removeAttribute("src");
        audio.dataset.source = source;
        if (source) {
            audio.src = source;
            audio.load();
        }
    }

    function updatePlayer(button) {
        const cover = value(button, "cover") || "/images/login-music-room.png";
        const title = value(button, "title") || "未命名歌曲";
        const artist = value(button, "artist");
        const anime = value(button, "anime");
        const album = value(button, "album");
        if (playerCover) {
            playerCover.src = cover;
        }
        if (playerDiscCover) {
            playerDiscCover.src = cover;
        }
        if (playerTitle) {
            playerTitle.textContent = title;
        }
        if (playerSub) {
            playerSub.textContent = [artist, anime, album].filter(Boolean).join(" · ");
        }
        if (playerOpen) {
            playerOpen.href = value(button, "playerUrl") || "/player";
        }
    }

    function swapBackground(cover) {
        if (!background || !cover) {
            return;
        }
        page.classList.add("is-background-changing");
        window.clearTimeout(backgroundSwapTimer);
        backgroundSwapTimer = window.setTimeout(() => {
            background.src = cover;
            page.classList.remove("is-background-changing");
        }, 120);
    }

    function updateSelection(button) {
        triggerSelectionTransition(button);
        selected = button;
        const cover = value(button, "cover") || "/images/login-music-room.png";
        trackButtons.forEach((item) => {
            const track = item.closest(".selection-track");
            if (track) {
                track.classList.toggle("is-active", item === button);
            }
        });
        if (featureCover) {
            featureCover.src = cover;
        }
        if (featureTitle) {
            featureTitle.textContent = value(button, "title") || "未命名歌曲";
        }
        if (featureAnime) {
            featureAnime.textContent = value(button, "anime");
        }
        if (featureSub) {
            featureSub.textContent = [value(button, "artist"), value(button, "album")]
                .filter(Boolean)
                .join(" · ");
        }
        if (playAction) {
            playAction.href = value(button, "playerUrl") || "/player";
        }
        updateAudioSource(button);
        updatePlayer(button);
        swapBackground(cover);
        centerSelection(button);
    }

    function syncPlayState() {
        const playing = Boolean(audio && !audio.paused && !audio.ended);
        player.classList.toggle("is-playing", playing);
        page.classList.toggle("is-playing", playing);
        if (playerToggle) {
            playerToggle.textContent = playing ? "⏸" : "▶";
            playerToggle.setAttribute("aria-label", playing ? "暂停" : "播放");
        }
        if (playing && playerStatus) {
            playerStatus.textContent = "圆盘、封面和背景正在跟随歌曲变化。";
        }
    }

    function openPlayer() {
        isPlayerOpen = true;
        rail.classList.add("is-playing");
        trackList.hidden = true;
        player.hidden = false;
        updatePlayer(selected);
        if (audio && audio.src) {
            audio.play().catch(() => {
                if (playerStatus) {
                    playerStatus.textContent = "浏览器阻止了自动播放，请点击圆形播放按钮。";
                }
            });
        } else if (playerStatus) {
            playerStatus.textContent = "这首歌还没有本地音频，可打开完整播放器继续处理。";
        }
        syncPlayState();
    }

    function closePlayer() {
        isPlayerOpen = false;
        if (audio) {
            audio.pause();
        }
        rail.classList.remove("is-playing");
        trackList.hidden = false;
        player.hidden = true;
        syncPlayState();
    }

    trackButtons.forEach((button) => {
        button.addEventListener("click", () => {
            updateSelection(button);
            if (page.classList.contains("selection-carousel-page")) {
                const playerUrl = value(button, "playerUrl");
                if (playerUrl) {
                    window.location.assign(playerUrl);
                }
                return;
            }
            if (isPlayerOpen) {
                openPlayer();
            }
        });
    });

    if (page.classList.contains("selection-carousel-page")) {
        trackList.addEventListener("wheel", (event) => {
            if (Math.abs(event.deltaY) <= Math.abs(event.deltaX)) {
                return;
            }
            event.preventDefault();
            trackList.scrollLeft += event.deltaY;
        }, { passive: false });
    }

    if (playAction && !page.classList.contains("selection-carousel-page")) {
        playAction.addEventListener("click", (event) => {
            event.preventDefault();
            openPlayer();
        });
    }

    if (playerBack) {
        playerBack.addEventListener("click", closePlayer);
    }

    if (playerToggle) {
        playerToggle.addEventListener("click", () => {
            if (!audio || !audio.src) {
                if (playerStatus) {
                    playerStatus.textContent = "这首歌还没有本地音频，请打开完整播放器处理。";
                }
                return;
            }
            if (audio.paused) {
                audio.play().catch(() => {});
            } else {
                audio.pause();
            }
        });
    }

    if (audio) {
        ["play", "pause", "ended"].forEach((eventName) => {
            audio.addEventListener(eventName, syncPlayState);
        });
    }

    if (background) {
        background.addEventListener("load", deriveCoverPalette);
    }
    updateSelection(selected);
    deriveCoverPalette();
})();
