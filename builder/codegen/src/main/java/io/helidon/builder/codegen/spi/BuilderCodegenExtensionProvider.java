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

package io.helidon.builder.codegen.spi;

import io.helidon.common.types.TypeName;

/**
 * An extension provider to modify the behavior of the builder code generator.
 */
public interface BuilderCodegenExtensionProvider {
    /**
     * Whether this extension provider supports the given type, that is configured by the user in
     * {@code io.helidon.builder.api.Prototype.Extension#value}.
     *
     * @param type type provided in the extension annotation that an extension may support
     * @return {@code true} if this provider supports the given type, {@code false} otherwise
     */
    boolean supports(TypeName type);

    /**
     * Create an extension for the given type(s), that was checked by {@link #supports(io.helidon.common.types.TypeName)}.
     *
     * @param supportedTypes types supported by this extension - at least one is always present
     * @return an extension to modify the generated code
     */
    BuilderCodegenExtension create(TypeName... supportedTypes);
}
