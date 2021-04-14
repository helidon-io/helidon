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

/**
 * Blocking APIs for OCI Metrics.
 */
public interface OciMetrics {
    /**
     * Create blocking OCI metrics from its reactive counterpart.
     *
     * @param reactive reactive OCI metrics
     * @return blocking OCI metrics
     */
    static OciMetrics create(OciMetricsRx reactive) {
        return new OciMetricsImpl(reactive);
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
     * @return metric response
     */
    PostMetricData.Response postMetricData(PostMetricData.Request request);
}
