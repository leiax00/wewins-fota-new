-- 配额检查并递增脚本
--
-- 参数:
--   KEYS[1]: 配额 key
--   ARGV[1]: 最大配额数
--   ARGV[2]: 过期时间（秒）
--
-- 返回值:
--   1 - 配额可用且已递增
--   0 - 配额已用尽
--
-- 说明:
--   原子操作：先检查配额是否已满，未满则递增计数器
--   首次创建时自动设置过期时间，实现每日配额自动重置

local key = KEYS[1]
local maxQuota = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])

-- 获取当前配额使用量
local current = redis.call('GET', key)

-- 如果 key 不存在，初始化为 0
if current == false then
    current = 0
else
    current = tonumber(current)
end

-- 检查配额是否已用尽
if current >= maxQuota then
    return 0
end

-- 配额未用尽，递增计数器
local newCount = redis.call('INCR', key)

-- 首次创建时设置过期时间
if newCount == 1 then
    redis.call('EXPIRE', key, ttl)
end

-- 返回成功
return 1
