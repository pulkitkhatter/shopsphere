package com.shopsphere.order.service;

import com.shopsphere.order.model.OrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Written BEFORE OrderPricingCalculator existed (TDD red -> green -> refactor). */
class OrderPricingCalculatorTest {

    private static OrderItem item(String id, String price, int qty) {
        return new OrderItem(id, "SKU-" + id, "Item " + id, new BigDecimal(price), qty);
    }

    @Test
    void total_isSumOfUnitPriceTimesQuantity() {
        BigDecimal total = OrderPricingCalculator.total(List.of(item("1", "10.00", 2), item("2", "5.50", 3)));

        assertThat(total).isEqualByComparingTo("36.50");
    }

    @Test
    void total_usesExactDecimalArithmetic() {
        // 0.1 * 3 must be exactly 0.30 (a double would give 0.30000000000000004)
        assertThat(OrderPricingCalculator.total(List.of(item("1", "0.10", 3)))).isEqualByComparingTo("0.30");
    }

    @Test
    void total_isRoundedToTwoDecimals() {
        assertThat(OrderPricingCalculator.total(List.of(item("1", "1.005", 1)))).isEqualByComparingTo("1.01");
    }

    @Test
    void total_ofNoItems_isRejected() {
        assertThatThrownBy(() -> OrderPricingCalculator.total(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mergeQuantities_sumsDuplicateProductLines_andKeepsFirstSeenOrder() {
        Map<String, Integer> merged = OrderPricingCalculator.mergeQuantities(
                List.of(new OrderPricingCalculator.Line("a", 1), new OrderPricingCalculator.Line("b", 2), new OrderPricingCalculator.Line("a", 4)));

        assertThat(merged).containsExactly(Map.entry("a", 5), Map.entry("b", 2));
    }

    @Test
    void mergeQuantities_rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> OrderPricingCalculator.mergeQuantities(List.of(new OrderPricingCalculator.Line("a", 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
