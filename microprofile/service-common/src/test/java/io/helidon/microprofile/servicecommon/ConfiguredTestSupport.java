/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.servicecommon;

import java.util.Optional;

import io.helidon.webserver.servicecommon.HelidonFeatureSupport;
import io.helidon.webserver.http.HttpService;

/**
 * Test SE service which does not really expose its own endpoint but does use config to set an "importance" value.
 */
public class ConfiguredTestSupport extends HelidonFeatureSupport {

    static final String ENDPOINT_PATH = "/testendpoint";

    private final int importance;

    /**
     * Initialization.
     *
     * @param builder builder for the service support instance.
     */
    private ConfiguredTestSupport(Builder builder) {
        super(System.getLogger(ConfiguredTestSupport.class.getName()), builder, "testservice");
        importance = builder.importance;
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public Optional<HttpService> service() {
        return Optional.of(rules -> {
        });
    }

    int importance() {
        return importance;
    }

    static class Builder extends HelidonFeatureSupport.Builder<Builder, ConfiguredTestSupport>
            implements io.helidon.common.Builder<Builder, ConfiguredTestSupport> {

        private int importance;

        private Builder() {
            super(ENDPOINT_PATH);
        }

        @Override
        public ConfiguredTestSupport build() {
            return new ConfiguredTestSupport(this);
        }

        @Override
        public Builder config(io.helidon.common.config.Config config) {
            super.config(config);
            config.get("importance").asInt().ifPresent(this::importance);
            return this;
        }

        public Builder importance(int value) {
            importance = value;
            return this;
        }
    }
}
