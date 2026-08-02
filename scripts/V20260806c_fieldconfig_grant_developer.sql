-- ============================================================
-- V20260806c_fieldconfig_grant_developer.sql
-- 给 DEVELOPER 角色授权 field:config:list（只读），使其可查看字段配置
-- 幂等：WHERE NOT EXISTS 防重复插入
-- ============================================================

SET NAMES utf8mb4;

-- 给 DEVELOPER(role_id=2) 授权 field:config:list(只读)
INSERT INTO role_permission (role_id, permission_id)
SELECT 2, p.id
FROM permission p
WHERE p.code = 'field:config:list'
  AND NOT EXISTS (
    SELECT 1 FROM role_permission rp
    WHERE rp.role_id = 2 AND rp.permission_id = p.id
  );

-- 确认授权结果
SELECT rp.role_id, r.code AS role_code, r.name AS role_name, p.code AS perm_code
FROM role_permission rp
JOIN role r ON rp.role_id = r.id
JOIN permission p ON rp.permission_id = p.id
WHERE p.code LIKE 'field:config%'
ORDER BY rp.role_id, p.code;
