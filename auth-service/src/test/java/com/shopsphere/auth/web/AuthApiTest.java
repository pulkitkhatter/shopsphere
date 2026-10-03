package com.shopsphere.auth.web;

import com.shopsphere.auth.config.SecurityConfig;
import com.shopsphere.auth.model.User;
import com.shopsphere.auth.service.AuthService;
import com.shopsphere.auth.service.OAuthException;
import com.shopsphere.auth.service.TokenResponse;
import com.shopsphere.common.web.ApiExceptionHandler;
import com.shopsphere.common.web.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, TokenController.class, UserInfoController.class})
@Import({SecurityConfig.class, ApiExceptionHandler.class, OAuthExceptionHandler.class})
class AuthApiTest {

    @Autowired MockMvc mvc;
    @MockBean AuthService auth;
    @MockBean JwtDecoder jwtDecoder;

    @Test
    void register_returns201_withoutLeakingPasswordHash() throws Exception {
        User saved = new User("bob", "b@x.io", "HASH", Set.of("USER"));
        saved.setId("1");
        when(auth.register("bob", "b@x.io", "s3cret-password")).thenReturn(saved);

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"email\":\"b@x.io\",\"password\":\"s3cret-password\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_withWeakPasswordAndBadEmail_returns400ProblemDetail() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"email\":\"nope\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void register_duplicate_returns409() throws Exception {
        when(auth.register("bob", "b@x.io", "s3cret-password")).thenThrow(new ConflictException("Username or email already in use"));

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"email\":\"b@x.io\",\"password\":\"s3cret-password\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void token_passwordGrant_returnsOAuth2Response_andForbidsCaching() throws Exception {
        when(auth.passwordGrant("bob", "pw")).thenReturn(new TokenResponse("AT", "Bearer", 900, "RT", "openid"));

        mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password").param("username", "bob").param("password", "pw"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.access_token").value("AT"))
                .andExpect(jsonPath("$.refresh_token").value("RT"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900));
    }

    @Test
    void token_badCredentials_returnsRfc6749Error() throws Exception {
        when(auth.passwordGrant("bob", "bad")).thenThrow(OAuthException.invalidGrant("Bad credentials"));

        mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password").param("username", "bob").param("password", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void token_unsupportedGrant_isRejected() throws Exception {
        mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "implicit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_grant_type"));
    }

    @Test
    void userinfo_requiresAuthentication() throws Exception {
        mvc.perform(get("/userinfo")).andExpect(status().isUnauthorized());
    }

    @Test
    void userinfo_returnsClaimsForValidToken() throws Exception {
        mvc.perform(get("/userinfo").with(jwt().jwt(j -> j.subject("bob").claim("email", "b@x.io").claim("roles", java.util.List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value("bob"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }
}
