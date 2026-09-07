if redis.call('GET', KEYS[2]) ~= ARGV[1] then return 'INVALID' end
if redis.call('HGET', KEYS[1], 'status') ~= 'ACTIVE' then return 'INVALID' end
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local expiry = tonumber(redis.call('HGET', KEYS[1], 'expiry') or '0')
if expiry <= now then return 'INVALID' end
if redis.call('HGET', KEYS[1], 'current') ~= ARGV[2] then
  redis.call('HSET', KEYS[1], 'status', 'REVOKED')
  return 'REUSED'
end
if redis.call('EXISTS', KEYS[3]) == 1 then return 'COLLISION' end
redis.call('SET', KEYS[3], ARGV[1], 'PXAT', expiry)
redis.call('HSET', KEYS[1], 'current', ARGV[3])
return 'OK'
