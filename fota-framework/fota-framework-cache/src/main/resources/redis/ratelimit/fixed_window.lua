-- 固定窗口限流脚本
--
-- 参数:
--   KEYS[1]: 限流 key
--   ARGV[1]: 时间窗口（秒）
--
-- 返回值:
--   当前计数值
--
-- 说明:
--   脚本只负责原子递增计数器并设置过期时间，限流判定由调用方完成
--   这样设计使脚本更通用，可用于不同的限流场景

local key = KEYS[1]
local ttl = tonumber(ARGV[1])

-- 原子递增计数器
local current = redis.call('INCR', key)

-- 首次创建时设置过期时间
if current == 1 then
    redis.call('EXPIRE', key, ttl)
end

-- 返回当前计数值
return current
