# Sekai Song Player

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
