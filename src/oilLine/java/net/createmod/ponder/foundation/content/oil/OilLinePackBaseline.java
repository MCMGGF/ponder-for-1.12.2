package net.createmod.ponder.foundation.content.oil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Read-only, uncached gate for the exact recipe and structure snapshot used by this tutorial. */
public final class OilLinePackBaseline {

    static final long MAX_STANDARD_FILE_BYTES = 8L * 1024 * 1024;
    static final long MAX_CONTENT_FILE_BYTES = 64L * 1024 * 1024;
    static final String CONTENT_PATH = "config/airshipworkshop/content.json";
    private static final String FAILURE_MESSAGE = "本教程配方或结构基线已变，请重新核对，未用旧数值演示";
    private static final Map<String, String> EXPECTED = expectedHashes();
    private static final Map<String, String> CURRENT = currentHashes();
    private static final Map<String, String> DEPLOYED_20261116 = deployedHashes();

    private OilLinePackBaseline() {}

    public static void verify(Path gameDir) {
        verifyProfiles(gameDir, Arrays.asList(DEPLOYED_20261116, CURRENT, EXPECTED));
    }

    static void verifyProfiles(Path gameDir, List<Map<String, String>> profiles) {
        if (profiles == null || profiles.isEmpty()) throw failure("未指定已核对的整合包基线");
        IllegalStateException first = null;
        // Accept a complete audited snapshot, never a mixture of hashes from different revisions.
        for (Map<String, String> profile : profiles) {
            try {
                verify(gameDir, profile);
                return;
            } catch (IllegalStateException failure) {
                if (first == null) first = failure;
                else first.addSuppressed(failure);
            }
        }
        throw first;
    }

    static void verify(Path gameDir, Map<String, String> expected) {
        try {
            if (gameDir == null || expected == null || expected.isEmpty()) {
                throw failure("未指定游戏目录或校验清单");
            }
            Path root = gameDir.toAbsolutePath().normalize().toRealPath();
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw failure("游戏目录不可用");
            }
            for (Map.Entry<String, String> entry : expected.entrySet()) {
                verifyFile(root, entry.getKey(), entry.getValue());
            }
        } catch (IOException | SecurityException exception) {
            throw failure("无法安全读取游戏配方或结构文件", exception);
        }
    }

    private static void verifyFile(Path root, String relativeName, String expectedHash) throws IOException {
        if (relativeName == null || expectedHash == null || !expectedHash.matches("[0-9a-fA-F]{64}")) {
            throw failure("文件校验清单无效");
        }
        Path relative = Paths.get(relativeName);
        if (relative.isAbsolute() || relative.getNameCount() == 0) {
            throw failure("拒绝读取游戏目录之外的文件");
        }
        for (Path part : relative) {
            String name = part.toString();
            if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
                throw failure("拒绝读取游戏目录之外的文件");
            }
        }
        Path file = resolveContained(root, relative);
        BasicFileAttributes before = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        String normalizedName = relative.toString().replace('\\', '/');
        long limit = CONTENT_PATH.equals(normalizedName) ? MAX_CONTENT_FILE_BYTES : MAX_STANDARD_FILE_BYTES;
        if (!before.isRegularFile() || before.size() > limit) {
            throw failure("文件类型或大小不符合基线：" + relativeName);
        }
        MessageDigest digest = sha256();
        long size = 0;
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
                if (size > limit) {
                    throw failure("文件大小超出基线限制：" + relativeName);
                }
                digest.update(buffer, 0, count);
            }
        }
        Path afterPath = resolveContained(root, relative);
        BasicFileAttributes after = Files.readAttributes(afterPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!file.equals(afterPath) || !after.isRegularFile() || size != before.size()
            || before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
            || !Objects.equals(before.fileKey(), after.fileKey())) {
            throw failure("读取期间文件发生变化：" + relativeName);
        }
        if (!expectedHash.equalsIgnoreCase(hex(digest.digest()))) {
            throw failure("文件内容与教学基线不一致：" + relativeName);
        }
    }

    private static Path resolveContained(Path root, Path relative) throws IOException {
        Path current = root;
        for (Path part : relative) {
            current = current.resolve(part).toRealPath();
            if (!current.startsWith(root)) {
                throw failure("目录链接指向游戏目录之外：" + relative);
            }
        }
        return current;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw failure("当前运行环境不支持 SHA-256", exception);
        }
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 255;
            result[i * 2] = digits[value >>> 4];
            result[i * 2 + 1] = digits[value & 15];
        }
        return new String(result);
    }

    private static IllegalStateException failure(String detail) {
        return new IllegalStateException(FAILURE_MESSAGE + "。" + detail);
    }

    private static IllegalStateException failure(String detail, Exception cause) {
        return new IllegalStateException(FAILURE_MESSAGE + "。" + detail, cause);
    }

    private static Map<String, String> expectedHashes() {
        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put("scripts/airship_workshop.zs", "518A7C2AB13F4CC3088813EB7768423BBFB3DE85A7053082D5E72324AD42CA96");
        hashes.put(CONTENT_PATH, "C7526F66827B5C33F45A392006A41CF787229119245C6D2622AED3B200696276");
        String machines = "config/modularmachinery/machinery/airship_workshop/";
        hashes.put(machines + "aw_steam_bay.json", "75C350A51D36D3E6263950B102A6632B984B4742B0ABCC3704F87648114B215A");
        hashes.put(machines + "aw_oil_pump.json", "77A6577A813F3D9AFE38C57EE1727E825ADD84A7707526CCCAC5EAB0A77F6EF1");
        hashes.put(machines + "aw_distillation_tower.json", "DB1CF5B2C39723F375BBF0248FB661502264F625F99F9BE8D8ADFC1A165BCAAE");
        hashes.put(machines + "aw_diesel_generator.json", "EF0D33233809ED19D44D281016E21F99999DD8F175B6086243C33386F394C4B6");
        return Collections.unmodifiableMap(hashes);
    }

    private static Map<String, String> currentHashes() {
        Map<String, String> hashes = new LinkedHashMap<>(EXPECTED);
        // Re-audited against the user's deployed pack on 2026-11-01; oil recipe values are unchanged.
        hashes.put("scripts/airship_workshop.zs", "F62CF954B77CCCD74C6B660E605758AAEFE7289E6951C10CDAC2332D17D02691");
        hashes.put(CONTENT_PATH, "3EA68ECD795A5E9E4923DBCFE6E2B34AAAE42BE6BC3725DA98196E12D3F48B2F");
        return Collections.unmodifiableMap(hashes);
    }

    private static Map<String, String> deployedHashes() {
        Map<String, String> hashes = new LinkedHashMap<>(EXPECTED);
        // 当前整合包的四站结构、基础油线配方和组件约束已重新核对。
        hashes.put("scripts/airship_workshop.zs", "4AC2010B588559483218791136FFBA55BC0E82CD70D596F896060060BC7FCA44");
        hashes.put(CONTENT_PATH, "7B95C3BEAD5A27624BB05EACC50714350E61265751005E853D101915A95D4CE4");
        return Collections.unmodifiableMap(hashes);
    }
}
