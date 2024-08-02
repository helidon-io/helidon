/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.h2;

import java.util.Map;
import java.util.function.BiFunction;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.dbclient.common.LocalTextContext;

/**
 * Base class for the local tests.
 */
abstract class H2LocalTest {

    static <T> LocalTextContext<T> context(BiFunction<DbClient, Config, T> factory) {
        return LocalTextContext.create(factory, Map.of(), true);
    }

    static void shutdown(LocalTextContext<?> ctx) {
        try {
            ctx.db().execute().dml("SHUTDOWN");
        } catch (Throwable ignored) {
        }
    }
}
