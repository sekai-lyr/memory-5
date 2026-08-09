package com.sekai.song;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.sekai.song.mapper")
public class SekaiSongApplication {

    public static void main(String[] args) {
        SpringApplication.run(SekaiSongApplication.class, args);
    }
}
