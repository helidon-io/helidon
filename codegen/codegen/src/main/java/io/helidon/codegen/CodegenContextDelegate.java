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

package io.helidon.codegen;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.spi.AnnotationMapper;
import io.helidon.codegen.spi.ElementMapper;
import io.helidon.codegen.spi.TypeMapper;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Base of codegen context implementation that delegates common parts of the API to an existing instance.
 */
public abstract class CodegenContextDelegate implements CodegenContext {
    private final CodegenContext delegate;

    /**
     * Create a new instance delegating all calls to the delegate.
     *
     * @param delegate to use for all methods
     */
    protected CodegenContextDelegate(CodegenContext delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<ModuleInfo> module() {
        return delegate.module();
    }

    @Override
    public CodegenFiler filer() {
        return delegate.filer();
    }

    @Override
    public CodegenLogger logger() {
        return delegate.logger();
    }

    @Override
    public CodegenScope scope() {
        return delegate.scope();
    }

    @Override
    public CodegenOptions options() {
        return delegate.options();
    }

    @Override
    public Optional<TypeInfo> typeInfo(TypeName typeName) {
        return delegate.typeInfo(typeName);
    }

    @Override
    public Optional<TypeInfo> typeInfo(TypeName typeName, Predicate<TypedElementInfo> elementPredicate) {
        return delegate.typeInfo(typeName, elementPredicate);
    }

    @Override
    public List<ElementMapper> elementMappers() {
        return delegate.elementMappers();
    }

    @Override
    public List<TypeMapper> typeMappers() {
        return delegate.typeMappers();
    }

    @Override
    public List<AnnotationMapper> annotationMappers() {
        return delegate.annotationMappers();
    }

    @Override
    public Set<TypeName> mapperSupportedAnnotations() {
        return delegate.mapperSupportedAnnotations();
    }

    @Override
    public Set<String> mapperSupportedAnnotationPackages() {
        return delegate.mapperSupportedAnnotationPackages();
    }

    @Override
    public Set<Option<?>> supportedOptions() {
        return delegate.supportedOptions();
    }

    @Override
    public String uniqueName(TypeInfo type, TypedElementInfo element) {
        return delegate.uniqueName(type, element);
    }
}
