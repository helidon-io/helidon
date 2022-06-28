package io.helidon.tracing;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class MapHeaderConsumer implements HeaderConsumer {
    private final Map<String, List<String>> headers;

    MapHeaderConsumer(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    @Override
    public Iterable<String> keys() {
        return headers.keySet();
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(headers.get(key))
                .flatMap(it -> {
                    if (it.isEmpty()) {
                        return Optional.empty();
                    }
                    return Optional.of(it.get(0));
                });
    }

    @Override
    public Iterable<String> getAll(String key) {
        return headers.getOrDefault(key, List.of());
    }

    @Override
    public boolean contains(String key) {
        return headers.containsKey(key);
    }

    @Override
    public void setIfAbsent(String key, String... values) {
        headers.putIfAbsent(key, List.of(values));
    }

    @Override
    public void set(String key, String... values) {
        headers.put(key, List.of(values));
    }
}
