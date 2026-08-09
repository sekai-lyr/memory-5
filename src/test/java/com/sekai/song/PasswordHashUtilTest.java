package com.sekai.song;

import com.sekai.song.util.PasswordHashUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PasswordHashUtilTest {

    @Test
    void matchesHashedPassword() {
        assertTrue(PasswordHashUtil.matches("123456", PasswordHashUtil.hash("123456")));
    }

    @Test
    void parsesNeteaseUserLink() {
        assertEquals("user", com.sekai.song.util.NeteaseMusicLinkUtil
                .parse("https://y.music.163.com/m/user?id=1454476260").getType());
    }

    @Test
    void parsesNeteasePlaylistLink() {
        assertEquals("playlist", com.sekai.song.util.NeteaseMusicLinkUtil
                .parse("https://music.163.com/#/playlist?id=123456").getType());
    }

    @Test
    void parsesQqSongDetailLink() {
        assertEquals("song", com.sekai.song.util.QqMusicLinkUtil
                .parse("https://y.qq.com/n/ryqq/songDetail/001Qu4I30eVFYb").getType());
    }

    @Test
    void parsesQqSongIdLink() {
        assertEquals("102636799", com.sekai.song.util.QqMusicLinkUtil
                .parse("https://y.qq.com/n/ryqq/songDetail?songid=102636799").getId());
    }

    @Test
    void parsesBilibiliBvLink() {
        assertEquals("video", com.sekai.song.util.BilibiliVideoLinkUtil
                .parse("https://www.bilibili.com/video/BV1GJ411x7h7").getType());
    }

    @Test
    void parsesBilibiliAvLink() {
        assertEquals("av80433022", com.sekai.song.util.BilibiliVideoLinkUtil
                .parse("https://www.bilibili.com/video/av80433022").getId());
    }
}
