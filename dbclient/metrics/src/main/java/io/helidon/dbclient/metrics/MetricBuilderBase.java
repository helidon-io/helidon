/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.metrics;

import java.util.function.BiFunction;

import io.helidon.common.config.Config;
import io.helidon.dbclient.DbClientServiceBase;
import io.helidon.dbclient.DbStatementType;

/**
 * A {@link io.helidon.dbclient.DbClientService} builder base for metrics.
 *
 * @param <B> type of the builder extending this class
 * @param <T> Type of the built {@link DbClientServiceBase} instance
 */
abstract class MetricBuilderBase<B extends MetricBuilderBase<B, T>, T extends DbClientServiceBase>
        extends DbClientServiceBase.BuilderBase<B, T> {

    private MeterMetadata meta;
    private BiFunction<String, DbStatementType, String> nameFormat;
    private boolean measureErrors = true;
    private boolean measureSuccess = true;
    private String description;

    /**
     * Configure a metric name.
     *
     * @param metricName name of the metric
     * @return updated builder instance
     */
    public B name(String metricName) {
        nameFormat = (s, s2) -> metricName;
        return identity();
    }

    /**
     * Configure metric metadata.
     *
     * @param meta metric metadata
     * @return updated builder instance
     */
    public B metadata(MeterMetadata meta) {
        this.meta = meta;
        return identity();
    }

    /**
     * Configure a name format.
     * <p>The format can use up to two parameters - first is the {@link io.helidon.dbclient.DbStatementType}
     * as a string, second is the statement name.
     *
     * @param format format string expecting zero to two parameters that can be processed by
     *               {@link String#format(String, Object...)}
     * @return updated builder instance
     */
    public B nameFormat(String format) {
        return nameFormat((name, queryType) -> String.format(format, queryType.toString(), name));
    }

    /**
     * Configure a name format function.
     * <p>The first parameter is the statement name.
     *
     * @param function function to use to create metric name
     * @return updated builder instance
     */
    public B nameFormat(BiFunction<String, DbStatementType, String> function) {
        this.nameFormat = function;
        return identity();
    }

    /**
     * Whether to measure failed statements.
     *
     * @param measureErrors set to {@code false} if errors should be ignored
     * @return updated builder instance
     */
    public B errors(boolean measureErrors) {
        this.measureErrors = measureErrors;
        return identity();
    }

    /**
     * Whether to measure successful statements.
     *
     * @param measureSuccess set to {@code false} if successes should be ignored
     * @return updated builder instance
     */
    public B success(boolean measureSuccess) {
        this.measureSuccess = measureSuccess;
        return identity();
    }

    /**
     * Description of the metric used in metric metadata.
     *
     * @param description description
     * @return updated builder instance
     */
    public B description(String description) {
        this.description = description;
        return identity();
    }

    /**
     * Configure a metric from configuration.
     * The following configuration key are used:
     * <table>
     * <caption>DB Metric configuration options</caption>
     * <tr>
     *     <th>key</th>
     *     <th>default</th>
     *     <th>description</th>
     * </tr>
     * <tr>
     *     <td>errors</td>
     *     <td>{@code true}</td>
     *     <td>Whether this metric triggers for error states</td>
     * </tr>
     * <tr>
     *     <td>success</td>
     *     <td>{@code true}</td>
     *     <td>Whether this metric triggers for successful executions</td>
     * </tr>
     * <tr>
     *     <td>name-format</td>
     *     <td>{@code db.metric-type.statement-name}</td>
     *     <td>A string format used to construct a metric name. The format gets two parameters: the statement name and the
     *     statement type</td>
     * </tr>
     * <tr>
     *     <td>description</td>
     *     <td>&nbsp;</td>
     *     <td>Description of this metric.</td>
     * </tr>
     * </table>
     *
     * @param config configuration to configure this metric
     * @return updated builder instance
     */
    public B config(Config config) {
        super.config(config);
        config.get("errors").asBoolean().ifPresent(this::errors);
        config.get("success").asBoolean().ifPresent(this::success);
        config.get("name-format").asString().ifPresent(this::nameFormat);
        config.get("description").asString().ifPresent(this::description);
        return identity();
    }

    /**
     * Get the description.
     *
     * @return description
     */
    String description() {
        return description;
    }

    /**
     * Get the metadata.
     *
     * @return metadata
     */
    MeterMetadata meta() {
        return meta;
    }

    /**
     * Get the name format function.
     *
     * @return function
     */
    BiFunction<String, DbStatementType, String> nameFormat() {
        return nameFormat;
    }

    /**
     * Indicate if errors are measured.
     *
     * @return {@code true} if errors are measured
     */
    boolean errors() {
        return measureErrors;
    }

    /**
     * Indicate if success is measured.
     *
     * @return {@code true} if success is measured
     */
    boolean success() {
        return measureSuccess;
    }
}
