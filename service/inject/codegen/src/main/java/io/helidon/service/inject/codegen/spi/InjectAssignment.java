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

package io.helidon.service.inject.codegen.spi;

import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.TypeName;

/**
 * Provides customized assignments for injected types.
 * <p>
 * When supporting third party injection frameworks (such as Jakarta Inject - JSR-330), it is quite easy to map annotations
 * to Helidon equivalents, but we also need to support some prescribed types for injection.
 * For example in Jakarta we need to support {@code Provider} type (same as {@link java.util.function.Supplier}, just predates
 * its existence).
 * As we need to assign the correct type to injection points, and it must behave similar to our Service provider, we need
 * to provide source code mapping from {@link java.util.function.Supplier} to the type (always three: plain type,
 * {@link java.util.Optional} type, and a {@link java.util.List} of types).
 */
public interface InjectAssignment {
    /**
     * Map the type to the correct one.
     *
     * @param typeName    type of the processed injection point, may be a generic type such as {@link java.util.List},
     *                    {@link java.util.Optional} (these are the types expected to be supported)
     * @param valueSource code that obtains value from Helidon injection (if this method returns a non-empty optional,
     *                    the provided value will be an {@link java.util.Optional} {@link java.util.function.Supplier},
     *                    {@link java.util.List} of {@link java.util.function.Supplier}, or a {@link java.util.function.Supplier}
     *                    as returned by the {@link Assignment#usedType()};
     *                    other type combinations are not supported
     * @return assignment to use, or an empty assignment if this provider does not understand the type
     */
    Optional<Assignment> assignment(TypeName typeName, String valueSource);

    /**
     * Assignment for code generation. The original intended purpose is to support {@code Provider} from javax and jakarta
     * without a dependency (or need to understand it) in the generator code.
     */
    interface Assignment {
        /**
         * Create a new assignment instance.
         *
         * @param usedType      type to use
         * @param codeGenerator code generator
         * @return a new assignment
         */
        static Assignment create(TypeName usedType, Consumer<ContentBuilder<?>> codeGenerator) {
            return new AssignmentImpl(usedType, codeGenerator);
        }

        /**
         * Type to use as the dependency type using only Helidon supported types
         * (i.e. {@link java.util.function.Supplier} instead of jakarta {@code Provider}).
         *
         * @return Helidon supported type
         */
        TypeName usedType();

        /**
         * Code generator that creates appropriate type required by the target.
         *
         * @return consumer of method content builder
         */
        Consumer<ContentBuilder<?>> codeGenerator();
    }
}
