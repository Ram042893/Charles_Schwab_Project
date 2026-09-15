package com.schwab.shortener.identity;

import com.schwab.shortener.config.AppProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IdentitySeeder implements ApplicationRunner {

    private final AppProperties properties;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public IdentitySeeder(AppProperties properties, AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (AppProperties.DemoUser demo : properties.security().demoUsers()) {
            users.findByUsernameIgnoreCase(demo.username()).orElseGet(() -> {
                AppUser user = new AppUser();
                user.setUsername(demo.username());
                user.setPasswordHash(passwordEncoder.encode(demo.password()));
                user.setRoles(demo.roles());
                user.setEnabled(true);
                return users.save(user);
            });
        }
    }
}
