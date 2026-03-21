-- Seed Cloudflare CDN warm worker dictionary type and default config item.
INSERT INTO sys_dict_type (id, code, name, i18n_key, status, description)
VALUES (
    15,
    'cdn_warm.worker.config',
    'CDN 预热配置',
    'cdnWarm.worker.config',
    'active',
    'CDN 预热策略、Cloudflare Worker 访问地址、鉴权密钥与默认 TTL 配置'
);

INSERT INTO sys_dict_item (id, dict_type_id, label, value, i18n_key, sort_order, status, extra)
SELECT
    50,
    id,
    '默认 CDN 预热配置',
    'default',
    'cdnWarm.worker.default',
    10,
    'active',
    '{
      "strategy": "SERVER",
      "workerUrl": "https://fota-warmer.leiax00.workers.dev",
      "warmSecret": "wewins@123",
      "defaultTtlSeconds": 2592000
    }'::jsonb
FROM sys_dict_type WHERE code = 'cdn_warm.worker.config';
