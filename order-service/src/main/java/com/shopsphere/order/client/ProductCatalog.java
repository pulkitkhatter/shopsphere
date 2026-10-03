package com.shopsphere.order.client;

import java.util.Optional;

public interface ProductCatalog {
    /** Empty = the product does not exist. Throws ServiceUnavailableException when the catalogue cannot be reached. */
    Optional<ProductInfo> find(String productId);
}
