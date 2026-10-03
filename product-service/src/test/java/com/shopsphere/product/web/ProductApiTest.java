package com.shopsphere.product.web;

import com.shopsphere.common.web.ApiExceptionHandler;
import com.shopsphere.common.web.ConflictException;
import com.shopsphere.common.web.NotFoundException;
import com.shopsphere.product.TestData;
import com.shopsphere.product.config.SecurityConfig;
import com.shopsphere.product.repo.ProductSearchCriteria;
import com.shopsphere.product.service.ProductData;
import com.shopsphere.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ProductControllerV1.class, ProductControllerV2.class})
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class ProductApiTest {

    static final String VALID_V2 = """
            {"sku":"SKU-9","name":"Phone","description":"d","category":"electronics","price":99.90,"stock":3,"tags":["new"]}""";
    static final String VALID_V1 = """
            {"sku":"SKU-9","name":"Phone","description":"d","category":"electronics","price":99.90,"stock":3}""";

    @Autowired MockMvc mvc;
    @MockBean ProductService service;
    @MockBean JwtDecoder jwtDecoder;

    static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    static org.springframework.test.web.servlet.request.RequestPostProcessor customer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // ---------- security ----------

    @Test
    void catalogueIsPublic() throws Exception {
        when(service.get("1")).thenReturn(TestData.product("1", "S", "5.00", 1));
        mvc.perform(get("/api/v2/products/1")).andExpect(status().isOk());
    }

    @Test
    void creatingProduct_withoutToken_is401() throws Exception {
        mvc.perform(post("/api/v2/products").contentType(MediaType.APPLICATION_JSON).content(VALID_V2))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void creatingProduct_asCustomer_is403() throws Exception {
        mvc.perform(post("/api/v2/products").with(customer()).contentType(MediaType.APPLICATION_JSON).content(VALID_V2))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void deletingProduct_asCustomer_is403() throws Exception {
        mvc.perform(delete("/api/v2/products/1").with(customer())).andExpect(status().isForbidden());
    }

    @Test
    void unknownEndpoints_areDeniedByDefault() throws Exception {
        mvc.perform(get("/internal/secret").with(admin())).andExpect(status().isForbidden());
    }

    // ---------- v2 ----------

    @Test
    void v2_create_asAdmin_returns201_withLocation() throws Exception {
        when(service.create(any(ProductData.class))).thenReturn(TestData.product("abc", "SKU-9", "99.90", 3));

        mvc.perform(post("/api/v2/products").with(admin()).contentType(MediaType.APPLICATION_JSON).content(VALID_V2))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/v2/products/abc")))
                .andExpect(jsonPath("$.price.amount").value(99.90))
                .andExpect(jsonPath("$.price.currency").value("USD"))
                .andExpect(jsonPath("$.inStock").value(true));
    }

    @Test
    void v2_create_mapsTagsToServiceData() throws Exception {
        when(service.create(any(ProductData.class))).thenReturn(TestData.product("abc", "SKU-9", "99.90", 3));

        mvc.perform(post("/api/v2/products").with(admin()).contentType(MediaType.APPLICATION_JSON).content(VALID_V2))
                .andExpect(status().isCreated());

        ArgumentCaptor<ProductData> captor = ArgumentCaptor.forClass(ProductData.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().tags()).containsExactly("new");
    }

    @Test
    void v2_create_withInvalidBody_returns400_withFieldErrors() throws Exception {
        String bad = """
                {"sku":"bad sku!","name":"","category":"c","price":-1,"stock":-5}""";

        mvc.perform(post("/api/v2/products").with(admin()).contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.stock").exists());
        verifyNoInteractions(service);
    }

    @Test
    void v2_create_duplicateSku_returns409() throws Exception {
        when(service.create(any())).thenThrow(new ConflictException("SKU SKU-9 already exists"));

        mvc.perform(post("/api/v2/products").with(admin()).contentType(MediaType.APPLICATION_JSON).content(VALID_V2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("SKU SKU-9 already exists"));
    }

    @Test
    void v2_get_unknown_returns404ProblemDetail() throws Exception {
        when(service.get("zzz")).thenThrow(new NotFoundException("Product zzz not found"));

        mvc.perform(get("/api/v2/products/zzz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void v2_search_passesFiltersAndPaging_andWrapsResultInPageEnvelope() throws Exception {
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(TestData.product("1", "S", "5.00", 1)),
                org.springframework.data.domain.PageRequest.of(1, 5), 11));

        mvc.perform(get("/api/v2/products").param("q", "laptop").param("category", "electronics")
                        .param("minPrice", "10").param("maxPrice", "2000").param("inStock", "true")
                        .param("page", "1").param("size", "5").param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("S"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3));

        ArgumentCaptor<ProductSearchCriteria> criteria = ArgumentCaptor.forClass(ProductSearchCriteria.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).search(criteria.capture(), pageable.capture());
        assertThat(criteria.getValue().q()).isEqualTo("laptop");
        assertThat(criteria.getValue().inStockOnly()).isTrue();
        assertThat(criteria.getValue().minPrice()).isEqualByComparingTo("10");
        assertThat(pageable.getValue().getSort().getOrderFor("price").isDescending()).isTrue();
    }

    @Test
    void v2_search_rejectsPageSizeAbove100() throws Exception {
        mvc.perform(get("/api/v2/products").param("size", "5000")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void v2_search_rejectsSortingOnNonWhitelistedField() throws Exception {
        mvc.perform(get("/api/v2/products").param("sort", "passwordHash,asc")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    // ---------- v1 (deprecated) ----------

    @Test
    void v1_responses_announceDeprecationAndSunset() throws Exception {
        when(service.listLegacy(null)).thenReturn(List.of(TestData.product("1", "S", "5.00", 1)));

        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(header().exists("Sunset"))
                .andExpect(header().string("Link", org.hamcrest.Matchers.containsString("successor-version")))
                .andExpect(jsonPath("$[0].price").value(5.00));        // flat price: the frozen v1 contract
    }

    @Test
    void v1_create_asAdmin_works() throws Exception {
        when(service.create(any())).thenReturn(TestData.product("abc", "SKU-9", "99.90", 3));

        mvc.perform(post("/api/v1/products").with(admin()).contentType(MediaType.APPLICATION_JSON).content(VALID_V1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("abc"));
    }

    @Test
    void v1_delete_asAdmin_returns204() throws Exception {
        mvc.perform(delete("/api/v1/products/1").with(admin())).andExpect(status().isNoContent());
        verify(service).delete("1");
    }
}
