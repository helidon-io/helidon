/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.spi;

import java.time.Instant;
import java.util.Optional;

import io.helidon.common.reactive.SubmissionPublisher;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigNode.ObjectNode;

/**
 * Testing implementation of {@link ConfigSource}.
 */
public class TestingConfigSource extends AbstractConfigSource<Instant> {

    private boolean subscribePollingStrategyInvoked = false;
    private boolean cancelPollingStrategyInvoked = false;

    private Instant timestamp;
    private ObjectNode loadedObjectNode;

    public TestingConfigSource(TestingBuilder builder) {
        super(builder);

        this.loadedObjectNode = builder.getObjectNode();
        timestamp = Instant.now();
    }

    @Override
    void subscribePollingStrategy() {
        subscribePollingStrategyInvoked = true;
        super.subscribePollingStrategy();
    }

    @Override
    void cancelPollingStrategy() {
        cancelPollingStrategyInvoked = true;
        super.cancelPollingStrategy();
    }

    public boolean isSubscribePollingStrategyInvoked() {
        return subscribePollingStrategyInvoked;
    }

    public boolean isCancelPollingStrategyInvoked() {
        return cancelPollingStrategyInvoked;
    }

    @Override
    protected String uid() {
        return "test";
    }

    @Override
    protected Optional<Instant> dataStamp() {
        return Optional.of(Instant.MAX);
    }

    @Override
    protected Data<ObjectNode, Instant> loadData() throws ConfigException {
        return new Data<>(Optional.ofNullable(loadedObjectNode), Optional.of(timestamp));
    }

    @Override
    public SubmissionPublisher<Optional<ObjectNode>> getChangesSubmitter() {
        return super.getChangesSubmitter();
    }

    public void changeLoadedObjectNode(ObjectNode newObjectNode, boolean submitChange) {
        timestamp = Instant.now();
        loadedObjectNode = newObjectNode;

        if (submitChange) {
            reload();
        }
    }

    public void changeLoadedObjectNode(ObjectNode newObjectNode) {
        changeLoadedObjectNode(newObjectNode, true);
    }

    public static TestingBuilder builder() {
        return new TestingBuilder()
                .pollingStrategy(() -> () -> subscriber -> {
                });
    }

    public static TestingBuilder builderNoPolling() {
        return new TestingBuilder();
    }

    /**
     * Testing implementation of {@link AbstractSource.Builder}.
     */
    public static class TestingBuilder extends Builder<TestingBuilder, Void> {
        private ObjectNode objectNode;

        private TestingBuilder() {
            super(Void.class);

            this.objectNode = null;
        }

        @Override
        protected TestingBuilder init(Config metaConfig) {
            return super.init(metaConfig);
        }

        public TestingBuilder objectNode(ObjectNode objectNode) {
            this.objectNode = objectNode;
            return this;
        }

        public ObjectNode getObjectNode() {
            return objectNode;
        }

        @Override
        public TestingConfigSource build() {
            return new TestingConfigSource(this);
        }

    }

}
