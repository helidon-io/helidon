package io.helidon.tracing;

import java.util.Optional;

public interface HeaderProvider {
    Iterable<String> keys();
    Optional<String> get(String key);
}
