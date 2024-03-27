/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.junit5;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;

/**
 * Docker database container configuration extension bean.
 * This container configuration extension may be provided
 * in {@link io.helidon.tests.integration.junit5.spi.SuiteProvider} method annotated
 * with {@link io.helidon.tests.integration.junit5.SetUpContainer} annotation and having
 * {@link DatabaseContainerConfig.Builder} method parameter.
 */
@Prototype.Blueprint
@Prototype.Configured
interface DatabaseContainerConfigBlueprint {

    /**
     * Database configuration node matching structure of DbClient configuration.
     * This should be the content of {@code "connection"} node:<ul>
     *     <li><b>url</b> - the database connection URL without user name and password</li>
     *     <li><b>username</b> - the database connection user name</li>
     *     <li><b>password</b> - the database connection user password</li>
     * </ul>
     *
     * @return DbClient {@link Config} sub-node matching {@code "connection"} key.
     */
    @Option.Configured
    @Option.DefaultCode("Config.empty()")
    Config dbClient();

}
