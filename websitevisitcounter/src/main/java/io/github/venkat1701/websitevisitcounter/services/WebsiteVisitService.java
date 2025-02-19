package io.github.venkat1701.websitevisitcounter.services;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class WebsiteVisitService {

    private final Cache<String, AtomicInteger> localCache;
    private final RedisTemplate<String, Integer> redisTemplate;
    private final Map<String, AtomicInteger> buffer = new ConcurrentHashMap<>();
    public WebsiteVisitService(Cache<String, AtomicInteger> localCache, RedisTemplate<String, Integer> redisTemplate) {
        this.localCache = localCache;
        this.redisTemplate = redisTemplate;
    }

    public void incrementVisit(String pageNumber) {
        this.buffer.computeIfAbsent(pageNumber, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    public int getVisitCount(String pageNumber) {
        int redisCount = 0;
        Number redisVal = this.redisTemplate.opsForValue().get(pageNumber);
        if (redisVal != null) {
            redisCount = redisVal.intValue();
        }

        var localAtomicValue = this.localCache.getIfPresent(pageNumber);
        int localCount = (localAtomicValue != null ? localAtomicValue.get() : 0);
        var bufferVal = this.buffer.get(pageNumber);
        int bufferCount = (bufferVal != null ? bufferVal.get() : 0);
        return redisCount + localCount + bufferCount;
    }

    @Scheduled(fixedRate = 5000)
    public void flushToLocalCache() {
        for(Map.Entry<String, AtomicInteger> entry : this.buffer.entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue().get();
            if(count > 0) {
                this.localCache.put(key, new AtomicInteger(entry.getValue().get()+this.localCache.asMap().getOrDefault(key, new AtomicInteger(0)).get()));
            }
        }
        this.buffer.clear();
    }


    @Scheduled(fixedRate = 20000)
    public void flushToRedis() {
        for (Map.Entry<String, AtomicInteger> entry : localCache.asMap().entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue().get();
            if (count > 0) {
                redisTemplate.opsForValue().increment(key, count);
                localCache.invalidate(key);
            }
        }
    }

}
