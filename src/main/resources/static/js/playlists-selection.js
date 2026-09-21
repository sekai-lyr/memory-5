(() => {
    "use strict";

    const page = document.querySelector(".playlist-index-page");
    const playlistButtons = Array.from(document.querySelectorAll("[data-playlist-selection]"));
    const background = document.querySelector("[data-playlist-background]");
    const featureCover = document.querySelector("[data-playlist-feature-cover]");
    const featureTitle = document.querySelector("[data-playlist-feature-title]");
    const featureMeta = document.querySelector("[data-playlist-feature-meta]");
    const featureDescription = document.querySelector("[data-playlist-feature-description]");
    const featureEnter = document.querySelector("[data-playlist-enter]");
    const featurePlay = document.querySelector("[data-playlist-play]");

    function value(button, name) {
        return button.dataset[name] || "";
    }

    let selectedButton = playlistButtons[0];
    let selectionTransitionTimer = 0;

    function triggerSelectionTransition(button) {
        const previousIndex = playlistButtons.indexOf(selectedButton);
        const nextIndex = playlistButtons.indexOf(button);
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
        selectedButton = button;
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
                const color = [pixels[index], pixels[index + 1], pixels[index + 2]];
                const brightness = color.reduce((sum, channel) => sum + channel, 0) / 3;
                if (brightness < 18) {
                    continue;
                }
                const saturation = (Math.max(...color) - Math.min(...color)) / Math.max(Math.max(...color), 1);
                samples.push({ color, score: 0.35 + saturation * 1.65 });
            }

            if (!samples.length) {
                return;
            }
            samples.sort((left, right) => right.score - left.score);
            const chosen = samples.slice(0, 32);
            const total = chosen.reduce((sum, sample) => sum + sample.score, 0);
            const primary = chosen.reduce((color, sample) => color.map((channel, index) =>
                channel + sample.color[index] * sample.score), [0, 0, 0])
                .map((channel) => channel / total);
            const secondary = primary.map((channel) => channel * 0.72 + 18);
            const accent = primary.map((channel) => channel * 0.52 + 255 * 0.48);

            page.style.setProperty("--selection-color-a", rgbString(primary));
            page.style.setProperty("--selection-color-b", rgbString(secondary));
            page.style.setProperty("--selection-accent", rgbString(accent));
        } catch (error) {
            // Remote covers without CORS keep the calm fallback palette from CSS.
        }
    }

    let backgroundSwapTimer = 0;

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
        playlistButtons.forEach((item) => {
            const track = item.closest(".playlist-index-track");
            if (track) {
                track.classList.toggle("is-active", item === button);
            }
        });

        const cover = value(button, "cover") || "/images/login-music-room.png";
        if (featureCover) {
            featureCover.src = cover;
        }
        if (featureTitle) {
            featureTitle.textContent = value(button, "name") || "未命名歌单";
        }
        if (featureMeta) {
            featureMeta.textContent = `${value(button, "count") || "0"} 首歌曲`;
        }
        if (featureDescription) {
            featureDescription.textContent = value(button, "description") || "还没有写下歌单描述。";
        }
        if (featureEnter) {
            featureEnter.href = value(button, "detailUrl") || "/playlists";
        }
        if (featurePlay) {
            featurePlay.href = value(button, "playUrl") || "/playlists";
        }
        const selectedTrack = button.closest(".playlist-index-track");
        if (selectedTrack) {
            selectedTrack.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "center" });
        }
        swapBackground(cover);
    }

    if (page && playlistButtons.length) {
        if (background) {
            background.addEventListener("load", deriveCoverPalette);
        }
        playlistButtons.forEach((button) => {
            button.addEventListener("click", () => updateSelection(button));
        });
        updateSelection(playlistButtons[0]);
        deriveCoverPalette();
    }

    document.querySelectorAll(".local-audio-upload-form").forEach((form) => {
        const audioExtensions = new Set(["mp3", "flac", "m4a", "wav", "ogg", "aac"]);
        const lyricExtensions = new Set(["lrc"]);
        const inputs = Array.from(form.querySelectorAll('input[type="file"][name="audioFiles"]'));
        const summary = form.querySelector("[data-file-summary]");

        const countAudioFiles = () => {
            let total = 0;
            let audio = 0;
            let lyric = 0;
            inputs.forEach((input) => {
                Array.from(input.files || []).forEach((file) => {
                    total++;
                    const extension = (file.name.split(".").pop() || "").toLowerCase();
                    if (audioExtensions.has(extension) || (file.type && file.type.startsWith("audio/"))) {
                        audio++;
                    } else if (lyricExtensions.has(extension)) {
                        lyric++;
                    }
                });
            });
            return { total, audio, lyric };
        };

        const updateSummary = () => {
            const { total, audio, lyric } = countAudioFiles();
            if (!summary) {
                return;
            }
            if (total === 0) {
                summary.textContent = "文件夹窗口可能不显示歌曲，选定后这里会显示数量";
                summary.classList.remove("ready");
                return;
            }
            const ignored = total - audio - lyric;
            summary.textContent = ignored > 0
                ? `已选 ${audio} 个音频，匹配歌词 ${lyric} 个，忽略 ${ignored} 个其他文件`
                : `已选 ${audio} 个音频，匹配歌词 ${lyric} 个`;
            summary.classList.toggle("ready", audio > 0);
        };

        inputs.forEach((input) => input.addEventListener("change", updateSummary));
        form.addEventListener("submit", (event) => {
            const { audio } = countAudioFiles();
            if (audio === 0) {
                event.preventDefault();
                if (summary) {
                    summary.textContent = "这个文件夹里没有可导入的 MP3 / FLAC / M4A / WAV / OGG / AAC";
                    summary.classList.remove("ready");
                }
            }
        });
    });
})();
