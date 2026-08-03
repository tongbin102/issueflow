package com.issueflow.service.data;

import com.issueflow.common.Constants;
import com.issueflow.config.DataManagementProperties;
import com.issueflow.dto.data.DataManagementConfigDTO;
import com.issueflow.entity.BackupRecord;
import com.issueflow.mapper.BackupRecordMapper;
import com.issueflow.service.SysConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据管理模块「纯逻辑」单元测试（Phase10 QA 独立验证）。
 *
 * <p>只覆盖不依赖 Spring 容器 / 数据库 / 文件系统副作用的确定性逻辑：</p>
 * <ul>
 *   <li>{@link SensitiveMaskService}：敏感 key 判定 + 脱敏 / 加密两档策略；</li>
 *   <li>{@link DataManagementConfigService}：sys_config 文本解析与默认值回落；</li>
 *   <li>{@link RetentionPolicyExecutor}：备份文件删除的路径越界防护。</li>
 * </ul>
 *
 * <p>刻意<b>不</b>测 {@code RetentionPolicyExecutor#apply()} 的淘汰主流程 ——
 * 它内部构造 MyBatis-Plus {@code LambdaQueryWrapper}，脱离 Spring 上下文时
 * lambda 列缓存未初始化会直接抛异常，属于集成测试范畴。</p>
 */
@DisplayName("Phase10 数据管理 · 纯逻辑单测")
class DataManagementPureLogicTest {

    // =====================================================================
    // SensitiveMaskService
    // =====================================================================

    @Nested
    @DisplayName("SensitiveMaskService 敏感值处理")
    class SensitiveMask {

        private final SensitiveMaskService service = new SensitiveMaskService();

        @ParameterizedTest(name = "[{index}] {0} 应判定为敏感")
        @ValueSource(strings = {
                "spring.datasource.password",
                "DB_PASSWD",
                "redis.pwd",
                "jwt.secret",
                "auth.token",
                "oss.credential",
                "app.private-key",
                "app.privateKey",
                "aliyun.oss.accessKeySecret",
                "sms.access-key",
                "pwd.salt",
                "callback.signature",
                "third.apiKey",
                "third.api-key",
                "header.Authorization"
        })
        @DisplayName("敏感关键字命中（大小写不敏感 / 子串匹配）")
        void shouldDetectSensitiveKeys(String key) {
            assertThat(service.isSensitive(key)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] {0} 不应判定为敏感")
        @ValueSource(strings = {
                "server.port",
                "spring.application.name",
                "issueflow.data-management.backup-dir",
                "data.management.backup.retain.count",
                "logging.level.root"
        })
        @DisplayName("普通配置键不误伤")
        void shouldNotFlagNormalKeys(String key) {
            assertThat(service.isSensitive(key)).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("null / 空 key 判定为不敏感，不抛异常")
        void shouldTolerateNullKey(String key) {
            assertThat(service.isSensitive(key)).isFalse();
        }

        @Test
        @DisplayName("非敏感项原样返回")
        void shouldPassThroughNonSensitiveValue() {
            assertThat(service.process("server.port", "8080")).isEqualTo("8080");
        }

        @Test
        @DisplayName("null 值统一归一为空串，绝不返回 null")
        void shouldNormalizeNullValue() {
            assertThat(service.process("spring.datasource.password", null)).isEmpty();
            assertThat(service.process("server.port", null)).isEmpty();
        }

        @Test
        @DisplayName("敏感项空值仍为空串（不产生无意义的 MASKED）")
        void shouldKeepEmptySensitiveValueEmpty() {
            assertThat(service.process("jwt.secret", "")).isEmpty();
        }

        @Test
        @DisplayName("敏感项：未配密钥 → ***MASKED***；已配密钥 → ENC(...) 且可解回")
        void shouldMaskOrEncryptSensitiveValue() {
            String plain = "P@ssw0rd-should-never-leak";
            String out = service.process("spring.datasource.password", plain);

            // 关键红线：无论走哪一档，输出都不能包含明文
            assertThat(out).doesNotContain(plain);

            if (service.encryptionEnabled()) {
                assertThat(out).startsWith("ENC(").endsWith(")");
                assertThat(service.decrypt(out)).isEqualTo(plain);
            } else {
                assertThat(out).isEqualTo(SensitiveMaskService.MASKED);
                assertThat(SensitiveMaskService.MASKED).isEqualTo("***MASKED***");
            }
        }

        @Test
        @DisplayName("decrypt 对非 ENC 包裹的输入原样返回，null 归一为空串")
        void shouldPassThroughNonEncText() {
            assertThat(service.decrypt("plain-text")).isEqualTo("plain-text");
            assertThat(service.decrypt(SensitiveMaskService.MASKED)).isEqualTo(SensitiveMaskService.MASKED);
            assertThat(service.decrypt(null)).isEmpty();
        }
    }

    // =====================================================================
    // DataManagementConfigService
    // =====================================================================

    @Nested
    @DisplayName("DataManagementConfigService 配置解析")
    class ConfigParsing {

        private final SysConfigService sysConfigService = mock(SysConfigService.class);
        private final DataManagementProperties properties = new DataManagementProperties();
        private final DataManagementConfigService service =
                new DataManagementConfigService(sysConfigService, properties);

        @Test
        @DisplayName("三项配置键与 SQL 迁移脚本 / DTO 注释严格对齐")
        void shouldReadAllThreeKeys() {
            when(sysConfigService.getConfig(Constants.CFG_DM_RETAIN_COUNT)).thenReturn("7");
            when(sysConfigService.getConfig(Constants.CFG_DM_RETAIN_DAYS)).thenReturn("14");
            when(sysConfigService.getConfig(Constants.CFG_DM_UPLOAD_MAX_MB)).thenReturn("256");

            DataManagementConfigDTO dto = service.getConfig();

            assertThat(dto.getMaxCopies()).isEqualTo(7);
            assertThat(dto.getDefaultDays()).isEqualTo(14);
            assertThat(dto.getSizeLimitMB()).isEqualTo(256);
            assertThat(Constants.CFG_DM_RETAIN_COUNT).isEqualTo("data.management.backup.retain.count");
            assertThat(Constants.CFG_DM_RETAIN_DAYS).isEqualTo("data.management.backup.retain.days");
            assertThat(Constants.CFG_DM_UPLOAD_MAX_MB).isEqualTo("data.management.upload.max.size.mb");
        }

        @Test
        @DisplayName("配置缺失回落 SQL 种子默认值 20 / 30 / 512")
        void shouldFallbackToDefaultsWhenMissing() {
            when(sysConfigService.getConfig(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);

            DataManagementConfigDTO dto = service.getConfig();

            assertThat(dto.getMaxCopies()).isEqualTo(Constants.DM_DEFAULT_RETAIN_COUNT).isEqualTo(20);
            assertThat(dto.getDefaultDays()).isEqualTo(Constants.DM_DEFAULT_RETAIN_DAYS).isEqualTo(30);
            assertThat(dto.getSizeLimitMB()).isEqualTo(Constants.DM_DEFAULT_UPLOAD_MAX_MB).isEqualTo(512);
        }

        @ParameterizedTest(name = "[{index}] 脏值 \"{0}\" 应回落默认 20")
        @ValueSource(strings = {"abc", " ", "12.5", "1e3", "--7"})
        @DisplayName("脏数据不抛异常，回落默认值")
        void shouldFallbackOnDirtyValue(String dirty) {
            when(sysConfigService.getConfig(Constants.CFG_DM_RETAIN_COUNT)).thenReturn(dirty);
            assertThat(service.getConfig().getMaxCopies()).isEqualTo(20);
        }

        @Test
        @DisplayName("上传上限换算为字节（MB × 1024 × 1024）")
        void shouldConvertUploadLimitToBytes() {
            when(sysConfigService.getConfig(Constants.CFG_DM_UPLOAD_MAX_MB)).thenReturn("2");
            assertThat(service.getUploadMaxBytes()).isEqualTo(2L * 1024 * 1024);
        }

        @Test
        @DisplayName("任务超时：非正数一律回落 fallback")
        void shouldGuardTaskTimeout() {
            when(sysConfigService.getConfig(Constants.CFG_DM_TASK_TIMEOUT)).thenReturn("0");
            assertThat(service.getTaskTimeoutSeconds(1800)).isEqualTo(1800);

            when(sysConfigService.getConfig(Constants.CFG_DM_TASK_TIMEOUT)).thenReturn("-5");
            assertThat(service.getTaskTimeoutSeconds(1800)).isEqualTo(1800);

            when(sysConfigService.getConfig(Constants.CFG_DM_TASK_TIMEOUT)).thenReturn("600");
            assertThat(service.getTaskTimeoutSeconds(1800)).isEqualTo(600);
        }

        @ParameterizedTest(name = "[{index}] preBackup=\"{0}\" → {1}")
        @CsvSource(value = {
                "NULL  , true",
                "''    , true",
                "true  , true",
                "TRUE  , true",
                "yes   , true",
                "false , false",
                "FALSE , false",
                "0     , false"
        }, nullValues = "NULL", emptyValue = "''")
        @DisplayName("恢复前预备份开关：默认偏安全侧（true）")
        void shouldDefaultPreBackupToSafeSide(String raw, boolean expected) {
            when(sysConfigService.getConfig(Constants.CFG_DM_PRE_BACKUP_ENABLED)).thenReturn(raw);
            assertThat(service.isPreBackupEnabled()).isEqualTo(expected);
        }

        @Test
        @DisplayName("备份根目录：sys_config 优先，缺失回落 properties")
        void shouldResolveBackupRoot() {
            when(sysConfigService.getConfig(Constants.CFG_DM_BACKUP_DIR)).thenReturn(null);
            assertThat(service.getBackupRoot().toString().replace('\\', '/'))
                    .endsWith("/data/issueflow/backups");

            when(sysConfigService.getConfig(Constants.CFG_DM_BACKUP_DIR)).thenReturn("  /tmp/dm-root  ");
            assertThat(service.getBackupRoot().toString().replace('\\', '/'))
                    .endsWith("/tmp/dm-root");
        }
    }

    // =====================================================================
    // RetentionPolicyExecutor（仅路径安全部分）
    // =====================================================================

    @Nested
    @DisplayName("RetentionPolicyExecutor 文件删除安全")
    class RetentionFileSafety {

        private final BackupRecordMapper mapper = mock(BackupRecordMapper.class);
        private final DataManagementConfigService configService = mock(DataManagementConfigService.class);
        private final RetentionPolicyExecutor executor = new RetentionPolicyExecutor(mapper, configService);

        @Test
        @DisplayName("正常相对路径：文件被删除，记录被逻辑删除")
        void shouldDeleteFileInsideRoot(@TempDir Path root) throws Exception {
            Path file = root.resolve("issueflow_backup_20260803_FULL.zip");
            Files.writeString(file, "zip-content");
            when(configService.getBackupRoot()).thenReturn(root);

            BackupRecord record = new BackupRecord();
            record.setId(1L);
            record.setFilePath("issueflow_backup_20260803_FULL.zip");

            assertThat(executor.removeOne(record)).isTrue();
            assertThat(Files.exists(file)).isFalse();
            verify(mapper).deleteById(1L);
        }

        @Test
        @DisplayName("路径越界（../）：拒绝删除，根目录外文件必须完好")
        void shouldRejectPathTraversal(@TempDir Path base) throws Exception {
            Path root = base.resolve("backups");
            Files.createDirectories(root);
            Path victim = base.resolve("important.txt");
            Files.writeString(victim, "must-survive");
            when(configService.getBackupRoot()).thenReturn(root);

            BackupRecord record = new BackupRecord();
            record.setId(2L);
            record.setFilePath("../important.txt");

            executor.deleteFileQuietly(record);

            assertThat(Files.exists(victim))
                    .as("越界路径必须被拦截，根目录外的文件不得被删除")
                    .isTrue();
        }

        @Test
        @DisplayName("filePath 为空 / 记录为空：静默跳过，不触碰数据库")
        void shouldSkipBlankPathAndNullRecord() {
            BackupRecord blank = new BackupRecord();
            blank.setId(3L);
            blank.setFilePath("   ");
            executor.deleteFileQuietly(blank);

            assertThat(executor.removeOne(null)).isFalse();
            assertThat(executor.removeOne(new BackupRecord())).isFalse();
            verify(mapper, never()).deleteById(anyLong());
        }
    }
}
