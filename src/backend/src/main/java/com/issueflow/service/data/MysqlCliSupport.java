package com.issueflow.service.data;

import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.service.data.DbConnectionResolver.DbConnectionInfo;
import com.issueflow.util.MaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Set;

/**
 * mysqldump / mysql 命令行调用的公共支撑（Phase10 数据管理）。
 *
 * <p>集中处理三件容易出安全事故的事：</p>
 * <ol>
 *   <li><b>密码传递</b>：只写 {@code --defaults-extra-file} 临时文件并设 0600，
 *       绝不用 {@code -p密码} —— 命令行参数会被同机任意用户 {@code ps} 看到；</li>
 *   <li><b>stderr 收集</b>：限长读取并脱敏，避免把连接串 / 密码 / 绝对路径抛给前端；</li>
 *   <li><b>可执行文件校验</b>：提前判断存在且可执行，给出明确的运维提示而非莫名其妙的 IOException。</li>
 * </ol>
 */
@Slf4j
@Component
public class MysqlCliSupport {

    /** stderr 最多保留的字符数，防御超大错误输出撑爆内存 */
    private static final int STDERR_MAX_CHARS = 4000;

    /** 临时凭据文件名前缀 */
    private static final String CRED_FILE_PREFIX = "dm-cred-";

    /** 临时凭据文件名后缀 */
    private static final String CRED_FILE_SUFFIX = ".cnf";

    /**
     * 校验可执行文件是否可用。
     *
     * @param executablePath 可执行文件路径
     * @throws BizException 不存在或不可执行时抛出 {@link ResultCode#DATA_TOOL_UNAVAILABLE}
     */
    public void requireExecutable(String executablePath) {
        if (executablePath == null || executablePath.trim().isEmpty()) {
            log.error("[MysqlCliSupport] 可执行文件路径未配置");
            throw new BizException(ResultCode.DATA_TOOL_UNAVAILABLE);
        }
        Path path = Paths.get(executablePath.trim());
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.exists(path)) {
            log.error("[MysqlCliSupport] 可执行文件不存在: {}", executablePath);
            throw new BizException(ResultCode.DATA_TOOL_UNAVAILABLE);
        }
        if (!Files.isExecutable(path)) {
            log.error("[MysqlCliSupport] 文件存在但不可执行: {}", executablePath);
            throw new BizException(ResultCode.DATA_TOOL_UNAVAILABLE);
        }
    }

    /**
     * 探测可执行文件是否可用（不抛异常版本，用于策略选择）。
     *
     * @param executablePath 可执行文件路径
     * @return true 可用
     */
    public boolean isExecutableAvailable(String executablePath) {
        try {
            requireExecutable(executablePath);
            return true;
        } catch (BizException e) {
            return false;
        }
    }

    /**
     * 生成 mysql 家族通用的凭据临时文件（权限 0600）。
     *
     * <p>文件内容形如：</p>
     * <pre>
     * [client]
     * host=127.0.0.1
     * port=3306
     * user=issueflow
     * password="p@ss"
     * </pre>
     *
     * @param info 连接信息，不可为 null
     * @return 临时文件路径，调用方 <b>必须</b> 在 finally 中调用 {@link #deleteQuietly(Path)}
     * @throws IOException 写文件失败
     */
    public Path writeCredentialsFile(DbConnectionInfo info) throws IOException {
        Path file = Files.createTempFile(CRED_FILE_PREFIX, CRED_FILE_SUFFIX);
        hardenPermissions(file);

        StringBuilder sb = new StringBuilder(256);
        sb.append("[client]\n");
        sb.append("host=").append(info.getHost()).append('\n');
        sb.append("port=").append(info.getPort()).append('\n');
        sb.append("user=").append(info.getUsername()).append('\n');
        sb.append("password=\"").append(escapeCnf(info.getPassword())).append("\"\n");
        // mysqldump 段：确保字符集一致，避免中文乱码
        sb.append("[mysqldump]\n");
        sb.append("default-character-set=utf8mb4\n");
        sb.append("[mysql]\n");
        sb.append("default-character-set=utf8mb4\n");

        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        // 写完再收紧一次，防止 umask 影响
        hardenPermissions(file);
        return file;
    }

    /**
     * 把文件权限收紧为仅属主可读写（POSIX 环境生效，Windows 静默跳过）。
     *
     * @param file 目标文件
     */
    private void hardenPermissions(Path file) {
        try {
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException | IOException e) {
            // Windows 开发环境不支持 POSIX 权限，退化为仅属主可读的近似处理
            try {
                java.io.File f = file.toFile();
                boolean ok = f.setReadable(false, false)
                        && f.setWritable(false, false)
                        && f.setReadable(true, true)
                        && f.setWritable(true, true);
                if (!ok) {
                    log.debug("[MysqlCliSupport] 临时凭据文件权限收紧未完全生效（非 POSIX 环境）");
                }
            } catch (SecurityException se) {
                log.debug("[MysqlCliSupport] 权限收紧被安全策略拒绝: {}", se.getClass().getSimpleName());
            }
        }
    }

    /**
     * 转义 my.cnf 双引号值中的特殊字符。
     *
     * @param raw 原始密码，可为 null
     * @return 转义后的文本
     */
    private String escapeCnf(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 静默删除临时凭据文件。
     *
     * <p>删除失败只记 debug —— 文件在系统临时目录且权限 0600，风险可控，
     * 但绝不能因为删不掉就让整个备份任务失败。</p>
     *
     * @param file 文件路径，可为 null
     */
    public void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.debug("[MysqlCliSupport] 临时凭据文件删除失败: {}", e.getClass().getSimpleName());
            file.toFile().deleteOnExit();
        }
    }

    /**
     * 读取子进程 stderr（限长 + 脱敏）。
     *
     * @param stream stderr 流，不可为 null
     * @return 脱敏后的错误文本；无内容返回空串
     */
    public String drainStderr(InputStream stream) {
        StringBuilder sb = new StringBuilder(512);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while (line != null && sb.length() < STDERR_MAX_CHARS) {
                // mysqldump 的这条 warning 是常态噪音，过滤掉免得吓到管理员
                if (!line.contains("Using a password on the command line")) {
                    sb.append(line).append('\n');
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            log.debug("[MysqlCliSupport] stderr 读取中断: {}", e.getClass().getSimpleName());
        }
        return sanitize(sb.toString());
    }

    /**
     * 错误文案脱敏：去掉绝对路径 / 密码片段。
     *
     * @param text 原始文本，可为 null
     * @return 脱敏后的文本
     */
    public String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String masked = MaskUtils.maskSensitivePath(text);
        // 兜底剥掉 password=xxx / -pxxx 形态
        masked = masked.replaceAll("(?i)password\\s*=\\s*\\S+", "password=***");
        masked = masked.replaceAll("(?i)(^|\\s)-p\\S+", "$1-p***");
        return masked.trim();
    }

    /**
     * 构造 POSIX 权限集合的字符串形式（用于日志与单测断言）。
     *
     * @return 固定返回 {@code rw-------}
     */
    public String credentialFileMode() {
        return PosixFilePermissions.toString(Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }
}
