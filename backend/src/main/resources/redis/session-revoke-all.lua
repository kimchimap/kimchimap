for _, sid in ipairs(redis.call('SMEMBERS', KEYS[1])) do
  local key = ARGV[1] .. sid
  if redis.call('EXISTS', key) == 1 then redis.call('HSET', key, 'status', 'REVOKED') end
end
return 1
