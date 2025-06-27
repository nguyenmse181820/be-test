package com.boeing.flightservice.service.cache;

import com.boeing.flightservice.exception.BadRequestException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SeatPriceCacheService {

    private final Cache<String, Double> cache;

    public SeatPriceCacheService() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
    }

    public void put(String key, Double value) {
        log.info("put: key={}, value={}", key, value);
        cache.put(key, value);
    }

    public Double get(String key) {
        return cache.getIfPresent(key);
    }
}
