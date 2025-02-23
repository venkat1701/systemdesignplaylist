package io.github.venkat1701.websitevisitcounter.services;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.venkat1701.websitevisitcounter.configurations.RedisShardManager;
import io.github.venkat1701.websitevisitcounter.dto.WebsiteVisitDTO;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Krish Jaiswal
 */
@Service
public class WebsiteVisitService {

    private final Cache<String, AtomicInteger> localCache;
    private final RedisShardManager redisShardManager;
    private final Map<String, AtomicInteger> buffer = new ConcurrentHashMap<>();

    public WebsiteVisitService(Cache<String, AtomicInteger> localCache, RedisShardManager redisShardManager) {
        this.localCache = localCache;
        this.redisShardManager = redisShardManager;
    }

    /**
     * Basically this method increments the buffer atomically. This buffer is required to temporarily store the atomic counter.
     * The reason of making the counter of the Buffer and localcache to be atomic is because, it is going to handle multiple, concurrent requests, which
     * needs the use of a mutex(which in java is called an atomic variable)
     * @param pageNumber The page number on which the counter has to be increased.
     */
    public void incrementVisit(String pageNumber) {
        buffer.computeIfAbsent(pageNumber, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    /**
     * This method is responsible for returning the count of visits on a particular page. Now there can be multiple edge cases. What are those?
     * 1. First is that the buffer already has some value in itself. But since the TTL of buffer is set to 5seconds, so there might not be any value, in case of which we set it to be 0.
     * 2. Secondly, we need to fetch the value present in the localCache for the given page number.
     *      Here it may or may not exist. If it doesnt, then localAtomicValue stores null. Based on the check, we retrieve the value to store in an integer.
     * 3. So the 1st and 2nd point combines to create a totalInMemory counter(all of which is served atomically). If a pageCounter has been created and then queried instantly, then we serve using inMemory buffer and cache.
     * 4. But if the buffer and localCache are empty, then we need to fetch that from the Redis Shard.
     * Although if you closely imagine, there's a logical error here. If a pageNumber counter exists in the redis shard, and then POST request is sent multiple times, and then fetched within the local buffer period
     * then a wrong value is returned. This I'll fix soon.
     * @param pageNumber
     * @return
     */
    public WebsiteVisitDTO getVisitResult(String pageNumber) {
        int bufferCount = buffer.getOrDefault(pageNumber, new AtomicInteger(0)).get();
        AtomicInteger localAtomicValue = localCache.getIfPresent(pageNumber);
        int localCount = (localAtomicValue != null ? localAtomicValue.get() : 0);
        int totalInMemory = bufferCount + localCount;
        if (totalInMemory > 0) {
            return new WebsiteVisitDTO(totalInMemory, "in_memory");
        } else {
            RedisTemplate<String, Integer> shardTemplate = redisShardManager.getShard(pageNumber);
            String shardId = redisShardManager.getShardId(pageNumber);
            Integer redisVal = shardTemplate.opsForValue().get(pageNumber);
            int redisCount = (redisVal != null ? redisVal : 0);
            return new WebsiteVisitDTO(redisCount+totalInMemory, shardId);
        }
    }

    /**
     * This simply flushes the buffer to local cache every 5 seconds.
     */
    @Scheduled(fixedRate = 5000)
    public void flushToLocalCache() {
        for (Map.Entry<String, AtomicInteger> entry : buffer.entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue().get();
            if (count > 0) {
                localCache.put(key, new AtomicInteger(
                        entry.getValue().get() + localCache.asMap().getOrDefault(key, new AtomicInteger(0)).get()));
            }
        }
        buffer.clear();
    }

    /**
     * This simply flushes the localcache to redis every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void flushToRedis() {
        for (Map.Entry<String, AtomicInteger> entry : localCache.asMap().entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue().get();
            if (count > 0) {
                RedisTemplate<String, Integer> shardTemplate = redisShardManager.getShard(key);
                shardTemplate.opsForValue().increment(key, count);
                localCache.invalidate(key);
            }
        }
    }
}
