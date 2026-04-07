/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.dbclient;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.mapper.Mappers;
import io.helidon.dbclient.spi.DbMapperProvider;

@Prototype.Blueprint(isPublic = false, createEmptyPublic = false)
@Prototype.CustomMethods(DbClientBuilderStateSupport.class)
interface DbClientBuilderStateBlueprint {

    Optional<String> url();

    Optional<String> username();

    Optional<String> password();

    @Option.DefaultBoolean(false)
    boolean missingMapParametersAsNull();

    Optional<DbStatements> statements();

    Optional<Mappers> mapperManager();

    Optional<DbMapperManager> dbMapperManager();

    @Option.Singular("mapperProvider")
    List<DbMapperProvider> mapperProviders();

    @Option.Singular("service")
    List<DbClientService> clientServices();

}
