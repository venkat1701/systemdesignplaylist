package io.github.venkat1701.websitevisitcounter.configurations;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * So basically, the entire idea of creating 2 shards and also a local cache is done with the help of Caffeine Cache and RedisConnectionFactory.
 * Now there are multiple reasons for using Caffeine as a local cache, but the reason of it being most widely used is why I've used it.
 * Also, in order to set up 2 shards of Redis, we use a asynchronous connection factory where we have two redis connection, one running at 7070 and another at 7071.
 * The two ports have been mentioned even in the docker compose file.
 * @author Krish Jaiswal.
 */
@Configuration
@EnableCaching
public class RedisConfiguration {

    /**
     * We are setting the TTL of the local cache to be 5 minutes. Actually, it can be much lesser. We just want the local cache to hold values until we flush it out into the Redis Shard.
     * @return Bean of Caffeine Cache
     */
    @Bean
    public Cache<String, AtomicInteger> localCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
    }

    /**
     * Lettuce is a scalable, thread safe, redis client for sync/async and reactive usage. So a LettuceConnectionFactory sits below in hierarchy of RedisConnectionFactory,
     * kind of like an abstraction built on top of RedisConnectionFactory that helps manage multiple redis clusters or even single redis sentinels.
     * We create a standalone instance provided with the hostname and port and also set this as a primary bean so that Spring doesnt get confused which one to qualify during the usage,
     * and gives preference to this bean.
     * @return
     */
    @Bean(name = "redisConnectionFactory7070")
    @Primary
    public RedisConnectionFactory redisConnectionFactory7070() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 7070);
        return new LettuceConnectionFactory(config);
    }

    /**
     * Same applies here as well. Everytime we create a new LettuceConnectionFactory, it just adds the shard into the connection pool.
     * @return
     */
    @Bean(name = "redisConnectionFactory7071")
    public RedisConnectionFactory redisConnectionFactory7071() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 7071);
        return new LettuceConnectionFactory(config);
    }

    /**
     * Basically, a RedisTemplate is a helper class that helps us with a lot of utility stuff. So there are two beans, one for 7070 and another for 7071.
     * @param redisConnectionFactory7070
     * @return
     */
    @Bean(name = "redisTemplate7070")
    public RedisTemplate<String, Integer> redisTemplate7070(RedisConnectionFactory redisConnectionFactory7070) {
        RedisTemplate<String, Integer> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory7070);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }


    @Bean(name = "redisTemplate7071")
    public RedisTemplate<String, Integer> redisTemplate7071(RedisConnectionFactory redisConnectionFactory7071) {
        RedisTemplate<String, Integer> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory7071);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
