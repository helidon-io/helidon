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

package io.helidon.config.metadata.processor;

import io.helidon.common.types.TypeName;

final class UsedTypes {
    static final TypeName DEPRECATED = TypeName.create(Deprecated.class);
    /*
    Using config metadata
     */
    static final TypeName META_CONFIGURED = TypeName.create("io.helidon.config.metadata.Configured");
    static final TypeName META_OPTION = TypeName.create("io.helidon.config.metadata.ConfiguredOption");
    static final TypeName META_OPTIONS = TypeName.create("io.helidon.config.metadata.ConfiguredOptions");

    /*
    Using builder API
     */
    static final TypeName COMMON_CONFIG = TypeName.create("io.helidon.common.config.Config");
    static final TypeName CONFIG = TypeName.create("io.helidon.config.Config");
    static final TypeName PROTOTYPE_FACTORY = TypeName.create("io.helidon.builder.api.Prototype.Factory");
    static final TypeName BLUEPRINT = TypeName.create("io.helidon.builder.api.Prototype.Blueprint");
    static final TypeName CONFIGURED = TypeName.create("io.helidon.builder.api.Prototype.Configured");
    static final TypeName PROTOTYPE_PROVIDES = TypeName.create("io.helidon.builder.api.Prototype.Provides");
    static final TypeName DESCRIPTION = TypeName.create("io.helidon.builder.api.Description");
    static final TypeName OPTION_CONFIGURED = TypeName.create("io.helidon.builder.api.Option.Configured");
    static final TypeName OPTION_REQUIRED = TypeName.create("io.helidon.builder.api.Option.Required");
    static final TypeName OPTION_PROVIDER = TypeName.create("io.helidon.builder.api.Option.Provider");
    static final TypeName OPTION_ALLOWED_VALUES = TypeName.create("io.helidon.builder.api.Option.AllowedValues");
    static final TypeName OPTION_ALLOWED_VALUE = TypeName.create("io.helidon.builder.api.Option.AllowedValue");
    static final TypeName OPTION_DEFAULT = TypeName.create("io.helidon.builder.api.Option.Default");
    static final TypeName OPTION_DEFAULT_INT = TypeName.create("io.helidon.builder.api.Option.DefaultInt");
    static final TypeName OPTION_DEFAULT_DOUBLE = TypeName.create("io.helidon.builder.api.Option.DefaultDouble");
    static final TypeName OPTION_DEFAULT_BOOLEAN = TypeName.create("io.helidon.builder.api.Option.DefaultBoolean");
    static final TypeName OPTION_DEFAULT_LONG = TypeName.create("io.helidon.builder.api.Option.DefaultLong");
    static final TypeName OPTION_DEFAULT_METHOD = TypeName.create("io.helidon.builder.api.Option.DefaultMethod");
    static final TypeName OPTION_DEFAULT_CODE = TypeName.create("io.helidon.builder.api.Option.DefaultCode");

    private UsedTypes() {
    }
}
