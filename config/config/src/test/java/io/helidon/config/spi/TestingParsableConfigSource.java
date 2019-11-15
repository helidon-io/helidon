/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;

/**
 * Testing implementation of {@link AbstractParsableConfigSource}.
 */
public class TestingParsableConfigSource extends AbstractParsableConfigSource<Instant> {
    private final Supplier<ConfigParser.Content<Instant>> contentSupplier;
    private boolean subscribePollingStrategyInvoked = false;
    private boolean cancelPollingStrategyInvoked = false;

    private TestingParsableConfigSource(TestingBuilder builder) {
        super(builder);

        this.contentSupplier = builder.getContentSupplier();
    }

    @Override
    protected String uid() {
        return "parsable-test";
    }

    @Override
    protected Optional<Instant> dataStamp() {
        ConfigParser.Content<Instant> content = contentSupplier.get();
        if (content != null) {
            return content.stamp();
        }
        return Optional.empty();
    }

    @Override
    protected ConfigParser.Content<Instant> content() throws ConfigException {
        ConfigParser.Content<Instant> content = contentSupplier.get();
        if (content != null) {
            return content;
        }
        throw new ConfigException("Source does not exist.");
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

    public static TestingBuilder builder() {
        return new TestingBuilder();
    }

    /**
     * Testing implementation of {@link AbstractParsableConfigSource.Builder}.
     */
    public static class TestingBuilder extends Builder<TestingBuilder, Void, TestingParsableConfigSource> {
        private Supplier<ConfigParser.Content<Instant>> contentSupplier;

        private TestingBuilder() {
            super(Void.class);

            this.contentSupplier = () -> null;
        }

        @Override
        public TestingBuilder config(Config metaConfig) {
            return super.config(metaConfig);
        }

        public TestingBuilder content(Supplier<ConfigParser.Content<Instant>> contentSupplier) {
            this.contentSupplier = contentSupplier;

            return this;
        }

        public TestingBuilder content(ConfigParser.Content<Instant> content) {
            this.contentSupplier = () -> content;

            return this;
        }

        @Override
        public TestingParsableConfigSource build() {
            return new TestingParsableConfigSource(this);
        }

        public Supplier<ConfigParser.Content<Instant>> getContentSupplier() {
            return contentSupplier;
        }
    }

}
