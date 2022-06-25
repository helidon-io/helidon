package io.helidon.tracing;

import java.util.Optional;
import java.util.Set;

public interface HeaderProvider {
    static HeaderProvider empty() {
        return new HeaderProvider() {
            @Override
            public Iterable<String> keys() {
                return Set.of();
            }

            @Override
            public Optional<String> get(String key) {
                return Optional.empty();
            }
        };
    }

    Iterable<String> keys();
    Optional<String> get(String key);
}
