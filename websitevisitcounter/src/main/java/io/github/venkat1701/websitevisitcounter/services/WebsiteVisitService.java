package io.github.venkat1701.websitevisitcounter.services;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class WebsiteVisitService {

    private final Cache<String, AtomicInteger> localCache;
    private final RedisTemplate<String, Integer> redisTemplate;

    public WebsiteVisitService(Cache<String, AtomicInteger> localCache, RedisTemplate<String, Integer> redisTemplate) {
        this.localCache = localCache;
        this.redisTemplate = redisTemplate;
    }

    public void incrementVisit(String pageNumber) {
        this.localCache.asMap()
                .computeIfAbsent(pageNumber, k -> new AtomicInteger(0))
                .incrementAndGet();
    }


    public int getVisitCount(String pagenumber) {
        var localCount = this.localCache.getIfPresent(pagenumber);
        if (localCount != null) {
            return localCount.get();
        } else {
            Number redisValue = this.redisTemplate.opsForValue().get(pagenumber);
            if (redisValue != null) {
                return new AtomicInteger(redisValue.intValue()).get();
            }
            return 0;
        }
    }


    public void evictCache(String pageNumber) {
        this.redisTemplate.delete(pageNumber);
        this.localCache.invalidate(pageNumber);
    }

    @Scheduled(fixedRate = 5000)
    public void flushToRedis() {
        for (String key : this.localCache.asMap().keySet()) {
            var visits = this.localCache.getIfPresent(key);
            if (visits != null) {
                this.redisTemplate.opsForValue().increment(key, visits.get());
                this.localCache.invalidate(key);
            }
        }
    }

}
