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
package io.helidon.metrics.serviceapi;

import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.metrics.api.ComponentMetricsSettings;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.servicecommon.rest.HelidonRestServiceSupport;
import io.helidon.webserver.Routing;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;

public class MyMetricsServiceSupport extends HelidonRestServiceSupport {

    private static final Logger LOGGER = Logger.getLogger(MyMetricsServiceSupport.class.getName());

    private final Counter counter;

    static MyMetricsServiceSupport.Builder builder() {
        return new Builder();
    }

    public MyMetricsServiceSupport(Builder builder) {
        super(LOGGER, builder, "myService");

        MetricRegistry registry = RegistryFactory
                .getInstance(builder.componentMetricsSettingsBuilder.build())
                .getRegistry(MetricRegistry.Type.APPLICATION);
        counter = registry.counter("myCounter");
    }

    @Override
    protected void postConfigureEndpoint(Routing.Rules defaultRules, Routing.Rules serviceEndpointRoutingRules) {

    }

    @Override
    public void update(Routing.Rules rules) {

    }
    void access() {
        counter.inc();
    }

    long getCount() {
        return counter.getCount();
    }

    public static class Builder extends HelidonRestServiceSupport.Builder<MyMetricsServiceSupport, Builder> {

        private ComponentMetricsSettings.Builder componentMetricsSettingsBuilder = ComponentMetricsSettings.builder();

        public Builder() {
            super(Builder.class, "/myservice");
        }

        public Builder componentMetricsSettings(ComponentMetricsSettings.Builder componentMetricsSettingsBuilder) {
            componentMetricsSettingsBuilder = componentMetricsSettingsBuilder;
            return this;
        }

        @Override
        public Builder config(Config config) {
            super.config(config);
            config.get(ComponentMetricsSettings.Builder.METRICS_CONFIG_KEY)
                    .as(ComponentMetricsSettings::builder)
                    .ifPresent(this::componentMetricsSettings);
            return this;
        }

        @Override
        public MyMetricsServiceSupport build() {
            return new MyMetricsServiceSupport(this);
        }
    }
}
