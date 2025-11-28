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

package io.helidon.builder.codegen.jackson;

import io.helidon.builder.codegen.spi.BuilderCodegenExtension;
import io.helidon.builder.codegen.spi.BuilderCodegenExtensionProvider;
import io.helidon.common.types.TypeName;

/**
 * Java {@link java.util.ServiceLoader} provider implementation to add Jackson support to the builder code generator.
 */
public class JacksonBuilderExtensionProvider implements BuilderCodegenExtensionProvider {
    /**
     * Constructor required by Java {@link java.util.ServiceLoader}.
     */
    public JacksonBuilderExtensionProvider() {
    }

    @Override
    public boolean supports(TypeName type) {
        return type.equals(JacksonTypes.JSON_SERIALIZE) || type.equals(JacksonTypes.JSON_DESERIALIZE);
    }

    @Override
    public BuilderCodegenExtension create(TypeName... supportedTypes) {
        boolean serialize = false;
        boolean deserialize = false;

        for (TypeName supportedType : supportedTypes) {
            if (supportedType.equals(JacksonTypes.JSON_SERIALIZE)) {
                serialize = true;
            } else if (supportedType.equals(JacksonTypes.JSON_DESERIALIZE)) {
                deserialize = true;
            } else {
                throw new IllegalArgumentException("Unsupported type in Jackson extension: " + supportedType);
            }
        }

        return new JacksonBuilderExtension(serialize, deserialize);
    }
}
