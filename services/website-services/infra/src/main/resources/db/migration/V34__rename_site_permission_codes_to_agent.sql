-- V34: RBAC 权限码随 site→agent 改名同步（V33 改名的一部分）。
-- Java 侧 @PreAuthorize 已改查 admin:agent:*；种子（V2/V5）写入的仍是 admin:site:*，此处对齐。
-- admin_role_permission 以 permission_id 关联，不受 code 改名影响；菜单 code/path 不动（path 须与前端路由一致）。

UPDATE admin_permission
SET code = REPLACE(code, 'admin:site:', 'admin:agent:'),
    resource = REPLACE(resource, 'site:', 'agent:')
WHERE code LIKE 'admin:site:%';
