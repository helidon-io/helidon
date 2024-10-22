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

package io.helidon.codegen.apt;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.Option;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

/**
 * Annotation processing code generation context.
 * @deprecated this API will be package local in the future, use through Helidon codegen only
 */
@Deprecated(forRemoval = true, since = "4.1.0")
public interface AptContext extends CodegenContext {
    /**
     * Create context from the processing environment, and a set of additional supported options.
     *
     * @param env processing environment
     * @param options supported options
     * @return a new annotation processing context
     */
    static AptContext create(ProcessingEnvironment env, Set<Option<?>> options) {
        return AptContextImpl.create(env, options);
    }

    /**
     * Annotation processing environment.
     *
     * @return environment
     */
    ProcessingEnvironment aptEnv();

    /**
     * Get a cached instance of the type info, and if not cached, cache the provided one.
     * Only type infos known not to be modified during this build are cached.
     *
     * @param typeName type name
     * @param typeInfoSupplier supplier of value if it is not yet cached
     * @return type info for that name, in case the type info cannot be created, an empty optional
     */
    Optional<TypeInfo> cache(TypeName typeName, Supplier<Optional<TypeInfo>> typeInfoSupplier);
}
