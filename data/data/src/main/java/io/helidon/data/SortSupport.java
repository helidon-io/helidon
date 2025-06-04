package io.helidon.data;

import java.util.Collections;
import java.util.List;

import io.helidon.builder.api.Prototype;

// SortBlueprint custom methods
class SortSupport {

    /**
     * Create new instance of query result ordering with no ordering set.
     *
     * @return new query result ordering instance
     */
    @Prototype.FactoryMethod
    static Sort unsorted() {
        return Sort.builder()
                .orderBy(Collections.emptyList())
                .build();
    }

    // Workaround: Blueprint codegen does not support varargs so create for single Order is required too
    /**
     * Create new instance of query result ordering.
     *
     * @param order order definition
     * @return new query result ordering instance
     */
    @Prototype.FactoryMethod
    static Sort create(Order order) {
        return Sort.builder()
                .orderBy(List.of(order))
                .build();
    }

    /**
     * Create new instance of query result ordering.
     *
     * @param orders order definitions
     * @return new query result ordering instance
     */
    @Prototype.FactoryMethod
    static Sort create(Order... orders) {
        return Sort.builder()
                .orderBy(List.of(orders))
                .build();
    }

    /**
     * Create new instance of query result ordering.
     *
     * @param orders {@link List} of order definitions
     * @return new query result ordering instance
     */
    @Prototype.FactoryMethod
    static Sort create(List<Order> orders) {
        return Sort.builder()
                .orderBy(orders)
                .build();
    }

    /**
     * Whether any order definitions are set.
     *
     * @return value of {@code true} when at least one order definition is set or {@code false} otherwise
     */
    @Prototype.PrototypeMethod
    public static boolean isSorted(Sort sort) {
        return sort.orderBy().isEmpty();
    }

}
