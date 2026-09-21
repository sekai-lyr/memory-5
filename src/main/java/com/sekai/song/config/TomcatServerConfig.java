package com.sekai.song.config;

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatServerConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatProtocolCustomizer() {
        return factory -> {
            String operatingSystem = System.getProperty("os.name", "").toLowerCase();
            if (operatingSystem.contains("win")) {
                factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
            }
        };
    }
}
