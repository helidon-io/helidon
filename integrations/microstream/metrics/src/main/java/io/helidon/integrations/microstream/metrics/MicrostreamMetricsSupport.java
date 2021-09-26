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

package io.helidon.integrations.microstream.metrics;

import java.util.Objects;

import io.helidon.config.Config;
import io.helidon.metrics.MetricsSupport;
import io.helidon.metrics.RegistryFactory;

import one.microstream.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

/**
 *
 * Helper class that provides the default metrics for an Microstream EmbeddedStorageManager.
 *
 */
public class MicrostreamMetricsSupport {

    private static final String CONFIG_METRIC_ENABLED_VENDOR = "vendor.";
    static final String BASE_ENABLED_KEY = CONFIG_METRIC_ENABLED_VENDOR + "enabled";

    private static final Metadata GLOBAL_FILE_COUNT = Metadata.builder()
            .withName("microstream.globalFileCount")
            .withDisplayName("total storage file count")
            .withDescription("Displays the number of storage files.")
            .withType(MetricType.GAUGE)
            .withUnit(MetricUnits.NONE)
            .build();

    private static final Metadata LIVE_DATA_LENGTH = Metadata.builder()
            .withName("microstream.liveDataLength")
            .withDisplayName("live data length")
            .withDescription("Displays live data length. This is the 'real' size of the stored data.")
            .withType(MetricType.GAUGE)
            .withUnit(MetricUnits.BYTES)
            .build();

    private static final Metadata TOTAL_DATA_LENGTH = Metadata.builder()
            .withName("microstream.totalDataLength")
            .withDisplayName("total data length")
            .withDescription("Displays total data length. This is the accumulated size of all storage data files.")
            .withType(MetricType.GAUGE)
            .withUnit(MetricUnits.BYTES)
            .build();

    private final Config config;
    private final EmbeddedStorageManager embeddedStorageManager;
    private final RegistryFactory registryFactory;
    private final MetricRegistry vendorRegistry;

    private MicrostreamMetricsSupport(Builder builder) {
        super();
        this.config = builder.config();
        this.embeddedStorageManager = builder.embeddedStorageManager();

        if (builder.registryFactory() == null) {
            registryFactory = RegistryFactory.getInstance(config);
        } else {
            registryFactory = builder.registryFactory();
        }

        this.vendorRegistry = registryFactory.getRegistry(MetricRegistry.Type.VENDOR);
    }

    /**
     * Create a new builder to construct an instance.
     *
     * @param embeddedStorageManager EmbeddedStorageManager instance that supplies the metrics data.
     * @return A new builder instance
     */
    public static Builder builder(EmbeddedStorageManager embeddedStorageManager) {
        return new Builder(embeddedStorageManager);
    }

    private void register(Metadata meta, Metric metric, Tag... tags) {
        if (config.get(CONFIG_METRIC_ENABLED_VENDOR + meta.getName() + ".enabled")
                .asBoolean()
                .orElse(true)) {
            vendorRegistry.register(meta, metric, tags);
        }
    }

    /**
     * Register this metrics at the vendor metrics registry.
     */
    public void registerMetrics() {
        register(GLOBAL_FILE_COUNT, (Gauge<Long>) () -> embeddedStorageManager.createStorageStatistics().fileCount());
        register(LIVE_DATA_LENGTH, (Gauge<Long>) () -> embeddedStorageManager.createStorageStatistics().liveDataLength());
        register(TOTAL_DATA_LENGTH, (Gauge<Long>) () -> embeddedStorageManager.createStorageStatistics().totalDataLength());
    }

    /**
     * A fluent API builder to build instances of {@link MetricsSupport}.
     */
    public static final class Builder implements io.helidon.common.Builder<MicrostreamMetricsSupport> {

        private final EmbeddedStorageManager embeddedStorageManager;
        private Config config = Config.empty();
        private RegistryFactory registryFactory;

        private Builder(EmbeddedStorageManager embeddedStorageManager) {
            Objects.requireNonNull(embeddedStorageManager);
            this.embeddedStorageManager = embeddedStorageManager;
        }

        @Override
        public MicrostreamMetricsSupport build() {
            return new MicrostreamMetricsSupport(this);
        }

        /**
         * get the current configured RegistryFactory.
         * @return RegistryFactory
         */
        public RegistryFactory registryFactory() {
            return this.registryFactory;
        }

        /**
         * get the current configuredEmbeddedStorageManager.
         * @return EmbeddedStorageManager
         */
        public EmbeddedStorageManager embeddedStorageManager() {
            return this.embeddedStorageManager;
        }

        /**
         * get the current configured helidon configuration.
         * @return Config
         */
        public Config config() {
            return this.config;
        }

        /**
         * set the RegistryFactory.
         *
         * @param registryFactory
         * @return MicrostreamMetricsSupport builder
         */
        public Builder registryFactory(RegistryFactory registryFactory) {
            this.registryFactory = registryFactory;
            return this;
        }

        /**
         * set the helidon configuration used by the builder.
         *
         * @param config
         * @return MicrostreamMetricsSupport builder
         */
        public Builder config(Config config) {
            this.config = config;
            return this;
        }
    }
}
