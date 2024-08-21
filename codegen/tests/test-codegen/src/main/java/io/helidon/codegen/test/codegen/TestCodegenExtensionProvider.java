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

package io.helidon.codegen.test.codegen;

import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.codegen.spi.CodegenExtensionProvider;
import io.helidon.common.Weight;
import io.helidon.common.types.TypeName;

public class TestCodegenExtensionProvider implements CodegenExtensionProvider {
    static final TypeName WEIGHT = TypeName.create(Weight.class);

    @Override
    public CodegenExtension create(CodegenContext ctx, TypeName generatorType) {
        return new TestCodegenExtension(ctx);
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(WEIGHT);
    }
}
