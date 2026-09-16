package com.schwab.shortener.shortener;

import com.schwab.shortener.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    @Test
    void generatesCodesOfConfiguredLengthAndAlphabet() {
        ShortCodeGenerator generator = new ShortCodeGenerator(props(8));
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String code = generator.next();
            assertThat(code).hasSize(8).matches("[A-Za-z0-9]+");
            codes.add(code);
        }
        assertThat(codes.size()).isGreaterThan(40);
    }

    private static AppProperties props(int length) {
        return new AppProperties(
                new AppProperties.Jwt("test-secret-key-must-be-at-least-thirty-two-chars", "test", Duration.ofMinutes(15), Duration.ofDays(1)),
                new AppProperties.Shortener(length, Duration.ofMinutes(10), 60, false),
                new AppProperties.Orchestration(3, Duration.ofMillis(10), false),
                new AppProperties.Security(java.util.List.of())
        );
    }
}
