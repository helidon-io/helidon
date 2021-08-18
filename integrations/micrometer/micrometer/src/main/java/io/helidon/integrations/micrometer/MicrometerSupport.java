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
 *
 */
package io.helidon.integrations.micrometer;

import java.util.function.Supplier;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.servicecommon.rest.HelidonRestServiceSupport;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Implements simple Micrometer support.
 * <p>
 * Developers create Micrometer {@code MeterRegistry} objects and enroll them with
 * {@link Builder}, providing with each enrollment a Helidon {@code Handler} for expressing the registry's
 * data in an HTTP response.
 * </p>
 * <p>Alternatively, developers can enroll any of the built-in registries represented by
 * the {@link MeterRegistryFactory.BuiltInRegistryType} enum.</p>
 * <p>
 * Having enrolled Micrometer meter registries with {@code MicrometerSupport.Builder} and built the
 * {@code MicrometerSupport} object, developers can invoke the {@link #registry()} method and use the returned {@code
 * MeterRegistry} to create or locate meters.
 * </p>
 */
public class MicrometerSupport extends HelidonRestServiceSupport {

    static final String DEFAULT_CONTEXT = "/micrometer";
    private static final String SERVICE_NAME = "Micrometer";

    private final MeterRegistryFactory meterRegistryFactory;

    private MicrometerSupport(Builder builder) {
        super(Logger.getLogger(MicrometerSupport.class.getName()), builder, SERVICE_NAME);

        meterRegistryFactory = builder.meterRegistryFactorySupplier.get();
    }

    /**
     * Fluid builder for {@code MicrometerSupport}.
     *
     * @return Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new {@code MicrometerSupport} using default settings.
     *
     * @return default MicrometerSupport
     */
    public static MicrometerSupport create() {
        return builder().build();
    }

    /**
     * Creates a new {@code MicrometerSupport} using the provided {@code Config} (anchored at the "metrics.micrometer" node).
     *
     * @param config Config settings for Micrometer set-up
     * @return newly-created MicrometerSupport
     */
    public static MicrometerSupport create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Returns the composite registry so apps can create and register meters on it.
     *
     * @return the composite registry
     */
    public MeterRegistry registry() {
        return meterRegistryFactory.meterRegistry();
    }

    @Override
    public void update(Routing.Rules rules) {
        configureEndpoint(rules, rules);
    }

    @Override
    protected void postConfigureEndpoint(Routing.Rules defaultRules, Routing.Rules serviceEndpointRoutingRules) {
        defaultRules
                .any(new MetricsContextHandler(meterRegistryFactory.meterRegistry()))
                .get(context(), this::getOrOptions)
                .options(context(), this::getOrOptions);
    }

    private void getOrOptions(ServerRequest serverRequest, ServerResponse serverResponse) {
        /*
          Each meter registry is paired with a function. For each, invoke the function
          looking for the first non-empty Optional<Handler> and invoke that handler. If
          none matches then return an error response.
         */
        meterRegistryFactory
                .matchingHandler(serverRequest, serverResponse)
                .accept(serverRequest, serverResponse);
    }

    /**
     * Fluid builder for {@code MicrometerSupport} objects.
     */
    public static class Builder extends HelidonRestServiceSupport.Builder<MicrometerSupport, Builder>
            implements io.helidon.common.Builder<MicrometerSupport> {

        private Supplier<MeterRegistryFactory> meterRegistryFactorySupplier = null;

        private Builder() {
            super(Builder.class, DEFAULT_CONTEXT);
        }

        @Override
        public MicrometerSupport build() {
            if (null == meterRegistryFactorySupplier) {
                meterRegistryFactorySupplier = () -> MeterRegistryFactory.getInstance(
                        MeterRegistryFactory.builder().config(config()));
            }
            return new MicrometerSupport(this);
        }

        /**
         * Assigns a {@code MeterRegistryFactory}.
         *
         * @param meterRegistryFactory the MeterRegistry  to use
         * @return updated builder instance
         */
        public Builder meterRegistryFactorySupplier(MeterRegistryFactory meterRegistryFactory) {
            this.meterRegistryFactorySupplier = () -> meterRegistryFactory;
            return this;
        }
    }

    // this class is created for cleaner tracing of web server handlers
    private static final class MetricsContextHandler implements Handler {

        private final MeterRegistry meterRegistry;

        private MetricsContextHandler(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @Override
        public void accept(ServerRequest req, ServerResponse res) {
            req.context().register(meterRegistry);
            req.next();
        }
    }
}
