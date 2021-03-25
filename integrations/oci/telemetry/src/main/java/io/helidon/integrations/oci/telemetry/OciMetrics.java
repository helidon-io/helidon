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

package io.helidon.integrations.oci.telemetry;

import java.net.URI;
import java.util.function.Consumer;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.oci.connect.OciRestApi;

/**
 * Reactive APIs for OCI Metrics.
 */
public interface OciMetrics {
    /**
     * Version of API supported by this client.
     */
    String API_VERSION = "20180401";
    /**
     * Host name prefix.
     */
    String API_HOST_PREFIX = "telemetry-ingestion";
    /**
     * Host format of API server.
     */
    String API_HOST_FORMAT = "https://%s.%s.oraclecloud.com";

    /**
     * Create a new fluent API builder for OCI metrics.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create OCI metrics using the default {@link io.helidon.integrations.oci.connect.OciRestApi}.
     *
     * @return OCI metrics instance connecting based on {@code DEFAULT} profile
     */
    static OciMetrics create() {
        return builder().build();
    }

    /**
     * Create OCI metrics based on configuration.
     *
     * @param config configuration on the node of OCI configuration
     * @return OCI metrics instance configured from the configuration
     * @see io.helidon.integrations.oci.telemetry.OciMetrics.Builder#config(io.helidon.config.Config)
     */
    static OciMetrics create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Publishes raw metric data points to the Monitoring service. For more information about publishing metrics, see
     * <a href="https://docs.oracle.com/iaas/Content/Monitoring/Tasks/publishingcustommetrics.htm">Publishing Custom Metrics</a>.
     * For important limits information, see
     * <a href="https://docs.oracle.com/iaas/Content/Monitoring/Concepts/monitoringoverview.htm#Limits">Limits on Monitoring</a>.
     *
     * Per-call limits information follows.
     *
     * Dimensions per metric group*. Maximum: 20. Minimum: 1.
     * Unique metric streams*. Maximum: 50.
     * Transactions Per Second (TPS) per-tenancy limit for this operation: 50.
     * *A metric group is the combination of a given metric, metric namespace, and tenancy for the purpose of determining
     * limits. A dimension is a qualifier provided in a metric definition. A metric stream is an individual set of aggregated
     * data for a metric, typically specific to a resource. For more information about metric-related concepts, see
     * <a href="https://docs.oracle.com/iaas/Content/Monitoring/Concepts/monitoringoverview.htm#concepts">Monitoring Concepts</a>.
     *
     * @param request metric request
     * @return future metric response
     */
    Single<PostMetricData.Response> postMetricData(PostMetricData.Request request);

    class Builder implements io.helidon.common.Builder<OciMetrics> {
        private final OciRestApi.Builder accessBuilder = OciRestApi.builder()
                .hostPrefix(API_HOST_PREFIX)
                .hostFormat(API_HOST_FORMAT);

        private String apiVersion = API_VERSION;

        private Builder() {
        }

        @Override
        public OciMetrics build() {
            return new OciMetricsImpl(this);
        }

        /**
         * Update from configuration. The configuration must be located on the {@code OCI} root configuration
         * node.
         *
         * @param config configuration
         * @return updated metrics builder
         */
        public Builder config(Config config) {
            accessBuilder.config(config);
            config.get("metrics.base-uri").as(URI.class).ifPresent(accessBuilder::baseUri);
            config.get("metrics.host-format").asString().ifPresent(accessBuilder::hostFormat);
            config.get("metrics.host-prefix").asString().ifPresent(accessBuilder::hostPrefix);
            config.get("metrics.api-version").asString().ifPresent(this::apiVersion);
            return this;
        }

        public  Builder apiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }

        /**
         * Update the rest access builder to modify defaults.
         *
         * @param builderConsumer consumer of the builder
         * @return updated metrics builder
         */
        public Builder updateRestAccess(Consumer<OciRestApi.Builder> builderConsumer) {
            builderConsumer.accept(accessBuilder);
            return this;
        }

        String apiVersion() {
            return apiVersion;
        }

        OciRestApi restAccess() {
            return accessBuilder.build();
        }
    }
}
