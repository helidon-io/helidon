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

package io.helidon.json.binding;

import io.helidon.builder.api.Prototype;

class JsonBindingConfigCustomMethods {

    private JsonBindingConfigCustomMethods() {
    }

    /**
     * Add type converter.
     *
     * @param builder json binding configuration builder
     * @param converter registered converter
     */
    @Prototype.BuilderMethod
    static void addConverter(JsonBindingConfig.BuilderBase<?, ?> builder, JsonConverter<?> converter) {
        builder.addSerializer(converter)
                .addDeserializer(converter);
    }

}
