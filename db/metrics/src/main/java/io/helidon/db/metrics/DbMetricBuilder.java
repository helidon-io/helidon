/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.db.metrics;

import java.util.function.BiFunction;

import io.helidon.config.Config;
import io.helidon.db.DbStatementType;

import org.eclipse.microprofile.metrics.Metadata;

/**
 * A metric builder used as a base for Helidon DB metrics.
 * @param <T> type of the builder extending this class
 */
public abstract class DbMetricBuilder<T extends DbMetricBuilder<T>> {
    private Metadata meta;
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
    public T name(String metricName) {
        nameFormat = (s, s2) -> metricName;
        return me();
    }

    /**
     * Configure metric metadata.
     *
     * @param meta metric metadata
     * @return updated builder instance
     */
    public T metadata(Metadata meta) {
        this.meta = meta;
        return me();
    }

    /**
     * Configure a name format.
     * <p>The format can use up to two parameters - first is the statement name, second the {@link io.helidon.db.DbStatementType}
     *  as a string.
     *
     * @param format format string expecting zero to two parameters that can be processed by
     *          {@link String#format(String, Object...)}
     * @return updated builder instance
     */
    public T nameFormat(String format) {
        return nameFormat((name, queryType) -> String.format(format, name, queryType.toString()));
    }

    /**
     * Configure a name format function.
     * <p>The first parameter is the statement name.
     *
     * @param function function to use to create metric name
     * @return updated builder instance
     */
    public T nameFormat(BiFunction<String, DbStatementType, String> function) {
        this.nameFormat = function;
        return me();
    }

    /**
     * Whether to measure failed statements.
     *
     * @param shouldWe set to {@code false} if errors should be ignored
     * @return updated builder instance
     */
    public T measureErrors(boolean shouldWe) {
        this.measureErrors = shouldWe;
        return me();
    }

    /**
     * Whether to measure successful statements.
     *
     * @param shouldWe set to {@code false} if successes should be ignored
     * @return updated builder instance
     */
    public T measureSuccess(boolean shouldWe) {
        this.measureSuccess = shouldWe;
        return me();
    }

    /**
     * Description of the metric used in metric metadata.
     *
     * @param description description
     * @return updated builder instance
     */
    public T description(String description) {
        this.description = description;
        return me();
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
     *     <td>Description of this metric, used in metric {@link org.eclipse.microprofile.metrics.Metadata}</td>
     * </tr>
     * </table>
     *
     * @param config configuration to configure this metric
     * @return updated builder instance
     */
    public T config(Config config) {
        config.get("errors").asBoolean().ifPresent(this::measureErrors);
        config.get("success").asBoolean().ifPresent(this::measureSuccess);
        config.get("name-format").asString().ifPresent(this::nameFormat);
        config.get("description").asString().ifPresent(this::description);
        return me();
    }

    String description() {
        return description;
    }

    @SuppressWarnings("unchecked")
    T me() {
        return (T) this;
    }

    Metadata meta() {
        return meta;
    }

    BiFunction<String, DbStatementType, String> nameFormat() {
        return nameFormat;
    }

    boolean measureErrors() {
        return measureErrors;
    }

    boolean measureSuccess() {
        return measureSuccess;
    }
}
