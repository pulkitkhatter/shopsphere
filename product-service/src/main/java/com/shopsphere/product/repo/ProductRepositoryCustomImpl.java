package com.shopsphere.product.repo;

import com.shopsphere.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    private final MongoTemplate mongo;

    public ProductRepositoryCustomImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Page<Product> search(ProductSearchCriteria c, Pageable pageable) {
        Query query = new Query();
        if (StringUtils.hasText(c.q())) {
            // index-backed full-text search (whole words, stemmed, case/diacritic-insensitive)
            query.addCriteria(TextCriteria.forDefaultLanguage().matchingAny(c.q().trim().split("\\s+")));
        }
        List<Criteria> filters = new ArrayList<>();
        if (StringUtils.hasText(c.category())) {
            filters.add(Criteria.where("category").is(c.category()));
        }
        if (c.minPrice() != null || c.maxPrice() != null) {
            Criteria price = Criteria.where("price");
            if (c.minPrice() != null) price = price.gte(c.minPrice());
            if (c.maxPrice() != null) price = price.lte(c.maxPrice());
            filters.add(price);
        }
        if (c.inStockOnly()) {
            filters.add(Criteria.where("stock").gt(0));
        }
        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters));
        }

        long total = mongo.count(Query.of(query).limit(-1).skip(-1), Product.class);
        List<Product> page = mongo.find(Query.of(query).with(pageable), Product.class);
        return PageableExecutionUtils.getPage(page, pageable, () -> total);
    }

    @Override
    public boolean decrementStockIfAvailable(String productId, int quantity) {
        Query q = new Query(Criteria.where("_id").is(productId).and("stock").gte(quantity));
        return mongo.updateFirst(q, new Update().inc("stock", -quantity), Product.class).getModifiedCount() == 1;
    }

    @Override
    public void incrementStock(String productId, int quantity) {
        mongo.updateFirst(new Query(Criteria.where("_id").is(productId)), new Update().inc("stock", quantity), Product.class);
    }
}
