package com.shopsphere.product.config;

import com.shopsphere.product.model.Product;
import com.shopsphere.product.repo.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevDataSeederTest {

    @Mock ProductRepository repository;

    @Test
    @SuppressWarnings("unchecked")
    void seedsADiverseCatalogue_whenEmpty() throws Exception {
        when(repository.count()).thenReturn(0L, 12L);

        new DevDataSeeder(repository).run(null);

        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<Product> seeded = captor.getValue();
        assertThat(seeded).hasSize(12);
        assertThat(seeded).extracting(Product::getSku).doesNotHaveDuplicates();
        assertThat(seeded).extracting(Product::getCategory).contains("electronics", "books", "home", "sports");
        assertThat(seeded).anyMatch(p -> p.getStock() == 0);          // an out-of-stock item for the UI/tests
    }

    @Test
    void neverTouchesAnExistingCatalogue() throws Exception {
        when(repository.count()).thenReturn(5L);

        new DevDataSeeder(repository).run(null);

        verify(repository, never()).saveAll(anyIterable());
    }
}
