package com.junseo.citymap.layout;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/**
 * 서버 폴더의 설계도 파일(plugins/플러그인/layout.json) 관리.
 * 운영자가 고쳐 쓸 수 있게 플러그인 안의 설계도를 꺼내 두는데, 플러그인을 새 버전으로 바꾸면
 * 꺼내 둔 옛 설계도가 그대로 남는 문제가 있어서, 버전이 올라갔으면 새 것으로 바꿉니다 (옛 것은 백업).
 */
public final class LayoutFile {

    /**
     * file 이 없으면 만들고, 플러그인 안의 설계도(bundled)가 더 새 버전이면 바꿉니다.
     *
     * @return 무엇을 했는지 한 줄 (아무것도 안 했으면 null)
     */
    public static String ensureCurrent(Path file, Supplier<InputStream> bundled) throws IOException {
        byte[] fresh;
        try (InputStream in = bundled.get()) {
            if (in == null) {
                throw new IOException("플러그인 안에 layout.json 이 없어요");
            }
            fresh = in.readAllBytes();
        }
        if (!Files.exists(file)) {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, fresh);
            return "설계도를 꺼내 뒀어요: " + file;
        }
        int bundledVersion = version(fresh);
        int fileVersion;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            fileVersion = Layout.parse(reader).version();
        } catch (RuntimeException e) {
            fileVersion = -1; // 깨진 파일
        }
        if (bundledVersion <= fileVersion) {
            return null;
        }
        Path backup = file.resolveSibling(file.getFileName() + ".v" + fileVersion + ".bak");
        Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        Files.write(file, fresh);
        return "설계도를 v" + fileVersion + " → v" + bundledVersion + " 로 바꿨어요 (옛 파일: " + backup.getFileName() + ")";
    }

    private static int version(byte[] json) {
        try (Reader reader = new InputStreamReader(new java.io.ByteArrayInputStream(json), StandardCharsets.UTF_8)) {
            return Layout.parse(reader).version();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private LayoutFile() {
    }
}
