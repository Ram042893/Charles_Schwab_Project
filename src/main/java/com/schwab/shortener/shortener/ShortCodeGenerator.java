package com.schwab.shortener.shortener;

import com.schwab.shortener.config.AppProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ShortCodeGenerator {

    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final AppProperties properties;

    public ShortCodeGenerator(AppProperties properties) {
        this.properties = properties;
    }

    public String next() {
        int length = properties.shortener().codeLength();
        char[] buffer = new char[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(buffer);
    }
}
