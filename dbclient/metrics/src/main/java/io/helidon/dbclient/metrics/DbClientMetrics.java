/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

/**
 * Utility class to obtain various types of metrics to register
 * with {@link io.helidon.dbclient.DbClient.Builder#addService(io.helidon.dbclient.DbClientService)}.
 * Metrics can be limited to a set of statement types or statement names, and also configured to
 * meter success, failure or both.
 *
 * @see io.helidon.dbclient.metrics.DbClientMetricBuilder#statementTypes(io.helidon.dbclient.DbStatementType...)
 * @see io.helidon.dbclient.metrics.DbClientMetricBuilder#statementNames(String...)
 * @see io.helidon.dbclient.metrics.DbClientMetricBuilder#statementPredicate(java.util.function.Predicate)
 * @see io.helidon.dbclient.metrics.DbClientMetricBuilder#success(boolean)
 * @see io.helidon.dbclient.metrics.DbClientMetricBuilder#errors(boolean)
 */
public class DbClientMetrics {
    private DbClientMetrics() {
    }

    /**
     * Create a counter builder, to be registered
     * with {@link io.helidon.dbclient.DbClient.Builder#addService(java.util.function.Supplier)}.
     *
     * @return a new counter builder
     * @see org.eclipse.microprofile.metrics.Counter
     */
    public static DbClientMetricBuilder counter() {
        return DbClientCounter.builder();
    }

    /**
     * Create a meter builder, to be registered
     * with {@link io.helidon.dbclient.DbClient.Builder#addService(java.util.function.Supplier)}.
     *
     * @return a new meter builder
     * @see org.eclipse.microprofile.metrics.Meter
     */
    public static DbClientMetricBuilder meter() {
        return DbClientMeter.builder();
    }

    /**
     * Create a timer builder, to be registered
     * with {@link io.helidon.dbclient.DbClient.Builder#addService(java.util.function.Supplier)}.
     *
     * @return a new timer builder
     * @see org.eclipse.microprofile.metrics.Timer
     */
    public static DbClientMetricBuilder timer() {
        return DbClientTimer.builder();
    }
}
