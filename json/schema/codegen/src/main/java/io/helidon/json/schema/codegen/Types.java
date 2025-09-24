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

package io.helidon.json.schema.codegen;

import java.math.BigDecimal;
import java.math.BigInteger;

import io.helidon.common.types.TypeName;

final class Types {

    //Common annotations
    static final TypeName JSON_SCHEMA_SCHEMA = TypeName.create("io.helidon.json.schema.JsonSchema.Schema");
    static final TypeName JSON_SCHEMA_ID = TypeName.create("io.helidon.json.schema.JsonSchema.Id");
    static final TypeName JSON_SCHEMA_TITLE = TypeName.create("io.helidon.json.schema.JsonSchema.Title");
    static final TypeName JSON_SCHEMA_DESCRIPTION = TypeName.create("io.helidon.json.schema.JsonSchema.Description");
    static final TypeName JSON_SCHEMA_REQUIRED = TypeName.create("io.helidon.json.schema.JsonSchema.Required");
    static final TypeName JSON_SCHEMA_PROVIDER = TypeName.create("io.helidon.json.schema.spi.JsonSchemaProvider");
    static final TypeName JSON_SCHEMA_DO_NOT_INSPECT = TypeName.create("io.helidon.json.schema.JsonSchema.DoNotInspect");
    static final TypeName JSON_SCHEMA_IGNORE = TypeName.create("io.helidon.json.schema.JsonSchema.Ignore");
    static final TypeName JSON_SCHEMA_PROPERTY_NAME =
            TypeName.create("io.helidon.json.schema.JsonSchema.PropertyName");

    //Integer annotations
    static final TypeName JSON_SCHEMA_INTEGER_MULTIPLE_OF =
            TypeName.create("io.helidon.json.schema.JsonSchema.Integer.MultipleOf");
    static final TypeName JSON_SCHEMA_INTEGER_MINIMUM =
            TypeName.create("io.helidon.json.schema.JsonSchema.Integer.Minimum");
    static final TypeName JSON_SCHEMA_INTEGER_MAXIMUM =
            TypeName.create("io.helidon.json.schema.JsonSchema.Integer.Maximum");
    static final TypeName JSON_SCHEMA_INTEGER_EXCLUSIVE_MAXIMUM =
            TypeName.create("io.helidon.json.schema.JsonSchema.Integer.ExclusiveMaximum");
    static final TypeName JSON_SCHEMA_INTEGER_EXCLUSIVE_MINIMUM =
            TypeName.create("io.helidon.json.schema.JsonSchema.Integer.ExclusiveMinimum");

    //String annotations
    static final TypeName JSON_SCHEMA_STRING_MIN_LENGTH =
            TypeName.create("io.helidon.json.schema.JsonSchema.String.MinLength");
    static final TypeName JSON_SCHEMA_STRING_MAX_LENGTH =
            TypeName.create("io.helidon.json.schema.JsonSchema.String.MaxLength");
    static final TypeName JSON_SCHEMA_STRING_PATTERN =
            TypeName.create("io.helidon.json.schema.JsonSchema.String.Pattern");

    //Object annotations
    static final TypeName JSON_SCHEMA_OBJECT_MIN_PROPERTIES =
            TypeName.create("io.helidon.json.schema.JsonSchema.Object.MinProperties");
    static final TypeName JSON_SCHEMA_OBJECT_MAX_PROPERTIES =
            TypeName.create("io.helidon.json.schema.JsonSchema.Object.MaxProperties");
    static final TypeName JSON_SCHEMA_OBJECT_ADDITIONAL_PROPERTIES =
            TypeName.create("io.helidon.json.schema.JsonSchema.Object.AdditionalProperties");

    //Number annotations
    static final TypeName JSON_SCHEMA_NUMBER_MULTIPLE_OF =
            TypeName.create("io.helidon.json.schema.JsonSchema.Number.MultipleOf");
    static final TypeName JSON_SCHEMA_NUMBER_MINIMUM =
            TypeName.create("io.helidon.json.schema.JsonSchema.Number.Minimum");
    static final TypeName JSON_SCHEMA_NUMBER_MAXIMUM =
            TypeName.create("io.helidon.json.schema.JsonSchema.Number.Maximum");
    static final TypeName JSON_SCHEMA_NUMBER_EXCLUSIVE_MAXIMUM =
            TypeName.create("io.helidon.json.schema.JsonSchema.Number.ExclusiveMaximum");
    static final TypeName JSON_SCHEMA_NUMBER_EXCLUSIVE_MINIMUM =
            TypeName.create("io.helidon.json.schema.JsonSchema.Number.ExclusiveMinimum");

    //Array annotations
    static final TypeName JSON_SCHEMA_ARRAY_MAX_ITEMS =
            TypeName.create("io.helidon.json.schema.JsonSchema.Array.MaxItems");
    static final TypeName JSON_SCHEMA_ARRAY_MIN_ITEMS =
            TypeName.create("io.helidon.json.schema.JsonSchema.Array.MinItems");
    static final TypeName JSON_SCHEMA_ARRAY_UNIQUE_ITEMS =
            TypeName.create("io.helidon.json.schema.JsonSchema.Array.UniqueItems");

    //Schema related types
    static final TypeName SCHEMA = TypeName.create("io.helidon.json.schema.Schema");

    //Random types
    static final TypeName LAZY_VALUE = TypeName.create("io.helidon.common.LazyValue");
    static final TypeName LAZY_VALUE_SCHEMA = TypeName.builder(Types.LAZY_VALUE)
            .addTypeArgument(Types.SCHEMA)
            .build();
    static final TypeName BIG_DECIMAL =  TypeName.create(BigDecimal.class);
    static final TypeName BIG_INTEGER =  TypeName.create(BigInteger.class);
    static final TypeName NUMBER =  TypeName.create(Number.class);
    static final TypeName JSONB_TRANSIENT =  TypeName.create("jakarta.json.bind.annotation.JsonbTransient");
    static final TypeName JSONB_PROPERTY =  TypeName.create("jakarta.json.bind.annotation.JsonbProperty");
    static final TypeName JSONB_CREATOR =  TypeName.create("jakarta.json.bind.annotation.JsonbCreator");

    //Service registry related annotations
    static final TypeName SERVICE_NAMED_BY_TYPE = TypeName.create("io.helidon.service.registry.Service.NamedByType");
    static final TypeName SERVICE_SINGLETON = TypeName.create("io.helidon.service.registry.Service.Singleton");

    private Types() {
    }

}
