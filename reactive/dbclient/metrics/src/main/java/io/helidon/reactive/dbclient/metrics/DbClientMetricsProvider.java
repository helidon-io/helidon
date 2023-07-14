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
package io.helidon.reactive.dbclient.metrics;

import java.lang.System.Logger.Level;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.reactive.dbclient.DbClientException;
import io.helidon.reactive.dbclient.DbClientService;
import io.helidon.reactive.dbclient.spi.DbClientServiceProvider;

/**
 * Java service loader service for DB metrics.
 */
public class DbClientMetricsProvider implements DbClientServiceProvider {
    private static final System.Logger LOGGER = System.getLogger(DbClientMetricsProvider.class.getName());

    @Override
    public String configKey() {
        return "metrics";
    }

    @Override
    public Collection<DbClientService> create(Config config) {
        List<Config> metricConfigs = config.asNodeList().orElseGet(List::of);
        List<DbClientService> result = new LinkedList<>();

        for (Config metricConfig : metricConfigs) {
            result.add(fromConfig(metricConfig));
        }

        if (result.isEmpty()) {
            LOGGER.log(Level.INFO, "DB Client metrics are enabled, yet none are configured in config.");
        }

        return result;
    }

    private DbClientService fromConfig(Config config) {
        String type = config.get("type").asString().orElse("COUNTER");
        switch (type) {
        case "COUNTER":
            return DbClientMetrics.counter().config(config).build();
        case "TIMER":
            return DbClientMetrics.timer().config(config).build();
        default:
            throw new DbClientException("Metrics type " + type + " is not supported through service loader");
        }
    }
}
