-- 侧栏父级不再叫「业务应用」。proxy-services 页改成客户端 API key 管理。
UPDATE admin_permission
SET permission_name = 'agent',
    updated_at = clock_timestamp()
WHERE permission_code = 'admin:menu:apps'
  AND deleted = FALSE
  AND permission_name = '业务应用';

UPDATE admin_permission
SET permission_name = 'API 管理',
    updated_at = clock_timestamp()
WHERE permission_code = 'admin:menu:ai.proxyservices'
  AND deleted = FALSE
  AND permission_name = 'proxy-services';
