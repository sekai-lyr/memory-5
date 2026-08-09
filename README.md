# 🎵 Sekai Song Player · 二次元音乐播放器

> **LRC 歌词同步 · 网易云分享链接直播 · 授权体系**
> Anime music player with LRC lyrics sync, NetEase share-link playback & licensing system

[![Java](https://img.shields.io/badge/Java-17-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![MyBatis](https://img.shields.io/badge/MyBatis-3-lightgrey)](https://mybatis.org/)
[![Thymeleaf](https://img.shields.io/badge/Thymeleaf-3-005F0F)](https://www.thymeleaf.org/)
[![LRC](https://img.shields.io/badge/LRC-歌词同步-brightgreen)](#)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

二次元主题音乐播放器：专属动漫歌曲库、LRC 歌词同步、自定义歌单与授权体系，内置商业化服务页面。

An anime-themed music player with your own song library, synchronized LRC lyrics, playlists, and a licensing system.

## ✨ 功能亮点 Features

- 用户注册、登录、退出 User auth
- 专属动漫歌曲库 Anime song library
- 背景二次元播放器皮肤 Anime-themed skins
- **LRC 歌词同步显示** LRC lyric sync
- 收藏歌曲 Favorites
- 自定义歌单和歌单歌曲管理 Custom playlists
- 歌曲/歌单搜索 Search
- **网易云单曲分享链接直接播放** NetEase share-link playback（粘贴分享链接即可）
- **商业化服务页**：`/business`（接单介绍）、`/service`（公开获客）、`/license`（授权中心）、`/business/licenses`（授权码管理）、`/business/leads`（线索后台）

## 💰 商业化定位 Monetization

推荐把项目作为"私有化音乐库搭建服务"售卖：

- ✅ 可以收费：本地部署、NAS/服务器部署、主题定制、数据迁移、备份恢复、长期维护
- ⚠️ 不要售卖：QQ 音乐、网易云音乐、B 站等第三方平台的歌曲/视频资源或公开在线播放曲库
- 📌 用户上传内容应由用户自行确认拥有合法使用权

默认测试账号：`sekai` / `123456`

## 📁 外部上传目录 External Uploads

大体积本地音频可放外部目录：

```bash
$env:SEKAI_EXTERNAL_UPLOAD_ROOT="D:/YourUploadRoot/uploads"
mvn spring-boot:run
```

URL 前缀 `/external-uploads/**`。

## 🗄️ 数据库 Database

表创建在 `sekai_friend` 数据库中，前缀 `sekai_song_`：

```sql
SOURCE build-data/sekai-song.sql;
```

## ▶️ 启动 Run

```bash
mvn spring-boot:run
```

默认端口：`8085`

平台发布文案见 `docs/monetization-platform-copy.md`。

## 📄 License

[MIT](LICENSE) © 2026 [sekai-lyr](https://github.com/sekai-lyr)

<p align="center">
  <img src="screenshots/demo.webp" alt="Demo" width="720"/>
</p>

---

**⭐ If this project helped you, star it! 如果这个项目对你有帮助，欢迎 Star！**
