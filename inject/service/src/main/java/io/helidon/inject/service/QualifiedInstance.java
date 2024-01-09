package io.helidon.inject.service;

import java.util.Set;

public record QualifiedInstance<T>(T instance, Set<Qualifier> qualifiers) {

    public static <T> QualifiedInstance<T> create(T instance, Qualifier... qualifiers) {
        return new QualifiedInstance<>(instance, Set.of(qualifiers));
    }

    public static <T> QualifiedInstance<T> create(T instance, Set<Qualifier> qualifiers) {
        return new QualifiedInstance<>(instance, Set.copyOf(qualifiers));
    }
}
