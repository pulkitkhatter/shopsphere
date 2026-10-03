package com.shopsphere.auth.config;

import com.shopsphere.auth.model.User;
import com.shopsphere.auth.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Creates demo accounts when shopsphere.auth.seed.enabled=true (dev profile only). Passwords come from config. */
@Component
@ConditionalOnProperty(prefix = "shopsphere.auth.seed", name = "enabled", havingValue = "true")
class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthProperties props;

    DevDataSeeder(UserRepository users, PasswordEncoder encoder, AuthProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("admin", "admin@shopsphere.local", props.seed().adminPassword(), Set.of("ADMIN", "USER"));
        seed("alice", "alice@shopsphere.local", props.seed().userPassword(), Set.of("USER"));
    }

    private void seed(String username, String email, String password, Set<String> roles) {
        if (password == null || password.isBlank() || users.existsByUsername(username)) return;
        users.save(new User(username, email, encoder.encode(password), roles));
        log.info("Seeded demo user '{}'", username);
    }
}
