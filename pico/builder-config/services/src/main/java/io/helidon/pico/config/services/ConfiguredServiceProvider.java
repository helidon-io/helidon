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

package io.helidon.pico.config.services;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.pico.config.spi.ConfigBeanAttributeVisitor;
import io.helidon.pico.config.spi.ConfigBeanMapper;
import io.helidon.pico.config.spi.ConfigResolver;
import io.helidon.pico.config.spi.MetaConfigBeanInfo;
import io.helidon.pico.ServiceProvider;

/**
 * An extension to {@link io.helidon.pico.ServiceProvider} that represents a config-driven service.
 *
 * @param <T> the type of this service provider manages
 * @param <CB> the type of config beans that this service is configured by
 */
public interface ConfiguredServiceProvider<T, CB> extends ServiceProvider<T>,
                                                          ConfigBeanMapper<CB> {

    /**
     * @return The service type
     */
    Class<?> getServiceType();

    /**
     * @return The {@link io.helidon.pico.config.api.ConfigBean} type that is used to configure this provider
     */
    Class<?> getConfigBeanType();

    /**
     * @return Meta information about the attributes associated with this service provider's
     * {@link io.helidon.pico.config.api.ConfigBean}.
     */
    MetaConfigBeanInfo<?> getConfigBeanInfo();

    /**
     * Builds a config bean instance using the configuration and resolver provided.
     *
     * @param cfg       the backing configuration
     * @param resolver  the resolver
     * @return the generated config bean instance
     */
    CB toConfigBean(io.helidon.config.Config cfg, ConfigResolver resolver);

    /**
     * @return the mapper appropriate for this service's config beans
     */
    default Function<Config, CB> getMapper() {
        return this::toConfigBean;
    }

    /**
     * @return the supplier that can provide the config bean
     */
    default Supplier<CB> getConfigBeanSupplier() {
        return this::getConfigBean;
    }

    /**
     * @return Meta information about the attributes of the {@link io.helidon.pico.config.api.ConfigBean}. Mostly
     * for internal use. Consider using {@link #visitAttributes(CB, ConfigBeanAttributeVisitor, Object)} instead.
     */
    Map<String, Map<String, Object>> getConfigBeanAttributes();

    /**
     * Visit the attributes of the config bean, calling the visitor for each attribute in the hierarchy.
     *
     * @param configBean            the config bean to visit
     * @param visitor               the visitor
     * @param userDefinedContext    the optional user define context
     * @param <R> the type of the user defined context
     */
    <R> void visitAttributes(CB configBean, ConfigBeanAttributeVisitor visitor, R userDefinedContext);

    /**
     * Gets the internal config bean instance id.
     *
     * @param configBean the config bean
     * @return the config bean instance id
     */
    String getConfigBeanInstanceId(CB configBean);

    /**
     * Returns the config bean associated with this managed service provider.
     *
     * @return the bean associated with this managed service provider
     */
    CB getConfigBean();

}
