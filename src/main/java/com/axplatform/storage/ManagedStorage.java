package com.axplatform.storage;

import com.axplatform.common.Problem;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Only immutable IDs and closed vocabularies form physical paths. Never original filenames. */
@Component
public class ManagedStorage {
    public static final Set<String> WORKFLOWS =
            Set.of("EXCEL", "WORD", "HWP", "PPT", "CRM", "CALENDAR", "APP");
    public static final Set<String> PURPOSES =
            Set.of("PHOTO", "IMPORT", "SOURCE", "OUTPUT", "OTHER");
    private final Path root, legacy;
    private final Clock clock;
    private final String volumeId;
    private final boolean perDevice;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ManagedStorage.class);

    public ManagedStorage(Environment env, Clock clock) throws IOException {
        this.clock = clock;
        String mode = env.getProperty("ax.storage.binding-mode", "strict");
        if (!Set.of("strict", "per-device").contains(mode))
            throw new IOException("ax.storage.binding-mode는 strict 또는 per-device여야 합니다.");
        perDevice = "per-device".equals(mode);
        String environment = env.getProperty("ax.storage.environment", "local");
        if (!environment.matches("[a-zA-Z0-9_-]{1,40}")) throw new IOException("저장 환경 이름을 확인하세요.");
        String configured = env.getProperty("ax.storage.root", "./data/managed");
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
        Path automatic = automaticRoot(windows, Files.isDirectory(Path.of("D:/")),
                System.getProperty("user.home"), environment);
        if ("auto".equalsIgnoreCase(configured) || configured.isBlank()) configured = automatic.toString();
        else if (perDevice && !windows && configured.matches("^[A-Za-z]:.*")) {
            log.info("PC별 저장 모드: Windows 경로 대신 이 컴퓨터의 기본 자료 폴더를 사용합니다.");
            configured = automatic.toString();
        }
        root = prepare(configured);
        Path marker = root.resolve("storage-volume.id");
        if (Files.isSymbolicLink(marker)) throw new IOException("저장소 식별 파일은 링크를 사용할 수 없습니다.");
        try {
            Files.writeString(
                    marker,
                    UUID.randomUUID().toString(),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS);
        } catch (FileAlreadyExistsException existing) {
        }
        try (var in = Files.newInputStream(marker, LinkOption.NOFOLLOW_LINKS)) {
            volumeId =
                    new String(in.readNBytes(100), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (!volumeId.matches("[a-f0-9-]{36}")) throw new IOException("저장소 식별 파일이 올바르지 않습니다.");
        }
        String legacyConfigured = env.getProperty("ax.storage.legacy-root",
                env.getProperty("ax.storage-root", "./data/private"));
        if (perDevice && !windows && legacyConfigured.matches("^[A-Za-z]:.*"))
            legacyConfigured = root.resolve("legacy").toString();
        legacy = Path.of(legacyConfigured).toAbsolutePath().normalize();
        if (Files.exists(legacy) && Files.isSymbolicLink(legacy))
            throw new IOException("기존 저장 루트에 심볼릭 링크를 사용할 수 없습니다.");
    }

    static Path automaticRoot(boolean windows, boolean hasDDrive, String home, String environment) {
        return windows && hasDDrive ? Path.of("D:/AxPlatform", environment)
                : Path.of(home, "AxPlatform", environment);
    }

    public boolean perDevice() { return perDevice; }

    private static Path prepare(String configured) throws IOException {
        if (configured.matches("^[A-Za-z]:.*")
                && !System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows"))
            throw new IOException(
                    "Windows 저장 경로를 macOS/Linux에서 사용할 수 없습니다. AX_STORAGE_ROOT를 절대 경로로 지정하세요.");
        Path p = Path.of(configured).toAbsolutePath().normalize();
        secureDirectories(p);
        return p.toRealPath();
    }

    private static void secureDirectories(Path p) throws IOException {
        Path current = p.getRoot();
        for (Path piece : p) {
            current = current.resolve(piece);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current)
                        || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("저장 경로에 링크 또는 파일이 있습니다.");
            } else Files.createDirectory(current);
        }
    }

    public void memberFolders(long account) throws IOException {
        if (account < 1) throw new IOException("회원 식별자가 필요합니다.");
        for (String workflow : WORKFLOWS)
            secureDirectories(root.resolve("members/" + account + "/" + workflow));
        secureDirectories(root.resolve("members/" + account + "/COMMON"));
    }

    public String key(
            long owner,
            long business,
            String scope,
            String workflow,
            String project,
            String purpose,
            String id,
            String extension) {
        return key(
                owner,
                business,
                scope,
                workflow,
                project,
                purpose,
                id,
                extension,
                LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))));
    }

    public String key(
            long owner,
            long business,
            String scope,
            String workflow,
            String project,
            String purpose,
            String id,
            String extension,
            LocalDate date) {
        if (owner < 1
                || business < 1
                || !Set.of("COMMON", "WORKFLOW", "PROJECT").contains(scope)
                || !PURPOSES.contains(purpose)
                || !id.matches("[a-f0-9-]{36}")
                || !extension.matches("png|jpg|txt|csv|xlsx"))
            throw Problem.invalid("자료 분류와 파일 형식을 확인하세요.");
        String category = "COMMON".equals(scope) ? "COMMON" : workflow;
        if (!"COMMON".equals(category) && !WORKFLOWS.contains(Objects.toString(category, "")))
            throw Problem.invalid("업무 종류를 선택하세요.");
        String container =
                "PROJECT".equals(scope) ? "projects/" + project : scope.toLowerCase(Locale.ROOT);
        if ("PROJECT".equals(scope) && (project == null || !project.matches("[a-f0-9-]{36}")))
            throw Problem.invalid("작업을 먼저 생성하세요.");
        return "members/"
                + owner
                + "/"
                + category
                + "/businesses/"
                + business
                + "/"
                + container
                + "/"
                + purpose.toLowerCase(Locale.ROOT)
                + "/"
                + date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                + "/"
                + id
                + "."
                + extension;
    }

    public Path resolve(String kind, String key) {
        if (key == null
                || key.contains("\\")
                || key.startsWith("/")
                || key.contains(":")
                || Arrays.asList(key.split("/", -1)).stream()
                        .anyMatch(x -> x.isEmpty() || x.equals(".") || x.equals(".."))
                || !key.matches("[a-zA-Z0-9_./-]{1,600}"))
            throw new Problem(503, "STORAGE_REFERENCE", "허용하지 않는 저장 경로입니다.");
        if ("LEGACY".equals(kind) && !key.matches("[a-f0-9-]{36}\\.(png|jpg|txt)"))
            throw new Problem(503, "STORAGE_REFERENCE", "기존 저장 경로를 확인하세요.");
        if (!Set.of("LEGACY", "MANAGED").contains(kind))
            throw new Problem(503, "STORAGE_REFERENCE", "저장 위치 종류를 확인하세요.");
        Path base = "LEGACY".equals(kind) ? legacy : root, p = base.resolve(key).normalize();
        if (!p.startsWith(base) || p.equals(base))
            throw new Problem(503, "STORAGE_REFERENCE", "저장 범위를 벗어났습니다.");
        Path current = base.getRoot();
        for (Path segment : p) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current))
                throw new Problem(503, "STORAGE_REFERENCE", "심볼릭 링크 파일은 사용할 수 없습니다.");
        }
        return p;
    }

    public Path write(String key, byte[] data) throws IOException {
        Path p = resolve("MANAGED", key);
        secureDirectories(p.getParent());
        // CREATE_NEW prevents collisions; remove only bytes created by this write on failure.
        boolean created = false;
        try {
            try (var out = Files.newOutputStream(p, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                created = true;
                out.write(data);
            }
        } catch (IOException e) {
            if (created) try { Files.deleteIfExists(p); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
        return p;
    }

    public byte[] read(String kind, String key, long expected, String hash) throws IOException {
        Path p = resolve(kind, key);
        try (var in = Files.newInputStream(p, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes(10 * 1024 * 1024 + 1);
            if (bytes.length != expected || !sha(bytes).equals(hash))
                throw new IOException("파일 크기 또는 무결성이 일치하지 않습니다.");
            return bytes;
        }
    }

    public static String sha(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String volumeId() {
        return volumeId;
    }

    public Map<String, String> roots() {
        return Map.of("managedRoot", root.toString(), "legacyRoot", legacy.toString(),
                "bindingMode", perDevice ? "per-device" : "strict", "volumeId", volumeId);
    }
}
