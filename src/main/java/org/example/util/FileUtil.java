package org.example.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

public final class FileUtil {
    private FileUtil() {
    }

    public static void ensureDir(String path) throws IOException {
        Files.createDirectories(Paths.get(path));
    }

    public static void writeString(String path, String content) throws IOException {
        Path target = Paths.get(path);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, content);
    }

    public static String readString(String path) throws IOException {
        return Files.readString(Paths.get(path));
    }

    public static boolean exists(String path) {
        return Files.exists(Paths.get(path));
    }

    public static String toBase64(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static void delete(String path) throws IOException {
        Files.deleteIfExists(Paths.get(path));
    }
}
