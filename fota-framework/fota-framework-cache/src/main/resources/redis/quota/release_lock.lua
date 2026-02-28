-- 分布式锁释放脚本
--
-- 参数:
--   KEYS[1]: 锁 key
--   ARGV[1]: 锁 token
--
-- 返回值:
--   1 - 释放成功
--   0 - 释放失败（不是持有者或锁已过期）
--
-- 说明:
--   原子操作：只有持有正确 token 的请求才能释放锁
--   防止误删其他线程持有的锁

local key = KEYS[1]
local token = ARGV[1]

-- 获取当前锁的持有者 token
local currentToken = redis.call('GET', key)

-- 锁不存在或已过期
if currentToken == false then
    return 0
end

-- 只有持有者才能释放锁
if currentToken == token then
    redis.call('DEL', key)
    return 1
end

-- 不是持有者，拒绝释放
return 0
