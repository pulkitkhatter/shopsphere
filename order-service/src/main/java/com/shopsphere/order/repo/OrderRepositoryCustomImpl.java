package com.shopsphere.order.repo;

import com.shopsphere.order.model.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class OrderRepositoryCustomImpl implements OrderRepositoryCustom {

    private final MongoTemplate mongo;

    public OrderRepositoryCustomImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void removeOutboxEvent(String orderId, String eventId) {
        mongo.updateFirst(new Query(Criteria.where("_id").is(orderId)),
                new Update().pull("outbox", new org.bson.Document("eventId", eventId)), Order.class);
    }
}
