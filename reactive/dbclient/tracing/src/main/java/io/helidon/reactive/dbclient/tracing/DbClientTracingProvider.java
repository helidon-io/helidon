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
package io.helidon.reactive.dbclient.tracing;

import java.lang.System.Logger.Level;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.reactive.dbclient.DbClientService;
import io.helidon.reactive.dbclient.spi.DbClientServiceProvider;

/**
 * Provider of tracing interceptors.
 */
public class DbClientTracingProvider implements DbClientServiceProvider {
    private static final System.Logger LOGGER = System.getLogger(DbClientTracingProvider.class.getName());

    @Override
    public String configKey() {
        return "tracing";
    }

    @Override
    public Collection<DbClientService> create(Config config) {
        List<Config> tracingConfigs = config.asNodeList().orElseGet(List::of);
        List<DbClientService> result = new LinkedList<>();

        for (Config tracingConfig : tracingConfigs) {
            result.add(fromConfig(tracingConfig));
        }

        if (result.isEmpty()) {
            LOGGER.log(Level.INFO, "DB Client tracing is enabled, yet none is configured in config.");
        }

        return result;
    }

    private DbClientService fromConfig(Config config) {
        return DbClientTracing.create(config);
    }
}
