package io.helidon.microprofile.testing.testng;

import java.util.Optional;

import io.helidon.microprofile.testing.HelidonTestContainer;

final class HelidonTestContainerHolder {

    private static final ThreadLocal<HelidonTestContainer> THREAD_LOCAL = new ThreadLocal<>();

    private HelidonTestContainerHolder() {
    }

    static Optional<HelidonTestContainer> get() {
        return Optional.ofNullable(THREAD_LOCAL.get());
    }

    static HelidonTestContainer getOrThrow() {
        return get().orElseThrow(() -> new IllegalStateException("Container not set"));
    }

    static void set(HelidonTestContainer container) {
        THREAD_LOCAL.set(container);
    }

    static void remove() {
        THREAD_LOCAL.remove();
    }
}
