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

package io.helidon.builder.codegen;

import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;

/**
 * Some static methods on custom methods (and deprecated option on the blueprint itself)
 * may be annotated with {@code Prototype.FactoryMethod}.
 * <p>
 * Such methods can be used to map from configuration to a type, or from a prototype to a
 * third party runtime-type.
 */
@Prototype.Blueprint(detach = true)
interface FactoryMethodBlueprint {
    /**
     * Type declaring the factory method.
     *
     * @return type declaring the factory method
     */
    TypeName declaringType();

    /**
     * Return type of the factory method.
     *
     * @return return type of the factory method
     */
    TypeName returnType();

    /**
     * Name of the factory method.
     *
     * @return factory method name
     */
    String methodName();

    /**
     * A parameter of the factory method, if any.
     *
     * @return parameter type, if any
     */
    Optional<TypeName> parameterType();

    /**
     * A factory method may be bound to a specific option.
     *
     * @return name of the option this factory method is bound to, if any
     */
    Optional<String> optionName();
}
