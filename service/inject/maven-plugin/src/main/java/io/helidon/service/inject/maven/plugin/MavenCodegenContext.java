/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.maven.plugin;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenContextBase;
import io.helidon.codegen.CodegenLogger;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenScope;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.scan.ScanContext;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import io.github.classgraph.ScanResult;

class MavenCodegenContext extends CodegenContextBase implements CodegenContext, ScanContext {
    private final ModuleInfo module;
    private final MavenFiler filer;
    private final ScanResult scanResult;

    protected MavenCodegenContext(CodegenOptions options,
                                  MavenFiler filer,
                                  CodegenLogger logger,
                                  CodegenScope scope,
                                  ScanResult scanResult,
                                  ModuleInfo module) {
        super(options, Set.of(), filer, logger, scope);

        this.module = module;
        this.filer = filer;
        this.scanResult = scanResult;
    }

    static MavenCodegenContext create(CodegenOptions options,
                                      ScanResult scanResult,
                                      CodegenScope scope,
                                      Path generatedSourceDir,
                                      Path outputDirectory,
                                      MavenLogger logger,
                                      ModuleInfo module /* may be null*/) {
        return new MavenCodegenContext(options,
                                       MavenFiler.create(generatedSourceDir, outputDirectory),
                                       logger,
                                       scope,
                                       scanResult,
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
        return Optional.empty();
    }

    @Override
    public Optional<TypeInfo> typeInfo(TypeName typeName, Predicate<TypedElementInfo> elementPredicate) {
        return Optional.empty();
    }

    @Override
    public ScanResult scanResult() {
        return scanResult;
    }
}
