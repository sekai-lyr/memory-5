<div align="center">

# 🎵 Sekai Song Player · 歌词同步音乐播放器

> ⭐ Sekai 系列实战项目 05 / 14 · 单项目独立仓库

![Java](https://img.shields.io/badge/Java-red?style=flat-square&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![MyBatis](https://img.shields.io/badge/MyBatis-blue?style=flat-square) ![Thymeleaf](https://img.shields.io/badge/Thymeleaf-blue?style=flat-square) ![MySQL](https://img.shields.io/badge/MySQL-blue?style=flat-square)

[![Stars](https://img.shields.io/github/stars/sekai-lyr/memory-5?style=flat-square&logo=github)](https://github.com/sekai-lyr/memory-5/stargazers) [![合集](https://img.shields.io/badge/📚_全系列合集-sekai--product-8250DF?style=flat-square)](https://github.com/sekai-lyr/sekai-product)

</div>

---

`memory-5` 是一个 Spring Boot + Thymeleaf + MyBatis 的二次元音乐播放器，专注于个人歌库整理、歌单管理和本地播放。

## 功能

- 用户注册、登录、退出
- 歌曲库、收藏、歌单和全局搜索
- LRC 同步歌词、播放队列、顺序/随机/单曲循环
- 睡眠定时、播放速度、音量、均衡器和 A-B 片段循环
- 时间书签、最近播放记录和本地音频/视频绑定
- 网易云、QQ 音乐、B 站歌曲/视频链接导入

网易云、QQ 音乐、B 站仅用于粘贴歌曲/歌单链接导入歌曲信息，不内嵌第三方平台页面。

## 外部上传目录

大体积本地音频可以放在外部上传目录，默认路径：

```text
D:/SekaiSongExternalUploads/memory-5/uploads
```

访问 URL 前缀为 `/external-uploads/**`。如果要换目录，可以通过环境变量或 JVM 参数配置：

```bash
SEKAI_EXTERNAL_UPLOAD_ROOT=D:/YourUploadRoot/uploads
mvn spring-boot:run
```

或：

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dsekai.externalUploadRoot=D:/YourUploadRoot/uploads"
```

## 数据库

表都创建在 `sekai_friend` 数据库中，前缀为 `sekai_song_`。

```sql
SOURCE build-data/sekai-song.sql;
```

默认测试账号：

- 用户名：`sekai`
- 密码：`123456`

## 启动

启动前设置本机 MySQL 凭据：

```powershell
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/sekai_friend?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的数据库密码"
```

如果使用 IntelliJ IDEA，请将上述变量填入运行配置的 **Environment variables**。

```bash
mvn spring-boot:run
```

默认端口：`8085`

---

## 🗺️ Sekai 项目全景导航

本项目是 **Sekai 系列作品集** 的一员，全部 14 个实战项目：

| 项目 | 简介 |
|------|------|
| [`memory-1`](https://github.com/sekai-lyr/memory-1) | 📊 二次元数据中心 |
| [`memory-2`](https://github.com/sekai-lyr/memory-2) | 🛒 二次元电商商城 |
| [`memory-3`](https://github.com/sekai-lyr/memory-3) | 📔 二次元回忆录 |
| [`memory-4`](https://github.com/sekai-lyr/memory-4) | 🎋 角色抽签与今日运势 |
| [`memory-5`](https://github.com/sekai-lyr/memory-5) | 🎵 歌词同步音乐播放器 |
| [`memory-6`](https://github.com/sekai-lyr/memory-6) | 🎮 原创角色合成小游戏 |
| [`memory-7`](https://github.com/sekai-lyr/memory-7) | 🖼️ 二次元壁纸图库系统 |
| [`memory-8`](https://github.com/sekai-lyr/memory-8) | 📖 英语单词学习系统 |
| [`memory-9`](https://github.com/sekai-lyr/memory-9) | 📝 表单收集与 AI 对话系统 |
| [`memory-10`](https://github.com/sekai-lyr/memory-10) | 📺 动漫视频导航站 |
| [`memory-11`](https://github.com/sekai-lyr/memory-11) | ⚔️ 游戏王规则卡牌对战 |
| [`memory-12`](https://github.com/sekai-lyr/memory-12) | 🎓 游戏化学习平台 |
| [`memory-13`](https://github.com/sekai-lyr/memory-13) | 🎭 Ren'Py 视觉小说 |
| [`memory-14`](https://github.com/sekai-lyr/memory-14) | 🤖 识番推荐与 RAG 助手平台 |

📦 **一站式浏览全部项目**：[sekai-lyr/sekai-product](https://github.com/sekai-lyr/sekai-product)（monorepo 合集）

<div align="center">

**如果这个项目对你有帮助或启发，欢迎点一个 ⭐ Star 支持一下！**

*Made with ❤️ by [sekai-lyr](https://github.com/sekai-lyr)*

</div>
