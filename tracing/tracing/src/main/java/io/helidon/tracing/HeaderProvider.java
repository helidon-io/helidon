package io.helidon.tracing;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface HeaderProvider {
    static HeaderProvider empty() {
        return create(Map.of());
    }

    static HeaderProvider create(Map<String, List<String>> inboundHeaders) {
        return new MapHeaderConsumer(Map.copyOf(inboundHeaders));
    }

    Iterable<String> keys();

    Optional<String> get(String key);

    Iterable<String> getAll(String key);

    boolean contains(String key);
}
