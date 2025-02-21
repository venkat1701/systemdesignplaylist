package io.github.venkat1701.websitevisitcounter.utility;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SortedMap;
import java.util.TreeMap;

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
        for(int i=0; i<numberOfReplicas; i++) {
            long hash = this.generateHash(server);
            this.ring.put(hash, server);
        }
    }

    public String getServer(String key) {
        if(this.ring.isEmpty()) {
            return null;
        }

        long hash = this.generateHash(key);
        if (!this.ring.containsKey(hash)) {
            SortedMap<Long, String> tailMap = this.ring.tailMap(hash);
            hash = tailMap.isEmpty() ? this.ring.firstKey() : tailMap.firstKey();
        }
        return this.ring.get(hash);
    }

    private long generateHash(String key) {
        this.messageDigest.reset();
        this.messageDigest.update(key.getBytes());
        byte[] digest = this.messageDigest.digest();
        return digest[0];
    }
}
