local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local expiry = now + tonumber(ARGV[5])
for _, sid in ipairs(redis.call('SMEMBERS', KEYS[2])) do
  if redis.call('HGET', ARGV[6] .. sid, 'status') ~= 'ACTIVE' then redis.call('SREM', KEYS[2], sid) end
end
if redis.call('SCARD', KEYS[2]) >= 20 then return 'LIMIT' end
if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[3]) == 1 then return 'COLLISION' end
redis.call('HSET', KEYS[1], 'status', 'ACTIVE', 'member', ARGV[2], 'version', ARGV[3], 'current', ARGV[4], 'expiry', expiry)
redis.call('PEXPIREAT', KEYS[1], expiry)
redis.call('SET', KEYS[3], ARGV[1], 'PXAT', expiry)
redis.call('SADD', KEYS[2], ARGV[1])
local ttl = redis.call('PTTL', KEYS[2])
if ttl < tonumber(ARGV[5]) then redis.call('PEXPIREAT', KEYS[2], expiry) end
return tostring(expiry)
