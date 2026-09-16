package com.schwab.shortener.shortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FeatureFlagServiceTest {

    @Autowired
    private FeatureFlagService featureFlags;

    @Test
    void seedsDefaultFlagsOffAndAllowsToggle() {
        assertThat(featureFlags.isEnabled(FeatureFlagService.CUSTOM_ALIAS)).isFalse();
        assertThat(featureFlags.isEnabled(FeatureFlagService.EXPIRATION)).isFalse();
        assertThat(featureFlags.isEnabled(FeatureFlagService.ANALYTICS_EXPORT)).isFalse();

        featureFlags.setEnabled(FeatureFlagService.CUSTOM_ALIAS, true);
        assertThat(featureFlags.isEnabled(FeatureFlagService.CUSTOM_ALIAS)).isTrue();

        featureFlags.setEnabled(FeatureFlagService.CUSTOM_ALIAS, false);
        assertThat(featureFlags.isEnabled(FeatureFlagService.CUSTOM_ALIAS)).isFalse();
    }
}
