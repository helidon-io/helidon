/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.config;

import java.time.Instant;
import java.util.Optional;

import io.helidon.config.spi.ConfigContent.NodeContent;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Testing implementation of {@link io.helidon.config.spi.ConfigSource}.
 */
public class TestingConfigSource extends AbstractConfigSource
        implements PollableSource<Instant>, NodeConfigSource {

    private volatile Instant timestamp;
    private volatile ObjectNode loadedObjectNode;
    private String uid;

    public TestingConfigSource(Builder builder) {
        super(builder);

        this.loadedObjectNode = builder.getObjectNode();
        this.timestamp = Instant.now();
        this.uid = builder.uid;
    }

    @Override
    protected String uid() {
        return uid;
    }

    @Override
    public boolean isModified(Instant stamp) {
        return stamp.isBefore(timestamp);
    }

    @Override
    public Optional<NodeContent> load() throws ConfigException {
        return Optional.ofNullable(NodeContent.builder()
                                           .node(loadedObjectNode)
                                           .stamp(timestamp)
                                           .build());
    }

    @Override
    public Optional<PollingStrategy> pollingStrategy() {
        return super.pollingStrategy();
    }

    public void changeLoadedObjectNode(ObjectNode newObjectNode) {
        timestamp = Instant.now();
        loadedObjectNode = newObjectNode;
        pollingStrategy().ifPresent(ps -> {
            if (ps instanceof TestingPollingStrategy) {
                ((TestingPollingStrategy) ps).changed();
            }
        });
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builderNoPolling() {
        return new Builder();
    }

    /**
     * Testing implementation of {@link AbstractSource.Builder}.
     */
    public static class Builder extends AbstractConfigSourceBuilder<Builder, Void>
            implements io.helidon.common.Builder<TestingConfigSource>,
                       PollableSource.Builder<Builder> {
        private ObjectNode objectNode;
        private String uid = "test";

        private Builder() {
            this.objectNode = null;
        }

        @Override
        public TestingConfigSource build() {
            return new TestingConfigSource(this);
        }

        @Override
        public Builder config(Config metaConfig) {
            return super.config(metaConfig);
        }

        public Builder objectNode(ObjectNode objectNode) {
            this.objectNode = objectNode;
            return this;
        }

        public ObjectNode getObjectNode() {
            return objectNode;
        }

        public Builder testingPollingStrategy() {
            return pollingStrategy(new TestingPollingStrategy());
        }

        @Override
        public Builder pollingStrategy(PollingStrategy pollingStrategy) {
            return super.pollingStrategy(pollingStrategy);
        }

        public Builder uid(String uid) {
            this.uid = uid;
            return this;
        }
    }

    private static class TestingPollingStrategy implements PollingStrategy {
        private Polled polled;

        @Override
        public void start(Polled polled) {
            this.polled = polled;
        }

        void changed() {
            assertThat("Polling strategy should have been started", polled, notNullValue());
            polled.poll(Instant.now());
        }
    }
}
