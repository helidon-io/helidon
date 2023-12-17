/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.maven.plugin;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenContextBase;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenLogger;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenScope;
import io.helidon.codegen.ModuleInfo;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

class MavenCodegenContext extends CodegenContextBase implements CodegenContext {
    private final ModuleInfo module;
    private final MavenFiler filer;

    protected MavenCodegenContext(CodegenOptions options,
                                  MavenFiler filer,
                                  CodegenLogger logger,
                                  CodegenScope scope,
                                  ModuleInfo module) {
        super(options, Set.of(), filer, logger, scope);

        this.module = module;
        this.filer = filer;
    }

    public static MavenCodegenContext create(CodegenOptions options,
                                             CodegenScope scope,
                                             Path generatedSourceDir,
                                             Path outputDirectory,
                                             MavenLogger logger,
                                             ModuleInfo module /* may be null*/) {
        return new MavenCodegenContext(options,
                                       MavenFiler.create(generatedSourceDir, outputDirectory),
                                       logger,
                                       scope,
                                       module);
    }

    @Override
    public Optional<ModuleInfo> module() {
        return Optional.ofNullable(module);
    }

    @Override
    public MavenFiler filer() {
        return filer;
    }

    @Override
    public Optional<TypeInfo> typeInfo(TypeName typeName) {
        throw new CodegenException("Cannot create type info, no scan provided");
    }

    @Override
    public Optional<TypeInfo> typeInfo(TypeName typeName, Predicate<TypedElementInfo> elementPredicate) {
        throw new CodegenException("Cannot create type info, no scan provided");
    }
}
