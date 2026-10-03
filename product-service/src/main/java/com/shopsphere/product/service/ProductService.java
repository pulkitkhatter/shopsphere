package com.shopsphere.product.service;

import com.shopsphere.common.web.ConflictException;
import com.shopsphere.common.web.NotFoundException;
import com.shopsphere.product.event.ProductEvent;
import com.shopsphere.product.event.ProductEventPublisher;
import com.shopsphere.product.model.Product;
import com.shopsphere.product.repo.ProductRepository;
import com.shopsphere.product.repo.ProductSearchCriteria;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    public static final String CACHE = "products";
    /** Hard cap for the legacy unpaginated v1 list: an API without a bound is a denial-of-service waiting to happen. */
    static final int V1_MAX_RESULTS = 100;

    private final ProductRepository repository;
    private final ProductEventPublisher events;

    public ProductService(ProductRepository repository, ProductEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    /** Read-through cache: hot product pages are served from Redis, Mongo is hit once per TTL / change. */
    @Cacheable(cacheNames = CACHE, key = "#id")
    public Product get(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    public List<Product> listLegacy(String category) {
        Pageable page = PageRequest.of(0, V1_MAX_RESULTS, Sort.by("name"));
        return category == null || category.isBlank()
                ? repository.findAll(page).getContent()
                : repository.findByCategory(category, page).getContent();
    }

    public Page<Product> search(ProductSearchCriteria criteria, Pageable pageable) {
        return repository.search(criteria, pageable);
    }

    public Product create(ProductData data) {
        if (repository.existsBySku(data.sku())) {
            throw new ConflictException("SKU " + data.sku() + " already exists");
        }
        Product product = new Product();
        apply(product, data);
        Product saved;
        try {
            saved = repository.save(product);
        } catch (DuplicateKeyException e) {          // unique index wins a race the existsBySku check cannot see
            throw new ConflictException("SKU " + data.sku() + " already exists");
        }
        events.publish(ProductEvent.Type.PRODUCT_CREATED, saved);
        return saved;
    }

    @CacheEvict(cacheNames = CACHE, key = "#id")
    public Product update(String id, ProductData data) {
        Product existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
        if (!existing.getSku().equals(data.sku()) && repository.existsBySku(data.sku())) {
            throw new ConflictException("SKU " + data.sku() + " already exists");
        }
        apply(existing, data);
        Product saved = repository.save(existing);   // @Version => OptimisticLockingFailureException on concurrent edit (HTTP 409)
        events.publish(ProductEvent.Type.PRODUCT_UPDATED, saved);
        return saved;
    }

    @CacheEvict(cacheNames = CACHE, key = "#id")
    public void delete(String id) {
        Product existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
        repository.delete(existing);
        events.publish(ProductEvent.Type.PRODUCT_DELETED, existing);
    }

    private static void apply(Product p, ProductData d) {
        p.setSku(d.sku());
        p.setName(d.name());
        p.setDescription(d.description());
        p.setCategory(d.category());
        p.setPrice(d.price());
        p.setStock(d.stock());
        p.setTags(d.tags());
    }
}
