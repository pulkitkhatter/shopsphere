package com.shopsphere.product.repo;

import com.shopsphere.product.TestData;
import com.shopsphere.product.config.MongoConfig;
import com.shopsphere.product.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against a real MongoDB in Docker (skipped automatically when Docker is not available). Run with `mvn verify`. */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@Import(MongoConfig.class)
@TestPropertySource(properties = "spring.data.mongodb.auto-index-creation=true")
class ProductRepositoryIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired ProductRepository repository;
    @Autowired MongoTemplate template;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.saveAll(List.of(
                named(TestData.product(null, "A-1", "100.00", 5), "Gaming laptop", "electronics"),
                named(TestData.product(null, "A-2", "20.00", 0), "Laptop sleeve", "accessories"),
                named(TestData.product(null, "A-3", "1500.00", 3), "Office desk", "home")));
    }

    private static Product named(Product p, String name, String category) {
        p.setId(null);
        p.setVersion(null);
        p.setName(name);
        p.setDescription(name + " description");
        p.setCategory(category);
        return p;
    }

    @Test
    void priceSortIsNumeric_notLexicographic() {
        // as strings "100.00" < "1500.00" < "20.00"; as decimals 20 < 100 < 1500
        Page<Product> page = repository.search(new ProductSearchCriteria(null, null, null, null, false),
                PageRequest.of(0, 10, Sort.by("price")));

        assertThat(page.getContent()).extracting(Product::getSku).containsExactly("A-2", "A-1", "A-3");
    }

    @Test
    void priceRangeFilter_usesNumericComparison() {
        Page<Product> page = repository.search(
                new ProductSearchCriteria(null, null, new BigDecimal("50"), new BigDecimal("200"), false), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Product::getSku).containsExactly("A-1");
    }

    @Test
    void textSearch_matchesWholeWordsInNameOrDescription() {
        Page<Product> page = repository.search(new ProductSearchCriteria("laptop", null, null, null, false),
                PageRequest.of(0, 10, Sort.by("price")));

        assertThat(page.getContent()).extracting(Product::getSku).containsExactly("A-2", "A-1");
    }

    @Test
    void filtersCombine_categoryAndInStock() {
        Page<Product> page = repository.search(new ProductSearchCriteria("laptop", "accessories", null, null, true), PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();      // the only accessory is out of stock
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void pagination_reportsTotals() {
        Page<Product> page = repository.search(new ProductSearchCriteria(null, null, null, null, false),
                PageRequest.of(1, 2, Sort.by("name")));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void skuIsUnique() {
        Product dup = named(TestData.product(null, "A-1", "1.00", 1), "x", "y");
        dup.setId(null);
        dup.setVersion(null);

        assertThatThrownBy(() -> repository.save(dup)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void decrementStock_neverGoesNegative_underConcurrency() throws Exception {
        String id = repository.findBySku("A-1").orElseThrow().getId();      // stock = 5
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Boolean>> tasks = IntStream.range(0, 20)
                .<Callable<Boolean>>mapToObj(i -> () -> repository.decrementStockIfAvailable(id, 1)).toList();

        long successes = 0;
        for (Future<Boolean> f : pool.invokeAll(tasks)) {
            if (f.get()) successes++;
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(5);                                   // exactly the 5 units that existed
        assertThat(repository.findById(id).orElseThrow().getStock()).isZero();
    }
}
