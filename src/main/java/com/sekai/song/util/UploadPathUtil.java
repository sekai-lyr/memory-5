package com.sekai.song.util;

import com.sekai.song.SekaiSongApplication;

import java.net.URI;
import java.nio.file.Path;

public final class UploadPathUtil {
    private UploadPathUtil() {
    }

    public static Path uploadRoot() {
        return projectRoot().resolve("uploads").toAbsolutePath().normalize();
    }

    private static Path projectRoot() {
        try {
            URI location = SekaiSongApplication.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
            Path classpathRoot = Path.of(location).toAbsolutePath().normalize();
            if (classpathRoot.endsWith(Path.of("target", "classes"))) {
                return classpathRoot.getParent().getParent();
            }
            Path fileName = classpathRoot.getFileName();
            Path parent = classpathRoot.getParent();
            if (fileName != null
                    && fileName.toString().endsWith(".jar")
                    && parent != null
                    && parent.endsWith("target")) {
                return parent.getParent();
            }
        } catch (Exception ignored) {
            // Fall back to the process working directory when running from an unusual launcher.
        }
        return Path.of("").toAbsolutePath().normalize();
    }
}
