/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.servicecommon.restcdi;

import io.helidon.config.Config;
import io.helidon.servicecommon.rest.HelidonRestServiceSupport;
import io.helidon.webserver.Routing;

import java.util.logging.Logger;

/**
 * Test SE service which does not really expose its own endpoint but does use config to set an "importance" value.
 */
public class ConfiguredTestSupport extends HelidonRestServiceSupport {


    static final String ENDPOINT_PATH = "/testendpoint";

    private final int importance;

    /**
     * Initialization.
     *
     * @param builder     builder for the service support instance.
     */
    private ConfiguredTestSupport(Builder builder) {
        super(Logger.getLogger(ConfiguredTestSupport.class.getName()), builder, "testservice");
        importance = builder.importance;
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    protected void postConfigureEndpoint(Routing.Rules defaultRules, Routing.Rules serviceEndpointRoutingRules) {
        // We are not exposing a service-specific endpoint, nor do we need to add handling to normal requests in the test.
    }

    @Override
    public void update(Routing.Rules rules) {
        configureEndpoint(rules, rules);
    }

    int importance() {
        return importance;
    }

    static class Builder extends HelidonRestServiceSupport.Builder<ConfiguredTestSupport, Builder>
            implements io.helidon.common.Builder<ConfiguredTestSupport> {


        private int importance;

        private Builder() {
            super(Builder.class, ENDPOINT_PATH);
        }

        @Override
        public ConfiguredTestSupport build() {
            return new ConfiguredTestSupport(this);
        }

        @Override
        public Builder config(Config config) {
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
