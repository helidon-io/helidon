package io.helidon.tracing;

import java.util.List;
import java.util.Map;

public interface HeaderConsumer extends HeaderProvider {
    static HeaderConsumer create(Map<String, List<String>> headers) {
        return new MapHeaderConsumer(headers);
    }

    void setIfAbsent(String key, String... values);

    void set(String key, String... values);
}
