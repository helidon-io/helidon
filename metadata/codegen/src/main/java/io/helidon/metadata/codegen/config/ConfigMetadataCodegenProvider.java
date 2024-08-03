/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.metadata.codegen.config;

import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.codegen.spi.CodegenExtensionProvider;
import io.helidon.common.types.TypeName;

import static io.helidon.metadata.codegen.config.ConfigMetadataTypes.CONFIGURED;
import static io.helidon.metadata.codegen.config.ConfigMetadataTypes.META_CONFIGURED;
import static io.helidon.metadata.codegen.config.ConfigMetadataTypes.META_OPTION;
import static io.helidon.metadata.codegen.config.ConfigMetadataTypes.META_OPTIONS;

/**
 * A Java {@link java.util.ServiceLoader} service implementation to add config metadata code generation.
 */
public class ConfigMetadataCodegenProvider implements CodegenExtensionProvider {
    /**
     * Public constructor required by {@link java.util.ServiceLoader}.
     */
    public ConfigMetadataCodegenProvider() {
    }

    @Override
    public CodegenExtension create(CodegenContext ctx, TypeName generatorType) {
        return new ConfigMetadataCodegenExtension(ctx);
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(META_CONFIGURED,
                      META_OPTION,
                      META_OPTIONS,
                      CONFIGURED);
    }
}
