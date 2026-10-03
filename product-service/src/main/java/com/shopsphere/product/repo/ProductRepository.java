package com.shopsphere.product.repo;

import com.shopsphere.product.model.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String>, ProductRepositoryCustom {

    boolean existsBySku(String sku);

    Optional<Product> findBySku(String sku);

    /** Slice avoids the extra COUNT query: the legacy v1 list does not need totals. */
    Slice<Product> findByCategory(String category, Pageable pageable);
}
