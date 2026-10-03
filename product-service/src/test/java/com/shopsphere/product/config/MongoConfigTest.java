package com.shopsphere.product.config;

import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MongoConfigTest {

    private final MongoCustomConversions conversions = new MongoConfig().mongoCustomConversions();

    @Test
    void bigDecimalIsStoredAsNativeDecimal128_notAsString() {
        assertThat(conversions.hasCustomWriteTarget(BigDecimal.class)).isTrue();
        assertThat(conversions.getCustomWriteTarget(BigDecimal.class)).contains(Decimal128.class);
    }

    @Test
    void roundTripKeepsExactValueAndScale() {
        BigDecimal original = new BigDecimal("1099.00");

        Decimal128 stored = new MongoConfig.BigDecimalToDecimal128().convert(original);
        BigDecimal read = new MongoConfig.Decimal128ToBigDecimal().convert(stored);

        assertThat(read).isEqualTo(original);          // equal, including scale (1099.00, not 1099)
    }
}
