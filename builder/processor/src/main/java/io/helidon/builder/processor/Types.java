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
    static final String PROTOTYPE_SAME_GENERIC = "io.helidon.builder.api.Prototype.SameGeneric";
    static final String BUILDER_INTERCEPTOR = "io.helidon.builder.api.Prototype.BuilderInterceptor";
    static final String IMPLEMENT = "io.helidon.builder.api.Prototype.Implement";
    static final String CONFIDENTIAL = "io.helidon.builder.api.Prototype.Confidential";
    static final String REDUNDANT = "io.helidon.builder.api.Prototype.Redundant";
    static final String FACTORY_METHOD = "io.helidon.builder.api.Prototype.FactoryMethod";
    static final String PROTOTYPE_BUILDER = "io.helidon.builder.api.Prototype.Builder";
    static final String PROTOTYPE_CONFIGURED_BUILDER = "io.helidon.builder.api.Prototype.ConfiguredBuilder";
    static final String PROTOTYPE_BLUEPRINT = "io.helidon.builder.api.Prototype.Blueprint";
    static final String PROTOTYPE_FACTORY = "io.helidon.builder.api.Prototype.Factory";
    static final String PROTOTYPE_ANNOTATED = "io.helidon.builder.api.Prototype.Annotated";
    static final String CUSTOM_METHODS = "io.helidon.builder.api.Prototype.CustomMethods";
    static final String PROTOTYPE_CUSTOM_METHOD = "io.helidon.builder.api.Prototype.PrototypeMethod";
    static final String BUILDER_CUSTOM_METHOD = "io.helidon.builder.api.Prototype.BuilderMethod";
    static final String SINGULAR = "io.helidon.builder.api.Prototype.Singular";

    static final TypeName BOXED_BOOLEAN_TYPE = TypeName.create(Boolean.class);
    static final TypeName CONFIGURED_TYPE = TypeName.create(CONFIGURED);
    static final TypeName CONFIGURED_OPTION_TYPE = TypeName.create(CONFIGURED_OPTION);
    static final TypeName CONFIG_TYPE = TypeName.create(CONFIG);
    static final TypeName SINGULAR_TYPE = TypeName.create(SINGULAR);
    static final TypeName PROTOTYPE_TYPE = TypeName.create(PROTOTYPE);
    static final TypeName PROTOTYPE_SAME_GENERIC_TYPE = TypeName.create(PROTOTYPE_SAME_GENERIC);
    static final TypeName IMPLEMENT_TYPE = TypeName.create(IMPLEMENT);
    static final TypeName CONFIDENTIAL_TYPE = TypeName.create(CONFIDENTIAL);
    static final TypeName REDUNDANT_TYPE = TypeName.create(REDUNDANT);
    static final TypeName RUNTIME_OBJECT_TYPE = TypeName.create(RUNTIME_OBJECT);
    static final TypeName BLUEPRINT_TYPE = TypeName.create(PROTOTYPE_BLUEPRINT);
    static final TypeName PROTOTYPE_FACTORY_TYPE = TypeName.create(PROTOTYPE_FACTORY);
    static final TypeName PROTOTYPE_ANNOTATED_TYPE = TypeName.create(PROTOTYPE_ANNOTATED);
    static final TypeName RUNTIME_PROTOTYPE_TYPE = TypeName.create(RUNTIME_PROTOTYPE);
    static final TypeName CUSTOM_METHODS_TYPE = TypeName.create(CUSTOM_METHODS);
    static final TypeName FACTORY_METHOD_TYPE = TypeName.create(FACTORY_METHOD);
    static final TypeName PROTOTYPE_CUSTOM_METHOD_TYPE = TypeName.create(PROTOTYPE_CUSTOM_METHOD);
    static final TypeName BUILDER_CUSTOM_METHOD_TYPE = TypeName.create(BUILDER_CUSTOM_METHOD);
    static final TypeName VOID_TYPE = TypeName.create(void.class);
    static final TypeName STRING_TYPE = TypeName.create(String.class);
    static final TypeName DURATION_TYPE = TypeName.create(Duration.class);
    static final TypeName CHAR_ARRAY_TYPE = TypeName.create(char[].class);

    static final TypeName LINKED_HASH_MAP_TYPE = TypeName.create(LinkedHashMap.class);
    static final TypeName LINKED_HASH_SET_TYPE = TypeName.create(LinkedHashSet.class);
    static final TypeName ARRAY_LIST_TYPE = TypeName.create(ArrayList.class);

    private Types() {
    }
}
