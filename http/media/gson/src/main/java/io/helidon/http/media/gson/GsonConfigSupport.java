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

package io.helidon.http.media.gson;

import java.util.Map;
import java.util.function.Consumer;

import io.helidon.builder.api.Prototype;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

final class GsonConfigSupport {
    private GsonConfigSupport() {
    }

    static class Decorator implements Prototype.BuilderDecorator<GsonSupportConfig.BuilderBase<?, ?>> {
        private static final Gson GSON = new GsonBuilder().create();
        private static final Map<String, Consumer<GsonBuilder>> BOOLEAN_PROPERTIES;

        static {
            BOOLEAN_PROPERTIES = Map.of("pretty-printing",
                                        GsonBuilder::setPrettyPrinting,
                                        "disable-html-escaping",
                                        GsonBuilder::disableHtmlEscaping,
                                        "disable-inner-class-serialization",
                                        GsonBuilder::disableInnerClassSerialization,
                                        "disable-jdk-unsafe",
                                        GsonBuilder::disableJdkUnsafe,
                                        "enable-complex-map-key-serialization",
                                        GsonBuilder::enableComplexMapKeySerialization,
                                        "exclude-fields-without-expose-annotation",
                                        GsonBuilder::excludeFieldsWithoutExposeAnnotation,
                                        "generate-non-executable-json",
                                        GsonBuilder::generateNonExecutableJson,
                                        "serialize-special-floating-point-values",
                                        GsonBuilder::serializeSpecialFloatingPointValues,
                                        "lenient",
                                        GsonBuilder::setLenient,
                                        "serialize-nulls",
                                        GsonBuilder::serializeNulls);
        }

        @Override
        public void decorate(GsonSupportConfig.BuilderBase<?, ?> target) {
            if (target.gson().isEmpty()) {
                if (target.properties().isEmpty() && target.typeAdapterFactories().isEmpty()) {
                    target.gson(GSON);
                } else {
                    GsonBuilder builder = new GsonBuilder();
                    target.properties()
                            .entrySet()
                            .stream()
                            .filter(Map.Entry::getValue)
                            .filter(entry -> BOOLEAN_PROPERTIES.containsKey(entry.getKey()))
                            .forEach(entry -> BOOLEAN_PROPERTIES.get(entry.getKey()).accept(builder));
                    target.typeAdapterFactories()
                            .forEach(builder::registerTypeAdapterFactory);
                    target.gson(builder.create());
                }
            }
        }
    }
}
