package com.shopsphere.auth.service;

import com.shopsphere.auth.model.User;
import com.shopsphere.auth.repo.UserRepository;
import com.shopsphere.common.web.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    /** Hash compared against when the username is unknown, so response time does not reveal which users exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, TokenService tokens) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    public User register(String username, String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();
        if (users.existsByUsername(username) || users.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Username or email already in use");
        }
        // self-registration can only ever create a plain USER; admins are provisioned out of band
        User user = new User(username, normalizedEmail, encoder.encode(password), Set.of("USER"));
        try {
            return users.save(user);
        } catch (DuplicateKeyException e) {      // lost a race against a concurrent registration
            throw new ConflictException("Username or email already in use");
        }
    }

    public TokenResponse passwordGrant(String username, String password) {
        User user = users.findByUsername(username).orElse(null);
        // always run a hash comparison so response time does not reveal whether a username exists (user enumeration)
        boolean ok = encoder.matches(password, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !ok || !user.isEnabled()) {
            log.warn("Failed login for username='{}'", sanitize(username));
            throw OAuthException.invalidGrant("Bad credentials");
        }
        return tokens.issue(user);
    }

    public TokenResponse refreshGrant(String refreshToken) {
        String username = tokens.consumeRefreshToken(refreshToken);
        User user = users.findByUsername(username)
                .filter(User::isEnabled)
                .orElseThrow(() -> OAuthException.invalidGrant("User no longer active"));
        return tokens.issue(user);
    }

    private static String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n\\t]", "_");   // prevent log forging
    }
}
