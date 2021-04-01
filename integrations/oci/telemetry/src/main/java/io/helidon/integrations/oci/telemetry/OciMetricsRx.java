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

import java.util.function.Consumer;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.oci.connect.OciRestApi;

/**
 * Reactive APIs for OCI Metrics.
 */
public interface OciMetricsRx {
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
    String API_HOST_FORMAT = "%s://%s.%s.%s";

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
    static OciMetricsRx create() {
        return builder().build();
    }

    /**
     * Create OCI metrics based on configuration.
     *
     * @param config configuration on the node of OCI configuration
     * @return OCI metrics instance configured from the configuration
     * @see OciMetricsRx.Builder#config(io.helidon.config.Config)
     */
    static OciMetricsRx create(Config config) {
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

    class Builder implements io.helidon.common.Builder<OciMetricsRx> {
        private final OciRestApi.Builder apiBuilder = OciRestApi.builder();

        private String apiVersion = API_VERSION;
        private String hostPrefix = API_HOST_PREFIX;
        private String endpoint;
        private OciRestApi restApi;

        private Builder() {
        }

        @Override
        public OciMetricsRx build() {
            if (restApi == null) {
                restApi = apiBuilder.build();
            }

            return new OciMetricsRxImpl(this);
        }

        /**
         * Update from configuration. The configuration must be located on the {@code OCI} root configuration
         * node.
         *
         * @param config configuration
         * @return updated metrics builder
         */
        public Builder config(Config config) {
            apiBuilder.config(config);
            config.get("metrics.host-prefix").asString().ifPresent(this::hostPrefix);
            config.get("metrics.endpoint").asString().ifPresent(this::endpoint);
            config.get("metrics.api-version").asString().ifPresent(this::apiVersion);
            return this;
        }

        public Builder hostPrefix(String prefix) {
            this.hostPrefix = prefix;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder apiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }

        /**
         * Update the rest API builder to modify defaults.
         *
         * @param builderConsumer consumer of the builder
         * @return updated metrics builder
         */
        public Builder updateRestApi(Consumer<OciRestApi.Builder> builderConsumer) {
            builderConsumer.accept(apiBuilder);
            return this;
        }

        public Builder restApi(OciRestApi restApi) {
            this.restApi = restApi;
            return this;
        }

        String apiVersion() {
            return apiVersion;
        }

        OciRestApi restApi() {
            return restApi;
        }

        String hostPrefix() {
            return hostPrefix;
        }

        String endpoint() {
            return endpoint;
        }
    }
}
