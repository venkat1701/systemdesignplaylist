package io.github.venkat1701.websitevisitcounter.configurations;


import io.github.venkat1701.websitevisitcounter.utility.ShardingAlgorithm;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * This ShardManager is responsible to store the metadata of shards(aka the number of shards and name of shards)
 * so as to display them during the delivery of requests and also add servers to the sharding algorithm so that consistent hashing
 * could be better implemented.
 * @author Krish Jaiswal
 */
@Component
public class RedisShardManager {

    private final Map<String, RedisTemplate<String, Integer>> shards = new HashMap<>();
    private final ShardingAlgorithm shardingAlgorithm;

    public RedisShardManager(@Qualifier("redisTemplate7070") RedisTemplate<String, Integer> redisTemplate7070,
                             @Qualifier("redisTemplate7071") RedisTemplate<String, Integer> redisTemplate7071,
                             ShardingAlgorithm shardingAlgorithm) {
        this.shardingAlgorithm = shardingAlgorithm;
        shards.put("redis_7070", redisTemplate7070);
        shards.put("redis_7071", redisTemplate7071);
        this.shardingAlgorithm.addServer("redis_7070");
        this.shardingAlgorithm.addServer("redis_7071");
    }

    public RedisTemplate<String, Integer> getShard(String key) {
        String shardId = shardingAlgorithm.getServer(key);
        return shards.get(shardId);
    }

    public String getShardId(String key) {
        return shardingAlgorithm.getServer(key);
    }

    public void addShard(String shardId, RedisTemplate<String, Integer> redisTemplate) {
        shards.put(shardId, redisTemplate);
        shardingAlgorithm.addServer(shardId);
    }
}
