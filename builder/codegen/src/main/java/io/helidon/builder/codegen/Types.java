/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.codegen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import io.helidon.common.Generated;
import io.helidon.common.types.TypeName;

final class Types {
    static final TypeName COMMON_CONFIG = TypeName.create("io.helidon.common.config.Config");
    static final TypeName GENERATED = TypeName.create(Generated.class);
    static final TypeName DEPRECATED = TypeName.create(Deprecated.class);
    static final TypeName LINKED_HASH_MAP = TypeName.create(LinkedHashMap.class);
    static final TypeName ARRAY_LIST = TypeName.create(ArrayList.class);
    static final TypeName LINKED_HASH_SET = TypeName.create(LinkedHashSet.class);
    static final TypeName CHAR_ARRAY = TypeName.create(char[].class);

    static final TypeName BUILDER_DESCRIPTION = TypeName.create("io.helidon.builder.api.Description");

    static final TypeName PROTOTYPE_BLUEPRINT = TypeName.create("io.helidon.builder.api.Prototype.Blueprint");
    static final TypeName PROTOTYPE_IMPLEMENT = TypeName.create("io.helidon.builder.api.Prototype.Implement");
    static final TypeName PROTOTYPE_API = TypeName.create("io.helidon.builder.api.Prototype.Api");
    static final TypeName PROTOTYPE_ANNOTATED = TypeName.create("io.helidon.builder.api.Prototype.Annotated");
    static final TypeName PROTOTYPE_FACTORY = TypeName.create("io.helidon.builder.api.Prototype.Factory");
    static final TypeName PROTOTYPE_CONFIGURED = TypeName.create("io.helidon.builder.api.Prototype.Configured");
    static final TypeName PROTOTYPE_BUILDER = TypeName.create("io.helidon.builder.api.Prototype.Builder");
    static final TypeName PROTOTYPE_CONFIGURED_BUILDER = TypeName.create("io.helidon.builder.api.Prototype.ConfiguredBuilder");
    static final TypeName PROTOTYPE_CUSTOM_METHODS = TypeName.create("io.helidon.builder.api.Prototype.CustomMethods");
    static final TypeName PROTOTYPE_FACTORY_METHOD = TypeName.create("io.helidon.builder.api.Prototype.FactoryMethod");
    static final TypeName PROTOTYPE_BUILDER_METHOD = TypeName.create("io.helidon.builder.api.Prototype.BuilderMethod");
    static final TypeName PROTOTYPE_PROTOTYPE_METHOD = TypeName.create("io.helidon.builder.api.Prototype.PrototypeMethod");
    static final TypeName PROTOTYPE_BUILDER_DECORATOR = TypeName.create("io.helidon.builder.api.Prototype.BuilderDecorator");
    static final TypeName PROTOTYPE_CONSTANT = TypeName.create("io.helidon.builder.api.Prototype.Constant");

    static final TypeName RUNTIME_PROTOTYPE = TypeName.create("io.helidon.builder.api.RuntimeType.PrototypedBy");
    static final TypeName RUNTIME_PROTOTYPED_BY = TypeName.create("io.helidon.builder.api.RuntimeType.PrototypedBy");
    static final TypeName RUNTIME_API = TypeName.create("io.helidon.builder.api.RuntimeType.Api");

    static final TypeName OPTION_SAME_GENERIC = TypeName.create("io.helidon.builder.api.Option.SameGeneric");
    static final TypeName OPTION_SINGULAR = TypeName.create("io.helidon.builder.api.Option.Singular");
    static final TypeName OPTION_CONFIDENTIAL = TypeName.create("io.helidon.builder.api.Option.Confidential");
    static final TypeName OPTION_REDUNDANT = TypeName.create("io.helidon.builder.api.Option.Redundant");
    static final TypeName OPTION_CONFIGURED = TypeName.create("io.helidon.builder.api.Option.Configured");
    static final TypeName OPTION_ACCESS = TypeName.create("io.helidon.builder.api.Option.Access");
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
    static final TypeName OPTION_DEPRECATED = TypeName.create("io.helidon.builder.api.Option.Deprecated");
    static final TypeName OPTION_TYPE = TypeName.create("io.helidon.builder.api.Option.Type");

    private Types() {
    }
}
