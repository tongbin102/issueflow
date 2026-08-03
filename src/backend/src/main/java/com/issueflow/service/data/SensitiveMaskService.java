package com.issueflow.service.data;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * 配置快照敏感值处理（Phase10 数据管理）。
 *
 * <p>备份包里的 {@code config/} 目录会随包分发（下载、外传、上传到工单），
 * 一旦夹带明文数据库密码 / JWT 密钥，等于把整个系统的钥匙交出去。
 * 因此导出前所有敏感项必须经过本服务。</p>
 *
 * <p><b>两档策略</b>：</p>
 * <ul>
 *   <li><b>默认（未配置密钥）</b>：直接替换为 {@value #MASKED} —— 不可逆，最安全。
 *       备份包主要用于灾备重建，配置本就应由运维按环境重新注入。</li>
 *   <li><b>配置了 {@code DM_CONFIG_ENC_KEY} 环境变量</b>：AES-256-GCM 加密，
 *       输出 {@code ENC(base64)}。适用于「同环境原样还原」场景，
 *       持有同一密钥的人才能解回。</li>
 * </ul>
 *
 * <p><b>为什么用 GCM 而不是 CBC</b>：GCM 自带完整性校验（AEAD），
 * 密文被篡改会在解密时直接抛异常，而 CBC 需要额外拼 HMAC 才安全。</p>
 */
@Slf4j
@Service
public class SensitiveMaskService {

    /** 不可逆脱敏后的占位符 */
    public static final String MASKED = "***MASKED***";

    /** 加密输出前缀，解密时据此识别 */
    private static final String ENC_PREFIX = "ENC(";

    /** 加密输出后缀 */
    private static final String ENC_SUFFIX = ")";

    /** 读取 AES 密钥的环境变量名 */
    private static final String ENV_KEY = "DM_CONFIG_ENC_KEY";

    /** GCM 推荐 IV 长度（字节） */
    private static final int GCM_IV_LENGTH = 12;

    /** GCM 认证标签长度（bit） */
    private static final int GCM_TAG_LENGTH_BIT = 128;

    /**
     * 敏感 key 判定用的关键字（小写子串匹配）。
     *
     * <p>用「包含」而非「等于」是刻意的：配置键名千变万化
     * （{@code spring.datasource.password} / {@code jwt.secret} /
     * {@code aliyun.oss.accessKeySecret}），穷举必然漏网，
     * 宁可多脱敏几个无害项，也不能漏掉一个密码。</p>
     */
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "credential",
            "private-key", "privatekey", "accesskey", "access-key",
            "salt", "signature", "apikey", "api-key", "authorization");

    /** 加密随机源 */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 判断配置键是否敏感。
     *
     * @param key 配置键，可为 null
     * @return true 敏感
     */
    public boolean isSensitive(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按需处理配置值：敏感则脱敏 / 加密，否则原样返回。
     *
     * @param key   配置键
     * @param value 配置值，可为 null
     * @return 处理后的值，非 null
     */
    public String process(String key, String value) {
        if (value == null) {
            return "";
        }
        if (!isSensitive(key)) {
            return value;
        }
        if (value.isEmpty()) {
            return "";
        }
        byte[] keyBytes = resolveKey();
        if (keyBytes == null) {
            return MASKED;
        }
        try {
            return encrypt(value, keyBytes);
        } catch (Exception e) {
            // 加密失败绝不能回落成明文 —— 宁可退化为不可逆脱敏
            log.warn("[SensitiveMask] 配置项加密失败，回落为不可逆脱敏: key={}, err={}",
                    key, e.getClass().getSimpleName());
            return MASKED;
        }
    }

    /**
     * 解密由 {@link #process(String, String)} 产出的 {@code ENC(...)} 值。
     *
     * @param cipherText 密文（含 ENC 包裹），可为 null
     * @return 明文；非 ENC 格式或密钥缺失时原样返回
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || !cipherText.startsWith(ENC_PREFIX) || !cipherText.endsWith(ENC_SUFFIX)) {
            return cipherText == null ? "" : cipherText;
        }
        byte[] keyBytes = resolveKey();
        if (keyBytes == null) {
            return cipherText;
        }
        try {
            String base64 = cipherText.substring(ENC_PREFIX.length(), cipherText.length() - ENC_SUFFIX.length());
            byte[] combined = Base64.getDecoder().decode(base64);
            if (combined.length <= GCM_IV_LENGTH) {
                return cipherText;
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            byte[] cipherBytes = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[SensitiveMask] 解密失败: {}", e.getClass().getSimpleName());
            return cipherText;
        }
    }

    /**
     * 是否已配置加密密钥（决定走加密档还是脱敏档）。
     *
     * @return true 已配置
     */
    public boolean encryptionEnabled() {
        return resolveKey() != null;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /**
     * AES-256-GCM 加密，输出 {@code ENC(base64(iv || cipher))}。
     *
     * @param plainText 明文
     * @param keyBytes  32 字节密钥
     * @return 密文串
     * @throws Exception 加密异常
     */
    private String encrypt(String plainText, byte[] keyBytes) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv));
        byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + cipherBytes.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);
        return ENC_PREFIX + Base64.getEncoder().encodeToString(combined) + ENC_SUFFIX;
    }

    /**
     * 从环境变量取密钥并规整为 32 字节。
     *
     * <p>用 SHA-256 把任意长度的口令拉伸到 32 字节，
     * 免去「必须填正好 32 个字符」这种反人类的运维约束。</p>
     *
     * @return 32 字节密钥；未配置返回 null
     */
    private byte[] resolveKey() {
        String raw = System.getenv(ENV_KEY);
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(raw.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("[SensitiveMask] 密钥派生失败: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
