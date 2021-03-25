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

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.common.rest.ApiJsonBuilder;
import io.helidon.integrations.oci.connect.OciRequestBase;

/**
 * Class to group together all types related to post metric data API.
 */
public final class PostMetricData {
    private PostMetricData() {
    }

    public static class MetricData extends OciRequestBase<MetricData> {
        public static MetricData builder() {
            return new MetricData();
        }

        /**
         * The OCID of the compartment to use for metrics.
         *
         * @param compartmentId compartment OCID
         * @return updated data
         */
        public MetricData compartmentId(String compartmentId) {
            return add("compartmentId", compartmentId);
        }

        /**
         * A list of metric values with timestamps. At least one data point is required per call.
         *
         * @param dataPoint data point
         * @return updated data
         */
        public MetricData addDataPoint(MetricDataPoint dataPoint) {
            return addToArray("datapoints", dataPoint);
        }

        /**
         * A utility method to add data point with count equal to {@code 1}.
         * For full control, please use {@link io.helidon.integrations.oci.telemetry.PostMetricData.MetricDataPoint#builder()}.
         *
         * @param timestamp temporal accessor, such as {@link java.time.Instant}
         * @param value Numeric value of the metric.
         * @return updated data
         */
        public MetricData addDataPoint(TemporalAccessor timestamp, double value) {
            return addDataPoint(MetricDataPoint.builder().timestamp(timestamp).value(value));
        }

        /**
         * Qualifiers provided in a metric definition. Available dimensions vary by metric namespace. Each dimension takes the
         * form
         * of a key-value pair. A valid dimension key includes only printable ASCII, excluding periods (.) and spaces. The
         * character limit for a dimension key is 256. A valid dimension value includes only Unicode characters. The character
         * limit for a dimension value is 256. Empty strings are not allowed for keys or values. Avoid entering confidential
         * information.
         *
         * @param key dimension key
         * @param value dimension value
         * @return updated data
         */
        public MetricData addDimension(String key, String value) {
            return addToObject("dimensions", key, value);
        }

        /**
         * Properties describing metrics. These are not part of the unique fields identifying the metric. Each metadata item takes
         * the form of a key-value pair. The character limit for a metadata key is 256. The character limit for a metadata
         * value is
         * 256.
         * <p>
         * Example: {@code "unit": "bytes"}.
         *
         * @param key name of metadata
         * @param value value of metadata
         * @return updated data
         */
        public MetricData addMetaData(String key, String value) {
            return addToObject("metadata", key, value);
        }

        /**
         * The name of the metric.
         *
         * A valid name value starts with an alphabetical character and includes only alphanumeric characters, dots, underscores,
         * hyphens, and dollar signs. The oci_ prefix is reserved. Avoid entering confidential information.
         *
         * @param name name
         * @return updated request
         */
        public MetricData name(String name) {
            return add("name", name);
        }

        /**
         * The source service or application emitting the metric.
         *
         * A valid namespace value starts with an alphabetical character and includes only alphanumeric characters and
         * underscores.
         * The "oci_" prefix is reserved. Avoid entering confidential information.
         *
         * @param namespace namespace
         * @return updated request
         */
        public MetricData namespace(String namespace) {
            return add("namespace", namespace);
        }

        /**
         * Resource group to assign to the metric. A resource group is a custom string that you can match when retrieving custom
         * metrics. Only one resource group can be applied per metric. A valid resourceGroup value starts with an alphabetical
         * character and includes only alphanumeric characters, periods (.), underscores (_), hyphens (-), and dollar signs ($).
         * Avoid entering confidential information.
         *
         * @param resourceGroup resource group
         * @return updated request
         */
        public MetricData resourceGroup(String resourceGroup) {
            return add("resourceGroup", resourceGroup);
        }

    }

    /**
     * https://docs.oracle.com/en-us/iaas/api/#/en/monitoring/20180401/datatypes/MetricDataDetails
     */
    public static class Request extends OciRequestBase<Request> {
        public static final String BATCH_ATOMICITY_ATOMIC = "ATOMIC";
        public static final String BATCH_ATOMICITY_NON_ATOMIC = "NON_ATOMIC";

        private Request() {
        }

        public static Request builder() {
            return new Request();
        }

        /**
         * Batch atomicity behavior. Requires either partial or full pass of input validation for metric objects in PostMetricData
         * requests. The default value of NON_ATOMIC requires a partial pass: at least one metric object in the request must pass
         * input validation, and any objects that failed validation are identified in the returned summary, along with their error
         * messages. A value of ATOMIC requires a full pass: all metric objects in the request must pass input validation.
         * <p>
         * Defaults to {@value #BATCH_ATOMICITY_NON_ATOMIC}.
         *
         * @param atomicity atomicity to use
         * @return updated request
         * @see #BATCH_ATOMICITY_ATOMIC
         * @see #BATCH_ATOMICITY_NON_ATOMIC
         */
        public Request batchAtomicity(String atomicity) {
            return add("batchAtomicity", atomicity);
        }

        /**
         * Raw metric data points to be posted to the monitoring service.
         *
         * @param metricData metric data
         * @return updated request
         */
        public Request addMetricData(MetricData metricData) {
            return addToArray("metricData", metricData);
        }
    }

    public static class Response extends ApiEntityResponse {
        private final int failedMetricsCount;
        private final List<FailedMetric> failedMetrics;

        private Response(Builder builder) {
            super(builder);

            JsonObject jsonObject = builder.entity();
            failedMetricsCount = jsonObject.getInt("failedMetricsCount");
            failedMetrics = isPresent(jsonObject, "failedMetrics")
                    .map(ignored -> {
                        List<FailedMetric> failedMetrics = new LinkedList<>();
                        JsonArray failedMetricsArray = jsonObject.getJsonArray("failedMetrics");
                        for (JsonValue jsonValue : failedMetricsArray) {
                            failedMetrics.add(FailedMetric.create((JsonObject) jsonValue));
                        }
                        return List.copyOf(failedMetrics);
                    }).orElseGet(List::of);
        }
        public int failedMetricsCount() {
            return failedMetricsCount;
        }

        public List<FailedMetric> failedMetrics() {
            return failedMetrics;
        }

        static Builder builder() {
            return new Builder();
        }

        static final class Builder extends ApiEntityResponse.Builder<Builder, Response, JsonObject> {
            private Builder() {
            }

            @Override
            public Response build() {
                return new Response(this);
            }
        }
    }

    public static class MetricDataPoint extends ApiJsonBuilder<MetricDataPoint> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

        private MetricDataPoint() {
        }

        public static MetricDataPoint builder() {
            return new MetricDataPoint();
        }

        /**
         * The number of occurrences of the associated value in the set of data.
         *
         * Default is 1. Value must be greater than zero.
         *
         * @param count
         * @return
         */
        public MetricDataPoint count(int count) {
            return add("count", count);
        }

        /**
         * Numeric value of the metric.
         *
         * @param value
         * @return
         */
        public MetricDataPoint value(double value) {
            return add("value", value);
        }

        public MetricDataPoint timestamp(TemporalAccessor instant) {
            return add("timestamp", FORMATTER.format(instant));
        }

        public MetricDataPoint timestamp(Date time) {
            return timestamp(time.toInstant());
        }
    }

    public static class FailedMetric {
        private final String message;
        private final JsonObject failedData;

        private FailedMetric(String message, JsonObject failedData) {
            this.message = message;
            this.failedData = failedData;
        }

        static FailedMetric create(JsonObject failedMetric) {
            String message = failedMetric.getString("message");

            return new FailedMetric(message, failedMetric.getJsonObject("metricData"));
        }

        public String message() {
            return message;
        }

        public JsonObject failedDataJson() {
            return failedData;
        }
    }
}
