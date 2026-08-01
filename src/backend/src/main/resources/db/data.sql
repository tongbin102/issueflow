-- ============================================================
-- issueFlow 初始化数据
-- 注意：默认管理员账号(admin / admin123)不在本文件写入，
--       而是由 IssueFlowApplication 的 ApplicationRunner
--       在启动时使用 BCryptPasswordEncoder 编码密码写入，
--       避免明文/硬编码密文。
--       管理员角色码 ADMIN，默认角色_id 取 ADMIN 记录。
-- ============================================================
SET NAMES utf8mb4;

-- 4 条角色字典
INSERT IGNORE INTO `role` (`code`, `name`, `description`) VALUES
  ('SUBMITTER', '提交者', '提交缺陷问题的用户'),
  ('DEVELOPER', '开发人员', '认领并处理缺陷的研发人员'),
  ('TESTER', '测试人员', '验证缺陷修复结果的测试人员'),
  ('ADMIN', '管理员', '平台管理员，拥有全部权限');
