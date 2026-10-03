package com.shopsphere.order.repo;

import com.shopsphere.order.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String>, OrderRepositoryCustom {

    Page<Order> findByUserId(String userId, Pageable pageable);

    Optional<Order> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    @Query("{ 'outbox.eventId': { $exists: true } }")
    List<Order> findWithPendingEvents(Pageable pageable);
}
