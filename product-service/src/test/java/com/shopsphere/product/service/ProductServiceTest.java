package com.shopsphere.product.service;

import com.shopsphere.common.web.ConflictException;
import com.shopsphere.common.web.NotFoundException;
import com.shopsphere.product.TestData;
import com.shopsphere.product.event.ProductEvent;
import com.shopsphere.product.event.ProductEventPublisher;
import com.shopsphere.product.model.Product;
import com.shopsphere.product.repo.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository repository;
    @Mock ProductEventPublisher events;
    @InjectMocks ProductService service;

    final ProductData data = new ProductData("SKU-1", "Phone", "desc", "electronics", new BigDecimal("99.90"), 5, List.of("x"));

    @Test
    void get_returnsProduct() {
        Product p = TestData.product("1", "SKU-1", "10.00", 1);
        when(repository.findById("1")).thenReturn(Optional.of(p));

        assertThat(service.get("1")).isSameAs(p);
    }

    @Test
    void get_unknownId_throwsNotFound() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("nope")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_savesProduct_andPublishesCreatedEvent() {
        when(repository.existsBySku("SKU-1")).thenReturn(false);
        when(repository.save(any(Product.class))).thenAnswer(i -> {
            Product saved = i.getArgument(0);
            saved.setId("new-id");
            return saved;
        });

        Product created = service.create(data);

        assertThat(created.getId()).isEqualTo("new-id");
        assertThat(created.getPrice()).isEqualByComparingTo("99.90");
        assertThat(created.getTags()).containsExactly("x");
        verify(events).publish(ProductEvent.Type.PRODUCT_CREATED, created);
    }

    @Test
    void create_duplicateSku_isConflict_andNothingIsSavedOrPublished() {
        when(repository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(data)).isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void create_losingRaceOnUniqueIndex_isAlsoConflict() {
        when(repository.existsBySku("SKU-1")).thenReturn(false);
        when(repository.save(any())).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> service.create(data)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(events);
    }

    @Test
    void update_changesFields_andPublishesUpdatedEvent() {
        Product existing = TestData.product("1", "SKU-1", "10.00", 1);
        when(repository.findById("1")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Product updated = service.update("1", data);

        assertThat(updated.getName()).isEqualTo("Phone");
        assertThat(updated.getStock()).isEqualTo(5);
        verify(events).publish(ProductEvent.Type.PRODUCT_UPDATED, existing);
    }

    @Test
    void update_toSkuOfAnotherProduct_isConflict() {
        Product existing = TestData.product("1", "OLD-SKU", "10.00", 1);
        when(repository.findById("1")).thenReturn(Optional.of(existing));
        when(repository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> service.update("1", data)).isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_keepingOwnSku_doesNotCheckUniqueness() {
        Product existing = TestData.product("1", "SKU-1", "10.00", 1);
        when(repository.findById("1")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.update("1", data);

        verify(repository, never()).existsBySku(any());
    }

    @Test
    void delete_removesProduct_andPublishesDeletedEvent() {
        Product existing = TestData.product("1", "SKU-1", "10.00", 1);
        when(repository.findById("1")).thenReturn(Optional.of(existing));

        service.delete("1");

        verify(repository).delete(existing);
        verify(events).publish(ProductEvent.Type.PRODUCT_DELETED, existing);
    }

    @Test
    void delete_unknownProduct_throwsNotFound() {
        when(repository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("x")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listLegacy_isBoundedTo100Results() {
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.listLegacy(null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(ProductService.V1_MAX_RESULTS);
    }
}
