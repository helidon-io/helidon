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

package io.helidon.common.types;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * An exports directive of a module info.
 */
@Prototype.Blueprint
interface ModuleInfoExportsBlueprint {
    /**
     * Name of the exported package.
     *
     * @return package name
     */
    String packageName();

    /**
     * Names of target modules, empty if exported without qualification.
     *
     * @return list of target modules
     */
    @Option.Singular
    List<String> targets();
}
