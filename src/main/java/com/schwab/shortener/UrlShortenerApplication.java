package com.schwab.shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(excludeName = {
        "org.springframework.boot.data.redis.autoconfigure.RedisAutoConfiguration",
        "org.springframework.boot.data.redis.autoconfigure.RedisReactiveAutoConfiguration",
        "org.springframework.boot.data.redis.autoconfigure.RedisRepositoriesAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@EnableCaching
@EnableAsync
@ConfigurationPropertiesScan
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
