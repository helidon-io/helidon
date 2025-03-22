/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.service.codegen;


import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Parameters to code generate default values.
 */
@Prototype.Blueprint
interface DefaultsParamsBlueprint {
    /**
     * Qualifier used when mapping string values to target type.
     *
     * @return mapper qualifier
     */
    @Option.Default("defaults")
    String mapperQualifier();

    /**
     * Name of the field/variable that contains a {@code Mappers} instance.
     *
     * @return mappers field name
     */
    @Option.Default("mappers")
    String mappersField();

    /**
     * Name as sent to default value provider.
     *
     * @return name to use with provider
     */
    @Option.Default("default")
    String name();

    /**
     * Name of the field/variable of the context to be sent to default value provider.
     *
     * @return context name
     */
    Optional<String> contextField();
}
