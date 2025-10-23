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
package io.helidon.data.codegen.common;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.helidon.builder.api.Prototype;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;

// MethodParamsBlueprint custom methods
class MethodParamsSupport {

    private MethodParamsSupport() {
        throw new UnsupportedOperationException("No instances of MethodParamsSupport are allowed");
    }

    /**
     * Whether existing method parameters trigger dynamic query to be generated.
     * Builds new {@link String} from stored property name elements.
     *
     * @return the method parameters
     */
    @Prototype.PrototypeMethod
    static boolean dynamic(MethodParams methodParams) {
        return methodParams.order().isPresent();
    }

    /**
     * Builds {@link Set} of query dynamic parts based on the method parameters.
     *
     * @param methodParams the method parameters
     * @return new immutable {@link Set} of query dynamic parts
     */
    @Prototype.PrototypeMethod
    static Set<PersistenceGenerator.DynamicQueryParts> dynamicParts(MethodParams methodParams) {
        Set<PersistenceGenerator.DynamicQueryParts> dynamicParts = new HashSet<>(PersistenceGenerator.DynamicQueryParts.LENGTH);
        if (methodParams.order().isPresent()) {
            dynamicParts.add(PersistenceGenerator.DynamicQueryParts.ORDER);
        }
        return Collections.unmodifiableSet(dynamicParts);
    }

}
