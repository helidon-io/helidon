package io.helidon.builder.test;

import java.util.Optional;
import java.util.function.BiConsumer;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.EventConfigSource;
import io.helidon.config.spi.NodeConfigSource;

import org.junit.jupiter.api.Test;

class SupplierTest {
    private static final String KEY = "string-supplier";
    private static final String ORIGINAL_VALUE = "value";
    private static final String NEW_VALUE = "new-value";

    @Test
    void testChange() {
        Config config = Config.just(new TestSource());

    }

    private static class TestSource implements ConfigSource, EventConfigSource, NodeConfigSource {

        private BiConsumer<String, ConfigNode> consumer;

        @Override
        public void onChange(BiConsumer<String, ConfigNode> changedNode) {
            this.consumer = changedNode;
        }

        @Override
        public Optional<ConfigContent.NodeContent> load() throws ConfigException {
            return Optional.of(ConfigContent.NodeContent.builder()
                                       .node(ConfigNode.ObjectNode.builder()
                                                     .addValue(KEY, ORIGINAL_VALUE)
                                                     .build())
                                       .build());
        }

        void update() {
            consumer.accept(KEY, ConfigNode.ValueNode.create(NEW_VALUE));
        }
    }
}
