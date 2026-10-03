package com.shopsphere.auth.service;

import com.shopsphere.auth.model.User;
import com.shopsphere.auth.repo.UserRepository;
import com.shopsphere.common.web.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock TokenService tokens;
    // low cost factor keeps the test fast; behaviour is identical
    final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder, tokens);
    }

    @Test
    void register_hashesPassword_andAlwaysAssignsUserRole() {
        when(users.existsByUsername("bob")).thenReturn(false);
        when(users.existsByEmail("bob@x.io")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User saved = service.register("bob", "Bob@X.io", "s3cret-password");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("s3cret-password");
        assertThat(encoder.matches("s3cret-password", captor.getValue().getPasswordHash())).isTrue();
        assertThat(saved.getRoles()).containsExactly("USER");
        assertThat(saved.getEmail()).isEqualTo("bob@x.io");
    }

    @Test
    void register_rejectsDuplicateUsername() {
        when(users.existsByUsername("bob")).thenReturn(true);

        assertThatThrownBy(() -> service.register("bob", "b@x.io", "s3cret-password"))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
    }

    @Test
    void passwordGrant_withCorrectCredentials_issuesTokens() {
        User user = new User("bob", "b@x.io", encoder.encode("s3cret-password"), Set.of("USER"));
        TokenResponse response = new TokenResponse("a", "Bearer", 900, "r", "openid");
        when(users.findByUsername("bob")).thenReturn(Optional.of(user));
        when(tokens.issue(user)).thenReturn(response);

        assertThat(service.passwordGrant("bob", "s3cret-password")).isSameAs(response);
    }

    @Test
    void passwordGrant_wrongPassword_isInvalidGrant() {
        User user = new User("bob", "b@x.io", encoder.encode("s3cret-password"), Set.of("USER"));
        when(users.findByUsername("bob")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.passwordGrant("bob", "wrong"))
                .isInstanceOfSatisfying(OAuthException.class, e -> assertThat(e.getError()).isEqualTo("invalid_grant"));
        verify(tokens, never()).issue(any());
    }

    @Test
    void passwordGrant_unknownUser_givesSameErrorAsWrongPassword() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.passwordGrant("ghost", "whatever-password"))
                .isInstanceOfSatisfying(OAuthException.class, e -> {
                    assertThat(e.getError()).isEqualTo("invalid_grant");
                    assertThat(e.getMessage()).isEqualTo("Bad credentials");
                });
    }

    @Test
    void passwordGrant_disabledUser_isRejectedEvenWithCorrectPassword() {
        User user = new User("bob", "b@x.io", encoder.encode("s3cret-password"), Set.of("USER"));
        user.setEnabled(false);
        when(users.findByUsername("bob")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.passwordGrant("bob", "s3cret-password")).isInstanceOf(OAuthException.class);
    }

    @Test
    void refreshGrant_rotatesToken_forActiveUser() {
        User user = new User("bob", "b@x.io", "hash", Set.of("USER"));
        TokenResponse response = new TokenResponse("a2", "Bearer", 900, "r2", "openid");
        when(tokens.consumeRefreshToken("r1")).thenReturn("bob");
        when(users.findByUsername("bob")).thenReturn(Optional.of(user));
        when(tokens.issue(user)).thenReturn(response);

        assertThat(service.refreshGrant("r1")).isSameAs(response);
    }
}
