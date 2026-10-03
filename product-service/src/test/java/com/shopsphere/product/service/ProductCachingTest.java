package com.shopsphere.product.service;

import com.shopsphere.product.TestData;
import com.shopsphere.product.event.ProductEventPublisher;
import com.shopsphere.product.model.Product;
import com.shopsphere.product.repo.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies the caching *behaviour* (annotations are proxies, so a plain Mockito test cannot prove it). */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProductCachingTest.Config.class)
class ProductCachingTest {

    @Configuration
    @EnableCaching
    static class Config {
        @Bean CacheManager cacheManager() { return new ConcurrentMapCacheManager(ProductService.CACHE); }
        @Bean ProductRepository repository() { return Mockito.mock(ProductRepository.class); }
        @Bean ProductEventPublisher publisher() { return Mockito.mock(ProductEventPublisher.class); }
        @Bean ProductService productService(ProductRepository r, ProductEventPublisher p) { return new ProductService(r, p); }
    }

    @Autowired ProductService service;
    @Autowired ProductRepository repository;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void clean() {
        reset(repository);
        cacheManager.getCache(ProductService.CACHE).clear();
    }

    @Test
    void secondReadOfSameProduct_isServedFromCache() {
        when(repository.findById("1")).thenReturn(Optional.of(TestData.product("1", "S", "5.00", 1)));

        service.get("1");
        service.get("1");
        service.get("1");

        verify(repository, times(1)).findById("1");
    }

    @Test
    void update_evictsCacheEntry_soNextReadSeesFreshData() {
        Product p = TestData.product("1", "S", "5.00", 1);
        when(repository.findById("1")).thenReturn(Optional.of(p));
        when(repository.save(p)).thenReturn(p);

        service.get("1");
        service.update("1", new ProductData("S", "New name", "d", "c", new BigDecimal("6.00"), 1, List.of()));
        service.get("1");

        verify(repository, times(3)).findById("1");   // get, update's own lookup, get again after eviction
    }

    @Test
    void differentProducts_areCachedSeparately() {
        when(repository.findById("1")).thenReturn(Optional.of(TestData.product("1", "A", "5.00", 1)));
        when(repository.findById("2")).thenReturn(Optional.of(TestData.product("2", "B", "5.00", 1)));

        assertThat(service.get("1").getSku()).isEqualTo("A");
        assertThat(service.get("2").getSku()).isEqualTo("B");
        service.get("1");
        service.get("2");

        verify(repository, times(1)).findById("1");
        verify(repository, times(1)).findById("2");
    }
}
