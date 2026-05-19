/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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
 * Database Client metric builder.
 *
 * @param <B> type of the builder extending this class
 * @param <T> Type of the built {@link DbClientServiceBase} instance
 */
public abstract class DbClientMetricBuilder<B extends DbClientMetricBuilder<B, T>, T extends DbClientServiceBase>
        extends MetricBuilderBase<B, T> {

    /**
     * Creates a database client metric builder.
     * Child classes may call this constructor; only direct public use is not supported.
     *
     * @deprecated direct public use is not supported; use {@link DbClientMetrics#counter()}
     *         or {@link DbClientMetrics#timer()} for the built-in metric builders
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    public DbClientMetricBuilder() {
    }
}
