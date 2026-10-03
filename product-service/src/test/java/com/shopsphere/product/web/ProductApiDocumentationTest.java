package com.shopsphere.product.web;

import com.shopsphere.common.web.ApiExceptionHandler;
import com.shopsphere.product.TestData;
import com.shopsphere.product.config.SecurityConfig;
import com.shopsphere.product.service.ProductData;
import com.shopsphere.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Documentation that cannot lie: these tests call the real controllers and the snippets they write
 * (target/generated-snippets) are stitched into src/docs/asciidoc/index.adoc. If the API changes and the
 * documented fields no longer match, the build fails.
 */
@WebMvcTest(ProductControllerV2.class)
@AutoConfigureRestDocs
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class ProductApiDocumentationTest {

    @Autowired MockMvc mvc;
    @MockBean ProductService service;
    @MockBean JwtDecoder jwtDecoder;

    @Test
    void searchProducts() throws Exception {
        when(service.search(any(), any())).thenReturn(new PageImpl<>(
                List.of(TestData.product("665f1c2e9d3a4b0001a1b2c3", "LAP-001", "1099.00", 25)), PageRequest.of(0, 12), 1));

        mvc.perform(get("/api/v2/products").param("q", "laptop").param("category", "electronics").param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andDo(document("products-search",
                        queryParameters(
                                parameterWithName("q").description("Full-text query (whole words) over name and description").optional(),
                                parameterWithName("category").description("Exact category filter").optional(),
                                parameterWithName("minPrice").description("Minimum price").optional(),
                                parameterWithName("maxPrice").description("Maximum price").optional(),
                                parameterWithName("inStock").description("Only products with stock > 0 (default false)").optional(),
                                parameterWithName("page").description("Zero-based page (default 0)").optional(),
                                parameterWithName("size").description("Page size, 1-100 (default 12)").optional(),
                                parameterWithName("sort").description("name | price | createdAt, then ,asc|desc (default name,asc)").optional()),
                        responseFields(
                                fieldWithPath("content[]").description("Products on this page"),
                                fieldWithPath("content[].id").description("Product id"),
                                fieldWithPath("content[].sku").description("Unique stock keeping unit"),
                                fieldWithPath("content[].name").description("Display name"),
                                fieldWithPath("content[].description").description("Long description"),
                                fieldWithPath("content[].category").description("Category"),
                                fieldWithPath("content[].price.amount").description("Unit price"),
                                fieldWithPath("content[].price.currency").description("ISO 4217 currency code"),
                                fieldWithPath("content[].stock").description("Units in stock"),
                                fieldWithPath("content[].inStock").description("true when stock > 0"),
                                fieldWithPath("content[].tags[]").description("Free-form tags"),
                                fieldWithPath("content[].version").description("Optimistic-locking version"),
                                fieldWithPath("content[].createdAt").description("Creation time (UTC)"),
                                fieldWithPath("content[].updatedAt").description("Last modification time (UTC)"),
                                fieldWithPath("page").description("Current page index"),
                                fieldWithPath("size").description("Requested page size"),
                                fieldWithPath("totalElements").description("Total matches"),
                                fieldWithPath("totalPages").description("Total pages"))));
    }

    @Test
    void getProduct() throws Exception {
        when(service.get("665f1c2e9d3a4b0001a1b2c3")).thenReturn(TestData.product("665f1c2e9d3a4b0001a1b2c3", "LAP-001", "1099.00", 25));

        mvc.perform(get("/api/v2/products/{id}", "665f1c2e9d3a4b0001a1b2c3"))
                .andExpect(status().isOk())
                .andDo(document("products-get",
                        pathParameters(parameterWithName("id").description("Product id")),
                        responseFields(
                                fieldWithPath("id").description("Product id"),
                                fieldWithPath("sku").description("Unique stock keeping unit"),
                                fieldWithPath("name").description("Display name"),
                                fieldWithPath("description").description("Long description"),
                                fieldWithPath("category").description("Category"),
                                fieldWithPath("price.amount").description("Unit price"),
                                fieldWithPath("price.currency").description("ISO 4217 currency code"),
                                fieldWithPath("stock").description("Units in stock"),
                                fieldWithPath("inStock").description("true when stock > 0"),
                                fieldWithPath("tags[]").description("Free-form tags"),
                                fieldWithPath("version").description("Optimistic-locking version"),
                                fieldWithPath("createdAt").description("Creation time (UTC)"),
                                fieldWithPath("updatedAt").description("Last modification time (UTC)"))));
    }

    @Test
    void createProduct() throws Exception {
        when(jwtDecoder.decode("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.signature")).thenReturn(Jwt.withTokenValue("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.signature")
                .header("alg", "RS256").subject("admin").claim("roles", List.of("ADMIN"))
                .issuedAt(java.time.Instant.now()).expiresAt(java.time.Instant.now().plusSeconds(600)).build());
        when(service.create(any(ProductData.class))).thenReturn(TestData.product("665f1c2e9d3a4b0001a1b2c3", "LAP-001", "1099.00", 25));

        mvc.perform(post("/api/v2/products")
                        .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"LAP-001","name":"UltraBook 14","description":"Light laptop","category":"electronics",
                                 "price":1099.00,"stock":25,"tags":["laptop","work"]}"""))
                .andExpect(status().isCreated())
                .andDo(document("products-create",
                        requestHeaders(headerWithName("Authorization").description("Bearer token of a user with role ADMIN")),
                        requestFields(
                                fieldWithPath("sku").description("Unique SKU: letters, digits and '-'"),
                                fieldWithPath("name").description("Display name (max 120)"),
                                fieldWithPath("description").description("Description (max 2000)").optional(),
                                fieldWithPath("category").description("Category (max 60)"),
                                fieldWithPath("price").description("Unit price, > 0, max 2 decimals"),
                                fieldWithPath("stock").description("Initial stock, >= 0"),
                                fieldWithPath("tags").description("Up to 10 tags").optional())));
    }
}
