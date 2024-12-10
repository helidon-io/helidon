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

package io.helidon.builder.processor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import io.helidon.common.Generated;
import io.helidon.common.types.TypeName;

final class Types {
    static final String OVERRIDE = Override.class.getName();
    static final String GENERATED = Generated.class.getName();
    static final String CONFIG = "io.helidon.common.config.Config";
    static final String CONFIGURED = "io.helidon.config.metadata.Configured";
    static final String CONFIGURED_OPTION = "io.helidon.config.metadata.ConfiguredOption";
    static final String RUNTIME_OBJECT = "io.helidon.builder.api.RuntimeType.Api";
    static final String RUNTIME_PROTOTYPE = "io.helidon.builder.api.RuntimeType.PrototypedBy";
    static final String PROTOTYPE = "io.helidon.builder.api.Prototype.Api";
    static final String BUILDER_DECORATOR = "io.helidon.builder.api.Prototype.BuilderDecorator";
    static final String IMPLEMENT = "io.helidon.builder.api.Prototype.Implement";
    static final String FACTORY_METHOD = "io.helidon.builder.api.Prototype.FactoryMethod";
    static final String PROTOTYPE_BUILDER = "io.helidon.builder.api.Prototype.Builder";
    static final String PROTOTYPE_CONFIGURED = "io.helidon.builder.api.Prototype.Configured";
    static final String PROTOTYPE_BLUEPRINT = "io.helidon.builder.api.Prototype.Blueprint";
    static final String PROTOTYPE_FACTORY = "io.helidon.builder.api.Prototype.Factory";
    static final String PROTOTYPE_ANNOTATED = "io.helidon.builder.api.Prototype.Annotated";
    static final String CUSTOM_METHODS = "io.helidon.builder.api.Prototype.CustomMethods";
    static final String PROTOTYPE_CUSTOM_METHOD = "io.helidon.builder.api.Prototype.PrototypeMethod";
    static final String BUILDER_CUSTOM_METHOD = "io.helidon.builder.api.Prototype.BuilderMethod";
    static final String DESCRIPTION = "io.helidon.builder.api.Description";
    static final String OPTION_SINGULAR = "io.helidon.builder.api.Option.Singular";
    static final String OPTION_SAME_GENERIC = "io.helidon.builder.api.Option.SameGeneric";
    static final String OPTION_CONFIDENTIAL = "io.helidon.builder.api.Option.Confidential";
    static final String OPTION_REDUNDANT = "io.helidon.builder.api.Option.Redundant";
    static final String OPTION_CONFIGURED = "io.helidon.builder.api.Option.Configured";
    static final String OPTION_ACCESS = "io.helidon.builder.api.Option.Access";
    static final String OPTION_REQUIRED = "io.helidon.builder.api.Option.Required";
    static final String OPTION_PROVIDER = "io.helidon.builder.api.Option.Provider";
    static final String OPTION_ALLOWED_VALUES = "io.helidon.builder.api.Option.AllowedValues";
    static final String OPTION_ALLOWED_VALUE = "io.helidon.builder.api.Option.AllowedValue";
    static final String OPTION_DEFAULT = "io.helidon.builder.api.Option.Default";
    static final String OPTION_DEFAULT_INT = "io.helidon.builder.api.Option.DefaultInt";
    static final String OPTION_DEFAULT_DOUBLE = "io.helidon.builder.api.Option.DefaultDouble";
    static final String OPTION_DEFAULT_BOOLEAN = "io.helidon.builder.api.Option.DefaultBoolean";
    static final String OPTION_DEFAULT_LONG = "io.helidon.builder.api.Option.DefaultLong";
    static final String OPTION_DEFAULT_METHOD = "io.helidon.builder.api.Option.DefaultMethod";
    static final String OPTION_DEFAULT_CODE = "io.helidon.builder.api.Option.DefaultCode";

    static final TypeName BOXED_BOOLEAN_TYPE = TypeName.create(Boolean.class);
    static final TypeName CONFIGURED_TYPE = TypeName.create(CONFIGURED);
    static final TypeName CONFIGURED_OPTION_TYPE = TypeName.create(CONFIGURED_OPTION);
    static final TypeName CONFIG_TYPE = TypeName.create(CONFIG);
    static final TypeName PROTOTYPE_TYPE = TypeName.create(PROTOTYPE);
    static final TypeName IMPLEMENT_TYPE = TypeName.create(IMPLEMENT);
    static final TypeName RUNTIME_OBJECT_TYPE = TypeName.create(RUNTIME_OBJECT);
    static final TypeName BLUEPRINT_TYPE = TypeName.create(PROTOTYPE_BLUEPRINT);
    static final TypeName PROTOTYPE_CONFIGURED_TYPE = TypeName.create(PROTOTYPE_CONFIGURED);
    static final TypeName PROTOTYPE_FACTORY_TYPE = TypeName.create(PROTOTYPE_FACTORY);
    static final TypeName PROTOTYPE_ANNOTATED_TYPE = TypeName.create(PROTOTYPE_ANNOTATED);
    static final TypeName RUNTIME_PROTOTYPE_TYPE = TypeName.create(RUNTIME_PROTOTYPE);
    static final TypeName CUSTOM_METHODS_TYPE = TypeName.create(CUSTOM_METHODS);
    static final TypeName FACTORY_METHOD_TYPE = TypeName.create(FACTORY_METHOD);
    static final TypeName PROTOTYPE_CUSTOM_METHOD_TYPE = TypeName.create(PROTOTYPE_CUSTOM_METHOD);
    static final TypeName BUILDER_CUSTOM_METHOD_TYPE = TypeName.create(BUILDER_CUSTOM_METHOD);
    static final TypeName DESCRIPTION_TYPE = TypeName.create(DESCRIPTION);
    static final TypeName DEPRECATED_TYPE = TypeName.create(Deprecated.class);
    static final TypeName OPTION_DEPRECATED_TYPE = TypeName.create("io.helidon.builder.api.Option.Deprecated");
    static final TypeName OPTION_SINGULAR_TYPE = TypeName.create(OPTION_SINGULAR);
    static final TypeName OPTION_SAME_GENERIC_TYPE = TypeName.create(OPTION_SAME_GENERIC);
    static final TypeName OPTION_CONFIDENTIAL_TYPE = TypeName.create(OPTION_CONFIDENTIAL);
    static final TypeName OPTION_REDUNDANT_TYPE = TypeName.create(OPTION_REDUNDANT);
    static final TypeName OPTION_CONFIGURED_TYPE = TypeName.create(OPTION_CONFIGURED);
    static final TypeName OPTION_ACCESS_TYPE = TypeName.create(OPTION_ACCESS);
    static final TypeName OPTION_REQUIRED_TYPE = TypeName.create(OPTION_REQUIRED);
    static final TypeName OPTION_PROVIDER_TYPE = TypeName.create(OPTION_PROVIDER);
    static final TypeName OPTION_ALLOWED_VALUES_TYPE = TypeName.create(OPTION_ALLOWED_VALUES);
    static final TypeName OPTION_ALLOWED_VALUE_TYPE = TypeName.create(OPTION_ALLOWED_VALUE);
    static final TypeName OPTION_DEFAULT_TYPE = TypeName.create(OPTION_DEFAULT);
    static final TypeName OPTION_DEFAULT_INT_TYPE = TypeName.create(OPTION_DEFAULT_INT);
    static final TypeName OPTION_DEFAULT_DOUBLE_TYPE = TypeName.create(OPTION_DEFAULT_DOUBLE);
    static final TypeName OPTION_DEFAULT_BOOLEAN_TYPE = TypeName.create(OPTION_DEFAULT_BOOLEAN);
    static final TypeName OPTION_DEFAULT_LONG_TYPE = TypeName.create(OPTION_DEFAULT_LONG);
    static final TypeName OPTION_DEFAULT_METHOD_TYPE = TypeName.create(OPTION_DEFAULT_METHOD);
    static final TypeName OPTION_DEFAULT_CODE_TYPE = TypeName.create(OPTION_DEFAULT_CODE);
    static final TypeName OPTION_TYPE = TypeName.create("io.helidon.builder.api.Option.Type");

    static final TypeName VOID_TYPE = TypeName.create(void.class);
    static final TypeName STRING_TYPE = TypeName.create(String.class);
    static final TypeName DURATION_TYPE = TypeName.create(Duration.class);
    static final TypeName CHAR_ARRAY_TYPE = TypeName.create(char[].class);

    static final TypeName LINKED_HASH_MAP_TYPE = TypeName.create(LinkedHashMap.class);
    static final TypeName LINKED_HASH_SET_TYPE = TypeName.create(LinkedHashSet.class);
    static final TypeName ARRAY_LIST_TYPE = TypeName.create(ArrayList.class);

    static final TypeName CONFIG_CONFIGURED_BUILDER = TypeName.create(
            "io.helidon.common.config.ConfigBuilderSupport.ConfiguredBuilder");
    static final TypeName CONFIG_BUILDER_SUPPORT = TypeName.create("io.helidon.common.config.ConfigBuilderSupport");

    private Types() {
    }
}
