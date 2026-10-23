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
package io.helidon.codegen.apt;

import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenScope;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.codegen.spi.CodegenExtensionProvider;
import io.helidon.common.types.TypeName;

public class TestHierarchyExtensionProvider implements CodegenExtensionProvider {
    @Override
    public CodegenExtension create(CodegenContext ctx, TypeName generatorType) {
        return round -> {
            if (ctx instanceof Context context) {
                context.callback.accept(round);
            }
        };
    }

    static final class Context extends AptContextImpl {
        private final Consumer<RoundContext> callback;

        Context(ProcessingEnvironment environment, Consumer<RoundContext> callback) {
            this(environment, AptOptions.create(environment), callback);
        }

        private Context(ProcessingEnvironment environment,
                        CodegenOptions options,
                        Consumer<RoundContext> callback) {
            super(environment,
                  options,
                  Set.of(),
                  new AptFiler(environment, options),
                  new AptLogger(environment, options),
                  CodegenScope.PRODUCTION,
                  null);
            this.callback = callback;
        }
    }
}
