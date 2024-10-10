package io.helidon.builder.test.testsubjects;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

// test all the funny generics
@Prototype.Blueprint
interface GenericsBlueprint<T extends CharSequence & Serializable, X extends T> {
    @Option.Singular
    Set<T> tValues();

    @Option.Singular
    Set<X> xValues();

    @Option.Singular
    Map<T, X> mappedValues();

    Optional<Supplier<? extends T>> complicatedValue();
}
