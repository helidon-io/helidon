/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.micrometer;

import java.util.function.Supplier;

import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.nima.servicecommon.HelidonFeatureSupport;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Implements simple Micrometer support.
 * <p>
 * Developers create Micrometer {@code MeterRegistry} objects and enroll them with
 * {@link io.helidon.integrations.micrometer.MicrometerService.Builder}, providing with each enrollment a Helidon {@code Handler} for expressing the registry's
 * data in an HTTP response.
 * </p>
 * <p>Alternatively, developers can enroll any of the built-in registries represented by
 * the {@link io.helidon.integrations.micrometer.MeterRegistryFactory.BuiltInRegistryType} enum.</p>
 * <p>
 * Having enrolled Micrometer meter registries with {@code MicrometerSupport.Builder} and built the
 * {@code MicrometerSupport} object, developers can invoke the {@link #registry()} method and use the returned {@code
 * MeterRegistry} to create or locate meters.
 * </p>
 */
public class MicrometerService extends HelidonFeatureSupport {

    static final String DEFAULT_CONTEXT = "/micrometer";
    private static final String SERVICE_NAME = "Micrometer";

    private final MeterRegistryFactory meterRegistryFactory;

    private MicrometerService(Builder builder) {
        super(System.getLogger(MicrometerService.class.getName()), builder, SERVICE_NAME);

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
    public static MicrometerService create() {
        return builder().build();
    }

    /**
     * Creates a new {@code MicrometerSupport} using the provided {@code Config} (anchored at the "metrics.micrometer" node).
     *
     * @param config Config settings for Micrometer set-up
     * @return newly-created MicrometerSupport
     */
    public static MicrometerService create(Config config) {
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
    public void routing(HttpRules rules) {
        throw new IllegalArgumentException("Cannot register service directly, please use one of the configureEndpoint "
                                                   + "methods on this instance");
    }

    @Override
    protected void postSetup(HttpRouting.Builder defaultRules, HttpRules serviceEndpointRoutingRules) {
        defaultRules
                .get(context(), this::getOrOptions)
                .options(context(), this::getOrOptions);
    }

    @Override
    public void beforeStart() {
        Contexts.globalContext().register(registry());
    }

    private void getOrOptions(ServerRequest serverRequest, ServerResponse serverResponse) throws Exception {
        /*
          Each meter registry is paired with a function. For each, invoke the function
          looking for the first non-empty Optional<Handler> and invoke that handler. If
          none matches then return an error response.
         */
        meterRegistryFactory
                .matchingHandler(serverRequest, serverResponse)
                .handle(serverRequest, serverResponse);
    }

    /**
     * Fluid builder for {@code MicrometerSupport} objects.
     */
    @Configured(prefix = "micrometer")
    public static class Builder extends HelidonFeatureSupport.Builder<Builder, MicrometerService>
            implements io.helidon.common.Builder<Builder, MicrometerService> {

        private Supplier<MeterRegistryFactory> meterRegistryFactorySupplier = null;

        private Builder() {
            super(DEFAULT_CONTEXT);
        }

        @Override
        public MicrometerService build() {
            if (null == meterRegistryFactorySupplier) {
                meterRegistryFactorySupplier = () -> MeterRegistryFactory.getInstance(
                        MeterRegistryFactory.builder().config(config()));
            }
            return new MicrometerService(this);
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
}
