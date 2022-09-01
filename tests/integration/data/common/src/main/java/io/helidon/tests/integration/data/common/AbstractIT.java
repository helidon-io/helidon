/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.data.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.reactive.dbclient.DbClient;
import io.helidon.reactive.dbclient.DbMapper;
import io.helidon.reactive.dbclient.DbRow;

/**
 * Common testing code.
 */
public abstract class AbstractIT {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(AbstractIT.class.getName());

    public static final Config CONFIG = Config.create(ConfigSources.classpath(ConfigIT.configFile()));

    public static final DbClient DB_CLIENT = initDbClient();

    /**
     * Initialize database client.
     *
     * @return database client instance
     */
    public static DbClient initDbClient() {
        Config dbConfig = CONFIG.get("db");
        return DbClient.builder(dbConfig).build();
    }

}
