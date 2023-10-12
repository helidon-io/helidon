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

import io.helidon.dbclient.DbClientServiceBase;

/**
 * Utility class to obtain various types of metrics to register
 * with {@link io.helidon.dbclient.DbClient.Builder#addService(io.helidon.dbclient.DbClientService)}.
 * Metrics can be limited to a set of statement types or statement names, and also configured to
 * meter success, failure or both.
 *
 * @see DbClientMetricBuilder#statementTypes(io.helidon.dbclient.DbStatementType...)
 * @see DbClientMetricBuilder#statementNames(String...)
 * @see DbClientMetricBuilder#statementPredicate(java.util.function.Predicate)
 * @see DbClientMetricBuilder#success(boolean)
 * @see DbClientMetricBuilder#errors(boolean)
 */
public class DbClientMetrics {
    private DbClientMetrics() {
    }

    /**
     * Create a counter builder, to be registered
     * with {@link io.helidon.dbclient.DbClient.Builder#addService(java.util.function.Supplier)}.
     *
     * @return a new counter builder
     * @see io.helidon.metrics.api.Counter
     */
    public static DbClientMetricBuilder<? extends DbClientMetricBuilder<?, ?>, ? extends DbClientServiceBase> counter() {
        return MetricCounter.builder();
    }

    /**
     * Create a timer builder, to be registered
     * with {@link io.helidon.dbclient.DbClient.Builder#addService(java.util.function.Supplier)}.
     *
     * @return a new timer builder
     * @see io.helidon.metrics.api.Timer
     */
    public static DbClientMetricBuilder<? extends DbClientMetricBuilder<?, ?>, ? extends DbClientServiceBase> timer() {
        return MetricTimer.builder();
    }

}
