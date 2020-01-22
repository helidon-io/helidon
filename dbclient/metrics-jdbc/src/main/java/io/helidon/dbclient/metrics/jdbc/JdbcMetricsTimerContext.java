/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient.metrics.jdbc;

import org.eclipse.microprofile.metrics.Timer;

/**
 * Metric {@link Timer.Context} wrapper for Hikari CP metric.
 */
public class JdbcMetricsTimerContext implements Timer.Context {

    private final com.codahale.metrics.Timer.Context context;

    JdbcMetricsTimerContext(final com.codahale.metrics.Timer.Context context) {
        this.context = context;
    }

    @Override
    public long stop() {
        return context.stop();
    }

    @Override
    public void close() {
        context.close();
    }

}
