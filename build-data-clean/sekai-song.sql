CREATE DATABASE IF NOT EXISTS sekai_friend
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE sekai_friend;

DROP TABLE IF EXISTS sekai_song_playlist_song;
DROP TABLE IF EXISTS sekai_song_playlist;
DROP TABLE IF EXISTS sekai_song_track;
DROP TABLE IF EXISTS sekai_song_user;

CREATE TABLE sekai_song_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_name VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    nick_name VARCHAR(64) NOT NULL,
    avatar_url VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sekai_song_track (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    artist VARCHAR(160),
    anime_title VARCHAR(160),
    album_name VARCHAR(160),
    cover_url VARCHAR(800),
    audio_url VARCHAR(800),
    local_audio_url VARCHAR(800),
    local_video_url VARCHAR(800),
    skin_url VARCHAR(800),
    tags VARCHAR(300),
    mood VARCHAR(80),
    lyric_text TEXT,
    duration_seconds INT,
    favorite TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sekai_song_track_user (user_id),
    CONSTRAINT fk_sekai_song_track_user FOREIGN KEY (user_id) REFERENCES sekai_song_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sekai_song_playlist (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    cover_url VARCHAR(800),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sekai_song_playlist_user (user_id),
    CONSTRAINT fk_sekai_song_playlist_user FOREIGN KEY (user_id) REFERENCES sekai_song_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sekai_song_playlist_song (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    playlist_id BIGINT NOT NULL,
    song_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sekai_song_playlist_song (playlist_id, song_id),
    CONSTRAINT fk_sekai_song_playlist_song_playlist FOREIGN KEY (playlist_id) REFERENCES sekai_song_playlist(id) ON DELETE CASCADE,
    CONSTRAINT fk_sekai_song_playlist_song_track FOREIGN KEY (song_id) REFERENCES sekai_song_track(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sekai_song_user(user_name, password, nick_name)
VALUES ('sekai', 'fc32e479ff7c6fb690468680fd3b371f620ed7b9b93f259755b1beb8654253a0', 'Sekai Listener');

INSERT INTO sekai_song_track(user_id, title, artist, anime_title, album_name, cover_url, audio_url, skin_url, tags, mood, lyric_text, favorite)
VALUES
(1, '夜色播放列表', 'Aster Tone', '星空电台', 'Midnight Anime Selection',
 '/images/login-music-room.png', '', '/images/login-music-room.png', 'ED, 治愈, 夜晚', '治愈',
 CONCAT('[00:00] 城市灯光慢慢亮起', CHAR(10), '[00:08] 耳机里是熟悉的片尾曲', CHAR(10), '[00:16] 今晚也把心事交给旋律', CHAR(10), '[00:25] 在另一个世界轻轻落座'), 1),
(1, '疾走片头曲', 'Niji Pulse', '放课后疾风', 'Opening Sparks',
 '/images/login-music-room.png', '', '/images/login-music-room.png', 'OP, 热血, 校园', '热血',
 CONCAT('[00:00] 风从操场边缘穿过', CHAR(10), '[00:07] 鼓点推着夏天向前', CHAR(10), '[00:15] 我们把明天唱到发亮'), 0),
(1, '雨窗插曲', 'Mikuya', '雨色回忆', 'Insert Song Archive',
 '/images/login-music-room.png', '', '/images/login-music-room.png', '插曲, 雨天, 温柔', '温柔',
 CONCAT('[00:00] 雨落在窗上', CHAR(10), '[00:09] 像一行还没发送的歌词', CHAR(10), '[00:18] 你在副歌里回头'), 1);

INSERT INTO sekai_song_playlist(user_id, name, description, cover_url)
VALUES
(1, '深夜补番歌单', '适合夜里写代码、看番、发呆的温柔循环。', '/images/login-music-room.png'),
(1, '热血开场', 'OP、战斗曲和让人想立刻出门的旋律。', '/images/login-music-room.png');

INSERT INTO sekai_song_playlist_song(playlist_id, song_id)
VALUES (1, 1), (1, 3), (2, 2);
