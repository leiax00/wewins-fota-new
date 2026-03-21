-- Seed load-control dictionary types and items.
-- Current stage uses dictionaries as the source of truth and snapshot.publish as the manual publish switch.

-- Align dictionary sequences first.
-- Some existing environments contain manually inserted seed data, which may leave the serial sequence behind.
SELECT setval(pg_get_serial_sequence('sys_dict_type', 'id'), COALESCE((SELECT MAX(id) FROM sys_dict_type), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('sys_dict_item', 'id'), COALESCE((SELECT MAX(id) FROM sys_dict_item), 0) + 1, false);

INSERT INTO sys_dict_type (code, name, i18n_key, status, description)
VALUES
    ('load_control.control_parameter', '负载控制参数', 'loadControl.controlParameter', 'active', '动态周期与下载延迟控制参数'),
    ('load_control.scoring.instance', '实例级评分规则', 'loadControl.scoring.instance', 'active', '实例级负载评分指标配置'),
    ('load_control.scoring.host', '主机辅助评分规则', 'loadControl.scoring.host', 'active', '主机级辅助负载评分指标配置'),
    ('load_control.scoring.region', '区域级评分规则', 'loadControl.scoring.region', 'active', '区域级负载评分指标配置'),
    ('load_control.sentinel.rule', 'Sentinel 规则', 'loadControl.sentinel.rule', 'active', 'Sentinel Flow/Degrade 规则配置'),
    ('load_control.meta', '负载控制元信息', 'loadControl.meta', 'active', '负载控制快照发布开关等元信息')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    i18n_key = EXCLUDED.i18n_key,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_dict_item (dict_type_id, label, value, i18n_key, sort_order, status, extra)
SELECT id, '全局负载控制参数', 'global.control', 'loadControl.control.global', 10, 'active',
       '{
         "kind": "load_control_parameter",
         "protectedIntervalMultiplier": 2.0,
         "downloadDelayMultiplier": 1.0,
         "minCheckIntervalSeconds": 1800,
         "maxCheckIntervalSeconds": 172800,
         "schemaVersion": 1
       }'::jsonb
FROM sys_dict_type WHERE code = 'load_control.control_parameter'
ON CONFLICT (dict_type_id, value) DO UPDATE
SET label = EXCLUDED.label,
    i18n_key = EXCLUDED.i18n_key,
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status,
    extra = EXCLUDED.extra,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_dict_item (dict_type_id, label, value, i18n_key, sort_order, status, extra)
SELECT id, src.label, src.value, src.i18n_key, src.sort_order, 'active', src.extra::jsonb
FROM sys_dict_type
JOIN (
    VALUES
        ('实例 JVM CPU', 'instance.jvm_cpu', 'loadControl.metric.instanceJvmCpu', 10, '{"kind":"load_scoring_metric","scope":"INSTANCE","metricKey":"INSTANCE_JVM_CPU","metricType":"RESOURCE","unit":"percent","enabled":true,"warning":70,"critical":90,"weight":16,"schemaVersion":1}'),
        ('实例 JVM 堆内存', 'instance.jvm_heap', 'loadControl.metric.instanceJvmHeap', 20, '{"kind":"load_scoring_metric","scope":"INSTANCE","metricKey":"INSTANCE_JVM_HEAP","metricType":"RESOURCE","unit":"percent","enabled":true,"warning":75,"critical":90,"weight":10,"schemaVersion":1}'),
        ('实例连接池使用率', 'instance.db_pool_usage', 'loadControl.metric.instanceDbPoolUsage', 30, '{"kind":"load_scoring_metric","scope":"INSTANCE","metricKey":"INSTANCE_DB_POOL_USAGE","metricType":"RESOURCE","unit":"percent","enabled":true,"warning":80,"critical":95,"weight":8,"schemaVersion":1}'),
        ('实例 Check QPS 利用率', 'instance.check_qps_utilization', 'loadControl.metric.instanceCheckQpsUtilization', 40, '{"kind":"load_scoring_metric","scope":"INSTANCE","metricKey":"INSTANCE_CHECK_QPS_UTILIZATION","metricType":"QPS_UTILIZATION","unit":"percent","enabled":true,"warning":60,"critical":80,"weight":11,"capacitySource":{"type":"sentinel_flow","resource":"upgrade:check","aggregation":"instance_direct"},"schemaVersion":1}'),
        ('实例 Report QPS 利用率', 'instance.report_qps_utilization', 'loadControl.metric.instanceReportQpsUtilization', 50, '{"kind":"load_scoring_metric","scope":"INSTANCE","metricKey":"INSTANCE_REPORT_QPS_UTILIZATION","metricType":"QPS_UTILIZATION","unit":"percent","enabled":true,"warning":60,"critical":80,"weight":4,"capacitySource":{"type":"sentinel_flow","resource":"upgrade:report","aggregation":"instance_direct"},"schemaVersion":1}'),
        ('实例 Check P50', 'instance.check_p50', 'loadControl.metric.instanceCheckP50', 60, '{"kind":"load_scoring_metric","scope":"INSTANCE","metricKey":"INSTANCE_CHECK_P50","metricType":"LATENCY","unit":"ms","enabled":true,"warning":30,"critical":60,"weight":5,"schemaVersion":1}'),
        ('实例 Check P99', 'instance.check_p99', 'loadControl.metric.instanceCheckP99', 70, '{"kind":"load_scoring_metric","scope":"INSTANCE","metricKey":"INSTANCE_CHECK_P99","metricType":"LATENCY","unit":"ms","enabled":true,"warning":50,"critical":100,"weight":11,"schemaVersion":1}'),
        ('实例 Report P50', 'instance.report_p50', 'loadControl.metric.instanceReportP50', 80, '{"kind":"load_scoring_metric","scope":"INSTANCE","metricKey":"INSTANCE_REPORT_P50","metricType":"LATENCY","unit":"ms","enabled":true,"warning":20,"critical":40,"weight":2,"schemaVersion":1}'),
        ('实例 Report P99', 'instance.report_p99', 'loadControl.metric.instanceReportP99', 90, '{"kind":"load_scoring_metric","scope":"INSTANCE","metricKey":"INSTANCE_REPORT_P99","metricType":"LATENCY","unit":"ms","enabled":true,"warning":40,"critical":80,"weight":6,"schemaVersion":1}')
) AS src(label, value, i18n_key, sort_order, extra)
ON sys_dict_type.code = 'load_control.scoring.instance'
ON CONFLICT (dict_type_id, value) DO UPDATE
SET label = EXCLUDED.label,
    i18n_key = EXCLUDED.i18n_key,
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status,
    extra = EXCLUDED.extra,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_dict_item (dict_type_id, label, value, i18n_key, sort_order, status, extra)
SELECT id, src.label, src.value, src.i18n_key, src.sort_order, 'active', src.extra::jsonb
FROM sys_dict_type
JOIN (
    VALUES
        ('主机 CPU', 'host.cpu', 'loadControl.metric.hostCpu', 10, '{"kind":"load_scoring_metric","scope":"HOST","metricKey":"HOST_CPU","metricType":"RESOURCE","unit":"percent","enabled":true,"warning":70,"critical":90,"weight":7,"schemaVersion":1}'),
        ('主机内存', 'host.memory', 'loadControl.metric.hostMemory', 20, '{"kind":"load_scoring_metric","scope":"HOST","metricKey":"HOST_MEMORY","metricType":"RESOURCE","unit":"percent","enabled":true,"warning":75,"critical":90,"weight":4,"schemaVersion":1}')
) AS src(label, value, i18n_key, sort_order, extra)
ON sys_dict_type.code = 'load_control.scoring.host'
ON CONFLICT (dict_type_id, value) DO UPDATE
SET label = EXCLUDED.label,
    i18n_key = EXCLUDED.i18n_key,
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status,
    extra = EXCLUDED.extra,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_dict_item (dict_type_id, label, value, i18n_key, sort_order, status, extra)
SELECT id, src.label, src.value, src.i18n_key, src.sort_order, 'active', src.extra::jsonb
FROM sys_dict_type
JOIN (
    VALUES
        ('区域 Check QPS 利用率', 'region.check_qps_utilization', 'loadControl.metric.regionCheckQpsUtilization', 10, '{"kind":"load_scoring_metric","scope":"REGION","metricKey":"REGION_CHECK_QPS_UTILIZATION","metricType":"QPS_UTILIZATION","unit":"percent","enabled":true,"warning":60,"critical":80,"weight":5,"capacitySource":{"type":"sentinel_flow","resource":"upgrade:check","aggregation":"region_instance_count_multiply"},"schemaVersion":1}'),
        ('区域 Report QPS 利用率', 'region.report_qps_utilization', 'loadControl.metric.regionReportQpsUtilization', 20, '{"kind":"load_scoring_metric","scope":"REGION","metricKey":"REGION_REPORT_QPS_UTILIZATION","metricType":"QPS_UTILIZATION","unit":"percent","enabled":true,"warning":60,"critical":80,"weight":2,"capacitySource":{"type":"sentinel_flow","resource":"upgrade:report","aggregation":"region_instance_count_multiply"},"schemaVersion":1}'),
        ('区域 Check P50', 'region.check_p50', 'loadControl.metric.regionCheckP50', 30, '{"kind":"load_scoring_metric","scope":"REGION","metricKey":"REGION_CHECK_P50","metricType":"LATENCY","unit":"ms","enabled":true,"warning":35,"critical":70,"weight":2,"schemaVersion":1}'),
        ('区域 Check P99', 'region.check_p99', 'loadControl.metric.regionCheckP99', 40, '{"kind":"load_scoring_metric","scope":"REGION","metricKey":"REGION_CHECK_P99","metricType":"LATENCY","unit":"ms","enabled":true,"warning":60,"critical":120,"weight":4,"schemaVersion":1}'),
        ('区域 Report P50', 'region.report_p50', 'loadControl.metric.regionReportP50', 50, '{"kind":"load_scoring_metric","scope":"REGION","metricKey":"REGION_REPORT_P50","metricType":"LATENCY","unit":"ms","enabled":true,"warning":25,"critical":50,"weight":1,"schemaVersion":1}'),
        ('区域 Report P99', 'region.report_p99', 'loadControl.metric.regionReportP99', 60, '{"kind":"load_scoring_metric","scope":"REGION","metricKey":"REGION_REPORT_P99","metricType":"LATENCY","unit":"ms","enabled":true,"warning":50,"critical":100,"weight":2,"schemaVersion":1}')
) AS src(label, value, i18n_key, sort_order, extra)
ON sys_dict_type.code = 'load_control.scoring.region'
ON CONFLICT (dict_type_id, value) DO UPDATE
SET label = EXCLUDED.label,
    i18n_key = EXCLUDED.i18n_key,
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status,
    extra = EXCLUDED.extra,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_dict_item (dict_type_id, label, value, i18n_key, sort_order, status, extra)
SELECT id, src.label, src.value, src.i18n_key, src.sort_order, 'active', src.extra::jsonb
FROM sys_dict_type
JOIN (
    VALUES
        ('Flow upgrade:check', 'flow.upgrade_check', 'loadControl.sentinel.flowUpgradeCheck', 10, '{"kind":"sentinel_flow_rule","resource":"upgrade:check","grade":"QPS","count":700,"controlBehavior":"RATE_LIMITER","maxQueueingTimeMs":50,"enabled":true,"schemaVersion":1}'),
        ('Flow upgrade:report', 'flow.upgrade_report', 'loadControl.sentinel.flowUpgradeReport', 20, '{"kind":"sentinel_flow_rule","resource":"upgrade:report","grade":"QPS","count":2100,"controlBehavior":"RATE_LIMITER","maxQueueingTimeMs":50,"enabled":true,"schemaVersion":1}'),
        ('Degrade UpgradeCheckService', 'degrade.upgrade_check_service', 'loadControl.sentinel.degradeUpgradeCheckService', 30, '{"kind":"sentinel_degrade_rule","resource":"UpgradeCheckService","grade":"RT","count":50,"timeWindow":30,"minRequestAmount":100,"slowRatioThreshold":0.5,"enabled":true,"schemaVersion":1}')
) AS src(label, value, i18n_key, sort_order, extra)
ON sys_dict_type.code = 'load_control.sentinel.rule'
ON CONFLICT (dict_type_id, value) DO UPDATE
SET label = EXCLUDED.label,
    i18n_key = EXCLUDED.i18n_key,
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status,
    extra = EXCLUDED.extra,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_dict_item (dict_type_id, label, value, i18n_key, sort_order, status, extra)
SELECT id, '快照发布开关', 'snapshot.publish', 'loadControl.meta.snapshotPublish', 10, 'active',
       '{
         "kind": "load_control_publish_control",
         "enabled": false,
         "schemaVersion": 1
       }'::jsonb
FROM sys_dict_type WHERE code = 'load_control.meta'
ON CONFLICT (dict_type_id, value) DO UPDATE
SET label = EXCLUDED.label,
    i18n_key = EXCLUDED.i18n_key,
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status,
    extra = EXCLUDED.extra,
    updated_at = CURRENT_TIMESTAMP;
