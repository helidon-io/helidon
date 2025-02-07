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

package io.helidon.integrations.langchain4j.codegen;

import java.util.Set;

import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.spi.TypeMapper;
import io.helidon.codegen.spi.TypeMapperProvider;
import io.helidon.common.types.TypeName;

import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_TOOL;

/**
 * A {@link java.util.ServiceLoader} provider implementation of a {@link io.helidon.codegen.spi.TypeMapperProvider}
 * to handle Tool annotated types.
 */
public class LcToolsMapperProvider implements TypeMapperProvider {
    /**
     * Public no-arg constructor required by {@link java.util.ServiceLoader}.
     */
    public LcToolsMapperProvider() {
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(LC_TOOL);
    }

    @Override
    public TypeMapper create(CodegenOptions codegenOptions) {
        return new LcToolsMapper();
    }
}
