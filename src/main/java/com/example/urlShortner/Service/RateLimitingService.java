package com.example.urlShortner.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Service
public class RateLimitingService implements HandlerInterceptor {
    private static final int RATE_LIMIT = 5;
    private static final long TIME_WINDOW_IN_SECONDS = 60;
    private static final String REDIS_KEY_PREFIX = "rate-limit:";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = request.getRemoteAddr();
        String redisKey = REDIS_KEY_PREFIX + clientIp;

        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        long currentTime = System.currentTimeMillis();
        long windowStartTime = currentTime - (TIME_WINDOW_IN_SECONDS * 1000);

        zSetOps.removeRangeByScore(redisKey, 0, windowStartTime);

        Long requestCount = zSetOps.zCard(redisKey);

        if (requestCount != null && requestCount >= RATE_LIMIT) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Rate limit exceeded. Please try again later.");
            return false;
        }

        zSetOps.add(redisKey, String.valueOf(currentTime), currentTime);

        redisTemplate.expire(redisKey, Duration.ofSeconds(TIME_WINDOW_IN_SECONDS));

        return true;
    }
}