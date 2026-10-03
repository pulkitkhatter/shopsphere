package com.shopsphere.product.repo;

import com.shopsphere.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepositoryCustom {

    Page<Product> search(ProductSearchCriteria criteria, Pageable pageable);

    /** Atomic "decrement if enough stock": a single Mongo update, so two orders can never oversell the last item. */
    boolean decrementStockIfAvailable(String productId, int quantity);

    void incrementStock(String productId, int quantity);
}
