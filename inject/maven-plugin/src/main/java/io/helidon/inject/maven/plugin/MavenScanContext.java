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
import java.util.function.Predicate;

import io.helidon.codegen.CodegenLogger;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenScope;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.scan.ScanContext;
import io.helidon.codegen.scan.ScanTypeInfoFactory;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import io.github.classgraph.ScanResult;

class MavenScanContext extends MavenCodegenContext implements ScanContext {
    private final ScanResult scanResult;

    protected MavenScanContext(CodegenOptions options,
                               MavenFiler filer,
                               CodegenLogger logger,
                               CodegenScope scope,
                               ScanResult scanResult,
                               ModuleInfo module) {
        super(options, filer, logger, scope, module);

        this.scanResult = scanResult;
    }

    static MavenScanContext create(CodegenOptions options,
                              ScanResult scanResult,
                              CodegenScope scope,
                              Path generatedSourceDir,
                              Path outputDirectory,
                              CodegenLogger logger,
                              ModuleInfo module /* may be null */) {


        return new MavenScanContext(options,
                                    MavenFiler.create(generatedSourceDir, outputDirectory),
                                    logger,
                                    scope,
                                    scanResult,
                                    module);
    }

    @Override
    public Optional<TypeInfo> typeInfo(TypeName typeName) {
        return ScanTypeInfoFactory.create(this, typeName);
    }

    @Override
    public Optional<TypeInfo> typeInfo(TypeName typeName, Predicate<TypedElementInfo> elementPredicate) {
        return ScanTypeInfoFactory.create(this, typeName, elementPredicate);
    }

    @Override
    public ScanResult scanResult() {
        return scanResult;
    }
}
