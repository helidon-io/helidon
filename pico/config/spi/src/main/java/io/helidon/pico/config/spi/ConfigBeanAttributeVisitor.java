/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.config.spi;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Provides a functional interface that can be used to access the {@link io.helidon.pico.config.api.ConfigBean}'s
 * attributes.
 */
@FunctionalInterface
public interface ConfigBeanAttributeVisitor<R> {

    /**
     * Each attribute comprising the config bean will be visited.
     *
     * @param attrName          the bean attribute name - these are the getter methods on the config bean itself
     * @param valueSupplier     the value supplier in case the value needs to be accessed
     * @param meta              the meta attributes for this attribute type
     * @param userDefinedCtx    the user define payload - whatever you want it to be, or null if not needed
     * @param type              the attribute type
     * @param typeArgument      the attribute type arguments (i.e., generics on the type)
     */
    void visit(String attrName,
               Supplier<Object> valueSupplier,
               Map<String, Object> meta,
               R userDefinedCtx,
               Class<?> type,
               Class<?>... typeArgument);

}
