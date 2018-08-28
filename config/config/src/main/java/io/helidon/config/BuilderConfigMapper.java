/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import java.lang.invoke.MethodHandle;
import java.util.Collection;

/**
 * Generic {@link ConfigMapper} implementation supporting the builder pattern.
 * <p>
 * Implementations must provide both the bean class {@code T} and the builder
 * class {@code T_Builder}, as outlined below:
 * <pre>{@code
 * public class T {
 *     //getters
 *
 *     public static T_Builder builder() {
 *         return new T_Builder();
 *     }
 * }
 * public class T_Builder {
 *     //setters
 *
 *     public T build() {
 *         new T(...);
 *     }
 * }
 * }</pre>
 * Class {@code T} must contain the public static method {@code builder()} that returns an instance of the corresponding Builder
 * class. The Config system deserializes properties into the Builder instance
 * (calling setters or setting fields; see {@link GenericConfigMapper}). The
 * Builder class must expose the public method {@code build()} that returns an
 * initialize instance of {@code T}.
 *
 * @param <T> type of target Java bean
 * @see ConfigMapperManager
 */
class BuilderConfigMapper<T> implements ConfigMapper<T> {

    private final Class<T> type;
    private final BuilderAccessor<T> builderAccessor;

    BuilderConfigMapper(Class<T> type, BuilderAccessor<T> builderAccessor) {
        this.type = type;
        this.builderAccessor = builderAccessor;
    }

    @Override
    public T apply(Config config) throws ConfigMappingException, MissingValueException {
        return builderAccessor.create(config);
    }

    /**
     * The class covers work with {@code T} builder.
     *
     * @param <T> type of target java bean
     */
    static class BuilderAccessor<T> {
        private final Class<?> builderType;
        private final MethodHandle builderHandler;
        private final Class<T> buildType;
        private final MethodHandle buildHandler;
        private final Collection<GenericConfigMapper.PropertyAccessor> builderAccessors;

        BuilderAccessor(ConfigMapperManager mapperManager,
                        Class<?> builderType,
                        MethodHandle builderHandler,
                        Class<T> buildType,
                        MethodHandle buildHandler) {
            this.builderType = builderType;
            this.builderHandler = builderHandler;
            this.buildType = buildType;
            this.buildHandler = buildHandler;

            builderAccessors = GenericConfigMapperUtils.getBeanProperties(mapperManager, builderType);
        }

        public T create(Config config) {
            try {
                Object builder = builderType.cast(builderHandler.invoke());

                for (GenericConfigMapper.PropertyAccessor builderAccessor : builderAccessors) {
                    builderAccessor.set(builder, config.get(builderAccessor.getName()));
                }

                return buildType.cast(buildHandler.invoke(builder));
            } catch (ConfigMappingException ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new ConfigMappingException(
                        config.key(),
                        buildType,
                        "Builder java bean initialization has failed with an exception.",
                        ex);
            }
        }
    }

}
