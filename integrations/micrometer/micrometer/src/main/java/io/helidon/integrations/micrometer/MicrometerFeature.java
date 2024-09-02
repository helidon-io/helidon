/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.config.Config;
import io.helidon.common.context.ContextValue;
import io.helidon.config.metadata.Configured;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.servicecommon.HelidonFeatureSupport;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Implements simple Micrometer support.
 * <p>
 * Developers create Micrometer {@code MeterRegistry} objects and enroll them with
 * {@link MicrometerFeature.Builder}, providing with each enrollment a Helidon {@code Handler} for expressing the registry's
 * data in an HTTP response.
 * </p>
 * <p>Alternatively, developers can enroll any of the built-in registries represented by
 * the {@link io.helidon.integrations.micrometer.MeterRegistryFactory.BuiltInRegistryType} enum.</p>
 * <p>
 * Having enrolled Micrometer meter registries with {@code MicrometerSupport.Builder} and built the
 * {@code MicrometerSupport} object, developers can invoke the {@link #registry()} method and use the returned {@code
 * MeterRegistry} to create or locate meters.
 * </p>
 * @deprecated Use the normal Helidon {@code /metrics} endpoint and configuration instead of {@code /micrometer}.
 */
@Deprecated(forRemoval = true, since = "4.1")
public class MicrometerFeature extends HelidonFeatureSupport {
    static final String DEFAULT_CONTEXT = "/micrometer";

    private static final ContextValue<MeterRegistry> METER_REGISTRY = ContextValue.create(MeterRegistry.class);
private static final String SERVICE_NAME = "Micrometer";
    private static final System.Logger LOGGER = System.getLogger(MicrometerFeature.class.getName());

    private final MeterRegistryFactory meterRegistryFactory;

    private MicrometerFeature(Builder builder) {
        super(System.getLogger(MicrometerFeature.class.getName()), builder, SERVICE_NAME);

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
    public static MicrometerFeature create() {
        return builder().build();
    }

    /**
     * Creates a new {@code MicrometerSupport} using the provided {@code Config} (anchored at the "metrics.micrometer" node).
     *
     * @param config Config settings for Micrometer set-up
     * @return newly-created MicrometerSupport
     */
    public static MicrometerFeature create(Config config) {
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
    protected void postSetup(HttpRouting.Builder defaultRouting, HttpRouting.Builder featureRouting) {
        defaultRouting
                .get(context(), this::getOrOptions)
                .options(context(), this::getOrOptions);
    }

    @Override
    public void beforeStart() {
        METER_REGISTRY.set(registry());
        LOGGER.log(System.Logger.Level.WARNING,
                   "Micrometer integration is deprecated and will be removed in a future major release.");
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
    public static class Builder extends HelidonFeatureSupport.Builder<Builder, MicrometerFeature>
            implements io.helidon.common.Builder<Builder, MicrometerFeature> {

        private Supplier<MeterRegistryFactory> meterRegistryFactorySupplier = null;

        private Builder() {
            super(DEFAULT_CONTEXT);
        }

        @Override
        public MicrometerFeature build() {
            if (null == meterRegistryFactorySupplier) {
                meterRegistryFactorySupplier = () -> MeterRegistryFactory.getInstance(
                        MeterRegistryFactory.builder().config(config()));
            }
            return new MicrometerFeature(this);
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
