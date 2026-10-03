package com.shopsphere.product.config;

import com.shopsphere.product.model.Product;
import com.shopsphere.product.repo.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@ConditionalOnProperty(name = "shopsphere.seed.enabled", havingValue = "true")
class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private final ProductRepository repository;

    DevDataSeeder(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) return;
        repository.saveAll(List.of(
                p("LAP-001", "UltraBook 14 Laptop", "Lightweight 14 inch laptop, 16GB RAM, 512GB SSD", "electronics", "1099.00", 25, "laptop", "work"),
                p("LAP-002", "GameStation 17 Laptop", "17 inch gaming laptop with dedicated graphics", "electronics", "1799.00", 8, "laptop", "gaming"),
                p("PHN-001", "Nova X Smartphone", "6.5 inch OLED smartphone with great camera", "electronics", "699.00", 60, "phone"),
                p("HDP-001", "Quiet Pro Headphones", "Wireless noise cancelling headphones", "electronics", "249.99", 40, "audio", "wireless"),
                p("KBD-001", "Mechanical Keyboard", "Hot-swappable mechanical keyboard with RGB", "electronics", "89.50", 120, "keyboard", "gaming"),
                p("BK-001", "Designing Data-Intensive Applications", "The big ideas behind reliable, scalable systems", "books", "42.00", 75, "book", "tech"),
                p("BK-002", "Clean Architecture", "A craftsman's guide to software structure and design", "books", "35.00", 50, "book", "tech"),
                p("BK-003", "The Pragmatic Programmer", "Your journey to mastery", "books", "39.00", 0, "book", "tech"),
                p("HOM-001", "Ceramic Coffee Mug", "350ml handmade ceramic mug", "home", "12.00", 300, "kitchen"),
                p("HOM-002", "Standing Desk", "Electric height adjustable standing desk", "home", "429.00", 15, "office"),
                p("SPT-001", "Trail Running Shoes", "Grippy lightweight trail running shoes", "sports", "119.00", 35, "running"),
                p("SPT-002", "Yoga Mat", "Non-slip 6mm yoga mat", "sports", "25.00", 90, "fitness")));
        log.info("Seeded {} demo products", repository.count());
    }

    private static Product p(String sku, String name, String description, String category, String price, int stock, String... tags) {
        Product p = new Product();
        p.setSku(sku);
        p.setName(name);
        p.setDescription(description);
        p.setCategory(category);
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setTags(List.of(tags));
        return p;
    }
}
