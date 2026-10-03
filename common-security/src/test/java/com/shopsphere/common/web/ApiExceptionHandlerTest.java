package com.shopsphere.common.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTest {

    record Body(@NotBlank String name) {}

    @RestController
    static class Boom {
        @GetMapping("/missing") void missing() { throw new NotFoundException("thing 7 not found"); }
        @GetMapping("/conflict") void conflict() { throw new ConflictException("already there"); }
        @GetMapping("/down") void down() { throw new ServiceUnavailableException("dependency down", new RuntimeException("secret internals")); }
        @GetMapping("/crash") void crash() { throw new IllegalStateException("db password is hunter2"); }
        @PostMapping("/valid") void valid(@Valid @RequestBody Body body) { }
    }

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Boom()).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void notFound_is404ProblemJson() throws Exception {
        mvc.perform(get("/missing")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("thing 7 not found"));
    }

    @Test
    void conflict_is409() throws Exception {
        mvc.perform(get("/conflict")).andExpect(status().isConflict());
    }

    @Test
    void serviceUnavailable_is503_withoutLeakingTheCause() throws Exception {
        mvc.perform(get("/down")).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret internals"))));
    }

    @Test
    void validationFailure_is400_withFieldErrors() throws Exception {
        mvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void malformedJson_is400() throws Exception {
        mvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unexpectedError_is500_andNeverLeaksTheMessage() throws Exception {
        mvc.perform(get("/crash")).andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hunter2"))));
    }

    @Test
    void unknownUrl_is404_notA500() throws Exception {
        MockMvc withResources = MockMvcBuilders.standaloneSetup(new Boom()).setControllerAdvice(new ApiExceptionHandler())
                .addDispatcherServletCustomizer(d -> d.setThrowExceptionIfNoHandlerFound(true)).build();
        withResources.perform(get("/does-not-exist")).andExpect(status().isNotFound());
    }

    @Test
    void wrongMethod_is405() throws Exception {
        mvc.perform(post("/missing")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void wrongContentType_is415() throws Exception {
        mvc.perform(post("/valid").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isUnsupportedMediaType());
    }
}
