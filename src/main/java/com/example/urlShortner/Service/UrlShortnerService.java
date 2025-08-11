package com.example.urlShortner.Service;

import com.example.urlShortner.Domain.UrlMapping;
import com.example.urlShortner.Repository.UrlMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UrlShortnerService {
    private int maxLength = 7;

    @Value("${server.port}")
    private String port;

    private String baseUrl = "http://localhost:" + port + "/";


    @Autowired
    private UrlMappingRepository urlMappingRepository;
    private Long lastTimestamp = -1L;
    private Long sequence = 0L;
    private final long CUSTOM_EPOCH = 1609459200000L;// Custom epoch

    private static final long SEQUENCE_BITS = 12L; // 4096 unique IDs per ms per worker
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long MAX_SEQUENCE = -1L ^ (-1L << SEQUENCE_BITS);
    // Max worker ID and datacenter ID are not strictly enforced here but are good practice for bit allocation
    // private static final long MAX_WORKER_ID = -1L ^ (-1L << WORKER_ID_BITS);
    // private static final long MAX_DATACENTER_ID = -1L ^ (-1L << DATACENTER_ID_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private String ALPHANUMERIC_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * Generates a unique 64-bit ID (simplified Snowflake-like).
     * This method is thread-safe.
     *
     * The ID structure is:
     * 1 bit (sign, always 0) + 41 bits (timestamp) + 5 bits (datacenter ID) + 5 bits (worker ID) + 12 bits (sequence)
     * This allows for IDs for about 69 years from the CUSTOM_EPOCH.
     *
     * @return A unique long ID.
     */
    private synchronized long generateUniqueId(long dataCenterId, long workerId) {
        long timestamp = timeGen();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException(String.format(
                    "Clock moved backwards. Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
        }

        if (lastTimestamp == timestamp) {
            // Same timestamp, increment sequence
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence overflow, wait until next millisecond
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // New timestamp, reset sequence
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_LEFT_SHIFT) |
                (dataCenterId << DATACENTER_ID_SHIFT) |
                (workerId << WORKER_ID_SHIFT) |
                sequence;
    }

    /**
     * Waits until the next millisecond to avoid sequence overflow.
     * @param lastTimestamp The last timestamp generated.
     * @return The current timestamp in milliseconds.
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * Returns the current timestamp in milliseconds.
     * @return Current time in milliseconds.
     */
    private long timeGen() {
        return System.currentTimeMillis();
    }

    private String encodeToBase62(long id) {
        if (id == 0) {
            return String.valueOf(ALPHANUMERIC_CHARS.charAt(0)); // Handle ID 0 specifically
        }

        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(ALPHANUMERIC_CHARS.charAt((int) (id % 62)));
            id /= 62;
        }
        return sb.reverse().toString();
    }



    public String generateShortUrl(String originalUrl, long dataCenterId, long workerId) {
        if (originalUrl == null || originalUrl.isEmpty()) {
            throw new IllegalArgumentException("Original URL cannot be null or empty");
        }
            if (dataCenterId < 0 || dataCenterId > 31) {
                throw new IllegalArgumentException("Data center ID must be between 0 and 31");
            }

        if (workerId < 0 || workerId > 31) {
            throw new IllegalArgumentException("Worker ID must be between 0 and 31");
        }

        UrlMapping domain = new UrlMapping();
        domain.setOriginalUrl(originalUrl);
        domain.setCreatedAt(LocalDateTime.now());
        long id = generateUniqueId(dataCenterId, workerId);
        domain.setId(id);
        domain.setShortUrl(encodeToBase62(id));
        UrlMapping result = urlMappingRepository.save(domain);


        return  baseUrl + result.getShortUrl() ;
    }

    @Cacheable(value = "shortUrlCache", key = "#shortUrl")
    public Optional<String> getOriginalUrl(String shortUrl) {
        return urlMappingRepository.findByShortUrl(shortUrl)
                .map(UrlMapping::getOriginalUrl);
    }

}
