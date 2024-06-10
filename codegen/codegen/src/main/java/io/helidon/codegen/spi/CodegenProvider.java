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

package io.helidon.codegen.spi;

import java.util.Set;

import io.helidon.codegen.Option;
import io.helidon.common.types.TypeName;

/**
 * A provider that is capable of processing types.
 * The results of methods defined on this interface can be used to expose this information to the environment,
 * such as annotation mapper.
 *
 * @see io.helidon.codegen.spi.AnnotationMapperProvider
 * @see io.helidon.codegen.spi.ElementMapperProvider
 * @see io.helidon.codegen.spi.TypeMapperProvider
 */
public interface CodegenProvider {
    /**
     * Configuration options that are supported.
     *
     * @return set of configuration options
     */
    default Set<Option<?>> supportedOptions() {
        return Set.of();
    }

    /**
     * Annotations that are supported.
     *
     * @return set of annotation types
     */
    default Set<TypeName> supportedAnnotations() {
        return Set.of();
    }

    /**
     * Supported packages of annotations.
     *
     * @return set of annotation packages
     */
    default Set<String> supportedAnnotationPackages() {
        return Set.of();
    }
}
