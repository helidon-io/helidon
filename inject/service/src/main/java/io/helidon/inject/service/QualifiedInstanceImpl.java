package io.helidon.inject.service;

import java.util.Set;

record QualifiedInstanceImpl<T>(T instance, Set<Qualifier> qualifiers) implements QualifiedInstance<T> {
    @Override
    public T get() {
        return instance;
    }
}
