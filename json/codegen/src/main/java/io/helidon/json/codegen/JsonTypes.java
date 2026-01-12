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

package io.helidon.json.codegen;

import io.helidon.common.types.TypeName;

final class JsonTypes {

    //Annotations
    static final TypeName JSON_ENTITY = TypeName.create("io.helidon.json.binding.Json.Entity");
    static final TypeName JSON_DESERIALIZER = TypeName.create("io.helidon.json.binding.Json.Deserializer");
    static final TypeName JSON_SERIALIZER = TypeName.create("io.helidon.json.binding.Json.Serializer");
    static final TypeName JSON_CONVERTER = TypeName.create("io.helidon.json.binding.Json.Converter");
    static final TypeName JSON_PROPERTY = TypeName.create("io.helidon.json.binding.Json.Property");
    static final TypeName JSON_IGNORE = TypeName.create("io.helidon.json.binding.Json.Ignore");
    static final TypeName JSON_REQUIRED = TypeName.create("io.helidon.json.binding.Json.Required");
    static final TypeName JSON_CREATOR = TypeName.create("io.helidon.json.binding.Json.Creator");
    static final TypeName JSON_SERIALIZE_NULLS = TypeName.create("io.helidon.json.binding.Json.SerializeNulls");
    static final TypeName JSON_PROPERTY_ORDER = TypeName.create("io.helidon.json.binding.Json.PropertyOrder");
    static final TypeName JSON_BUILDER_INFO = TypeName.create("io.helidon.json.binding.Json.BuilderInfo");
    static final TypeName JSON_FAIL_ON_UNKNOWN = TypeName.create("io.helidon.json.binding.Json.FailOnUnknown");

    //Types
    static final TypeName JSON_DESERIALIZER_TYPE = TypeName.create("io.helidon.json.binding.JsonDeserializer");
    static final TypeName JSON_SERIALIZER_TYPE = TypeName.create("io.helidon.json.binding.JsonSerializer");
    static final TypeName JSON_CONVERTER_TYPE = TypeName.create("io.helidon.json.binding.JsonConverter");
    static final TypeName JSON_BINDING_CONFIGURATOR = TypeName.create("io.helidon.json.binding.JsonBindingConfigurator");
    static final TypeName JSON_BINDING_FACTORY = TypeName.create("io.helidon.json.binding.JsonBindingFactory");
    static final TypeName JSON_SERIALIZERS = TypeName.create("io.helidon.json.binding.Serializers");

    static final TypeName JSON_GENERATOR = TypeName.create("io.helidon.json.JsonGenerator");
    static final TypeName BYTES = TypeName.create("io.helidon.common.buffers.Bytes");
    static final TypeName JSON_PARSER = TypeName.create("io.helidon.json.JsonParser");

    static final TypeName BUILDER_TYPE = TypeName.create("io.helidon.common.Builder");

    static final TypeName SERVICE_REGISTRY_PER_LOOKUP = TypeName.create("io.helidon.service.registry.Service.PerLookup");

    private JsonTypes() {
    }

}
