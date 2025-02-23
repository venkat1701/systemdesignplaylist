package io.github.venkat1701.websitevisitcounter.utility;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Now here I have implemented the consistent hashing algorithm using MD5 MessageDigest.
 * The reason for choosing a TreeMap is that it is a R-B Tree and is best suited to form a Ring like structure. It comes from the NavigableMap. Now we also need log(n) time
 * to perform the get, put and remove operations and is best suited to fetch a shard based on its hash, even when other shards are removed.
 *
 * Moving on, the choice of using implementations of NavigableMap is due to the support of tail maps. Tail Maps return the portion of the map where the key is greater than or equal to a given key.
 * This helps us when one of the shards is removed and the requests must be directed to other shard present on the ring.
 */
@Component
public class ShardingAlgorithm {

    private final TreeMap<Long, String> ring;
    private final int numberOfReplicas;
    private final MessageDigest messageDigest;

    public ShardingAlgorithm() throws NoSuchAlgorithmException {
        this.ring = new TreeMap<>();
        this.numberOfReplicas = 2;
        this.messageDigest = MessageDigest.getInstance("MD5");
    }

    public void addServer(String server) {
        // Append replica index to differentiate multiple entries per server
        for (int i = 0; i < numberOfReplicas; i++) {
            long hash = generateHash(server + "-" + i);
            ring.put(hash, server);
        }
    }

    public String getServer(String key) {
        if (ring.isEmpty()) {
            return null;
        }
        long hash = generateHash(key);
        if (!ring.containsKey(hash)) {
            SortedMap<Long, String> tailMap = ring.tailMap(hash);
            hash = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();
        }
        return ring.get(hash);
    }

    /**
     * Generating the hash is something that is derived from a Medium article. It wasnt chatgpt.
     * @param key
     * @return
     */
    private long generateHash(String key) {
        messageDigest.reset();
        messageDigest.update(key.getBytes());
        byte[] digest = messageDigest.digest();
        // This section of code was taken from multiple articles.
        // Use the first 4 bytes to form a 32-bit integer (as a long) for a better hash distribution
        long hash = ((long)(digest[0] & 0xFF) << 24)
                | ((long)(digest[1] & 0xFF) << 16)
                | ((long)(digest[2] & 0xFF) << 8)
                | ((long)(digest[3] & 0xFF));
        return hash & 0xffffffffL; // this basically ensures it isnt negative.
    }
}
