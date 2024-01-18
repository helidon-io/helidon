/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.eclipsestore.metrics;

import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

import io.helidon.common.config.Config;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageRawFileStatistics;

import static io.helidon.metrics.api.Meter.BaseUnits.BYTES;

/**
 * Helper class that provides the default metrics for an Eclipse Store EmbeddedStorageManager.
 */
public class EclipseStoreHealthMetricsSupport {

    private static final String CONFIG_METRIC_ENABLED_VENDOR = "vendor.";

    private static final GaugeInfo<StorageRawFileStatistics> GLOBAL_FILE_COUNT =
            new GaugeInfo<>("eclipsestore.globalFileCount",
                    "Displays the number of storage files.",
                    null,
                    StorageRawFileStatistics::fileCount);

    private static final GaugeInfo<StorageRawFileStatistics> LIVE_DATA_LENGTH =
            new GaugeInfo<>("eclipsestore.liveDataLength",
                    "Displays live data length. This is the 'real' size of the stored data.",
                    BYTES,
                    StorageRawFileStatistics::liveDataLength);

    private static final GaugeInfo<StorageRawFileStatistics> TOTAL_DATA_LENGTH =
            new GaugeInfo<>("eclipsestore.totalDataLength",
                    "Displays total data length. This is the accumulated size of all storage data files.",
                    BYTES,
                    StorageRawFileStatistics::totalDataLength);

    private final Config config;
    private final EmbeddedStorageManager embeddedStorageManager;
    private final MeterRegistry vendorRegistry;

    private EclipseStoreHealthMetricsSupport(Builder builder) {
        super();
        this.config = builder.config();
        this.embeddedStorageManager = builder.embeddedStorageManager();

        MetricsFactory metricsFactory;
        if (builder.metricsFactory() == null) {
            metricsFactory = MetricsFactory.getInstance(config.get(MetricsConfig.METRICS_CONFIG_KEY));
        } else {
            metricsFactory = builder.metricsFactory();
        }

        this.vendorRegistry = metricsFactory.globalRegistry();
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

    private void register(GaugeInfo<StorageRawFileStatistics> gaugeInfo, StorageRawFileStatistics stats) {
        if (config.get(CONFIG_METRIC_ENABLED_VENDOR + gaugeInfo.name + ".enabled")
                .asBoolean()
                .orElse(true)) {
            vendorRegistry.getOrCreate(gaugeInfo.builder(stats));
        }
    }

    /**
     * Register this metrics at the vendor metrics registry.
     */
    public void registerMetrics() {
        StorageRawFileStatistics stats = embeddedStorageManager.createStorageStatistics();
        register(GLOBAL_FILE_COUNT, stats);
        register(LIVE_DATA_LENGTH, stats);
        register(TOTAL_DATA_LENGTH, stats);
    }

    private record GaugeInfo<T>(String name,
                                String description,
                                String unit,
                                ToDoubleFunction<T> fn,
                                Tag... tags) {

        Gauge.Builder<Double> builder(T stateObject) {
            Gauge.Builder<Double> builder = Gauge.builder(name, stateObject, fn)
                    .description(description);
            if (unit != null) {
                builder.baseUnit(unit);
            }
            if (tags != null) {
                builder.tags(List.of(tags));
            }
            return builder;
        }
    }

    /**
     * A fluent API builder to build instances of {@link io.helidon.integrations.eclipsestore.metrics.EclipseStoreHealthMetricsSupport}.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, EclipseStoreHealthMetricsSupport> {

        private final EmbeddedStorageManager embeddedStorageManager;
        private Config config = Config.empty();
        private MetricsFactory metricsFactory;

        private Builder(EmbeddedStorageManager embeddedStorageManager) {
            Objects.requireNonNull(embeddedStorageManager);
            this.embeddedStorageManager = embeddedStorageManager;
        }

        @Override
        public EclipseStoreHealthMetricsSupport build() {
            return new EclipseStoreHealthMetricsSupport(this);
        }

        /**
         * get the current configured MetricsFactory.
         *
         * @return MetricsFactory
         */
        public MetricsFactory metricsFactory() {
            return this.metricsFactory;
        }

        /**
         * get the current configuredEmbeddedStorageManager.
         *
         * @return EmbeddedStorageManager
         */
        public EmbeddedStorageManager embeddedStorageManager() {
            return this.embeddedStorageManager;
        }

        /**
         * get the current configured helidon configuration.
         *
         * @return Config
         */
        public Config config() {
            return this.config;
        }

        /**
         * set the MetricsFactory.
         *
         * @param metricsFactory metrics factory
         * @return EclipseStoreHealthMetricsSupport builder
         */
        public Builder metricsFactory(MetricsFactory metricsFactory) {
            this.metricsFactory = metricsFactory;
            return this;
        }

        /**
         * set the helidon configuration used by the builder.
         *
         * @param config configuration
         * @return EclipseStoreHealthMetricsSupport builder
         */
        public Builder config(Config config) {
            this.config = config;
            return this;
        }
    }
}
