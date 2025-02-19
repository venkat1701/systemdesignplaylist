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
//        this.localCache.asMap()
//                .computeIfAbsent(pageNumber, k -> new AtomicInteger(0))
//                .incrementAndGet();
    }


    public int getVisitCount(String pagenumber) {
        var bufferCount = this.buffer.getOrDefault(pagenumber, new AtomicInteger(0)).get();

        var localCount = this.localCache.getIfPresent(pagenumber);
        if (localCount != null) {
            return localCount.get()+bufferCount;
        } else {
            Number redisValue = this.redisTemplate.opsForValue().get(pagenumber);
            if (redisValue != null) {
                int visitCount = redisValue.intValue()+bufferCount;
                return visitCount+redisValue.intValue();
            }

            this.localCache.put(pagenumber, new AtomicInteger(redisValue.intValue()+bufferCount));
            return 0;
        }
    }


    public void evictCache(String pageNumber) {
        this.redisTemplate.delete(pageNumber);
        this.localCache.invalidate(pageNumber);
    }

    @Scheduled(fixedRate = 5000)
    public void flushToRedis() {

        //flushing the buffer to redis
        for(var entry : this.buffer.entrySet()) {
            this.redisTemplate.opsForValue().increment(entry.getKey(), entry.getValue().intValue());
        }

        this.buffer.clear();

//        for (String key : this.localCache.asMap().keySet()) {
//            var visits = this.localCache.getIfPresent(key);
//            if (visits != null) {
//                this.redisTemplate.opsForValue().increment(key, visits.get());
//                this.localCache.invalidate(key);
//            }
//        }
    }

}
