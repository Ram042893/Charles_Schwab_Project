package com.schwab.shortener.shortener;

import com.schwab.shortener.config.CacheConfig;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureFlagService implements ApplicationRunner {

    public static final String CUSTOM_ALIAS = "CUSTOM_ALIAS";
    public static final String EXPIRATION = "EXPIRATION";
    public static final String ANALYTICS_EXPORT = "ANALYTICS_EXPORT";

    private final FeatureFlagRepository repository;

    public FeatureFlagService(FeatureFlagRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed(CUSTOM_ALIAS, false, "Allow custom short aliases");
        seed(EXPIRATION, false, "Allow expiring short links");
        seed(ANALYTICS_EXPORT, false, "Allow analytics export");
    }

    @Cacheable(cacheNames = CacheConfig.FEATURE_FLAGS, key = "#name")
    @Transactional(readOnly = true)
    public boolean isEnabled(String name) {
        return repository.findById(name).map(FeatureFlag::isEnabled).orElse(false);
    }

    @CacheEvict(cacheNames = CacheConfig.FEATURE_FLAGS, key = "#name")
    @Transactional
    public void setEnabled(String name, boolean enabled) {
        FeatureFlag flag = repository.findById(name).orElseGet(() -> {
            FeatureFlag created = new FeatureFlag();
            created.setName(name);
            return created;
        });
        flag.setEnabled(enabled);
        repository.save(flag);
    }

    private void seed(String name, boolean enabled, String description) {
        if (repository.existsById(name)) {
            return;
        }
        FeatureFlag flag = new FeatureFlag();
        flag.setName(name);
        flag.setEnabled(enabled);
        flag.setDescription(description);
        repository.save(flag);
    }
}
