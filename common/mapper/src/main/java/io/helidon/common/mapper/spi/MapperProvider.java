/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.common.mapper.spi;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.Mapper;

/**
 * Java Service loader service to get mappers.
 * <p>
 * Mapper provider provides mappers based on the source and target types and a qualifier.
 * Generic mappers should always return {@link io.helidon.common.mapper.spi.MapperProvider.Support#COMPATIBLE}, so specific mappers
 * can be created for qualified usages. This is to support a different date/time mapper depending on usage. For this
 * case we may have the following qualifiers (example, not normative): {@code config,jdbc-oracle,http-header,http-query}.
 * Qualifiers should be defined by a constant in each component using mapping.
 */
@FunctionalInterface
public interface MapperProvider {
    /**
     * Find a mapper that is capable of mapping from source to target classes.
     * Qualifiers are defined by each component using mapping. In case of clashing qualifiers, the first mapper
     * that returns {@link io.helidon.common.mapper.spi.MapperProvider.Support#SUPPORTED} will be chosen.
     *
     * @param sourceClass class of the source
     * @param targetClass class of the target
     * @param qualifier   qualifiers of this mapping (such as {@code config} or {@code http-headers}, may be empty for default
     * @return a mapper that is capable of mapping (or converting) sources to targets
     */
    ProviderResponse mapper(Class<?> sourceClass, Class<?> targetClass, String qualifier);

    /**
     * Find a mapper that is capable of mapping from source to target types.
     * This method supports mapping to/from types that contain generics.
     *
     * @param sourceType generic type of the source
     * @param targetType generic type of the target
     * @param qualifier qualifier of the mapping - this is to allow multiple mappings for the same type depending on context
     *                  such as HTTP Headers may use a different date mapping than database operations
     * @return a mapper that is capable of mapping (or converting) sources to targets, default implementation
     *      calls {@link #mapper(Class, Class, java.lang.String)} for types that are not generic,
     *      {@link io.helidon.common.mapper.spi.MapperProvider.ProviderResponse#unsupported()} otherwise.
     */
    default ProviderResponse mapper(GenericType<?> sourceType, GenericType<?> targetType, String qualifier) {
        if (sourceType.isClass() && targetType.isClass()) {
            ProviderResponse resp = mapper(sourceType.rawType(), targetType.rawType(), qualifier);
            if (resp.support() == Support.SUPPORTED) {
                return new ProviderResponse(Support.COMPATIBLE, resp.mapper());
            }
        }
        return ProviderResponse.unsupported();
    }

    /**
     * How does this provider support the type.
     */
    enum Support {
        /**
         * Correct type(s) and expected qualifier.
         */
        SUPPORTED,
        /**
         * Correct type(s), unexpected qualifier.
         */
        COMPATIBLE,
        /**
         * Incorrect type(s).
         */
        UNSUPPORTED
    }

    /**
     * Response of a provider.
     *
     * @param support how is this supported
     * @param mapper mapper to map the type, or null if support is {@link Support#UNSUPPORTED}
     */
    record ProviderResponse(Support support, Mapper<?, ?> mapper) {
        private static final ProviderResponse UNSUPPORTED = new ProviderResponse(Support.UNSUPPORTED, null);

        /**
         * Unsupported provider response.
         * @return constant - unsupported response
         */
        public static ProviderResponse unsupported() {
            return UNSUPPORTED;
        }
    }
}
