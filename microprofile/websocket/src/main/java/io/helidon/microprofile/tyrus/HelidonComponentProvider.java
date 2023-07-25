/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tyrus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import org.glassfish.tyrus.core.ComponentProvider;

/**
 * A service provider for Tyrus to create and destroy beans using CDI. By default,
 * and according to the Jakarta WebSocket specification, beans are created and
 * destroyed for each client connection (in "connection scope"). However, this provider
 * also supports endpoints in {@link ApplicationScoped}. These endpoint instances
 * are not destroyed here but at a later time by the CDI container. No other scopes
 * are currently supported.
 */
public class HelidonComponentProvider extends ComponentProvider {

    /**
     * Checks if a bean is known to CDI.
     *
     * @param c {@link Class} to be checked
     * @return outcome of test
     */
    @Override
    public boolean isApplicable(Class<?> c) {
        BeanManager beanManager = CDI.current().getBeanManager();
        return beanManager.getBeans(c).size() > 0;
    }

    /**
     * Create a new instance using CDI. Note that if the bean is {@link ApplicationScoped}
     * the same instance will be returned every time this method is called.
     *
     * @param c {@link Class} to be created
     * @return new instance
     * @param <T> type of new instance
     */
    @Override
    public <T> Object create(Class<T> c) {
        return CDI.current().select(c).get();
    }

    /**
     * Beans are normally scoped to a client connection. However, if a bean is explicitly
     * set to be in {@link ApplicationScoped}, it will not be destroyed here.
     *
     * @param o instance to be destroyed
     * @return outcome of operation
     */
    @Override
    public boolean destroy(Object o) {
        try {
            if (!o.getClass().isAnnotationPresent(ApplicationScoped.class)) {
                CDI.current().destroy(o);
            }
        } catch (UnsupportedOperationException | IllegalStateException e) {
            return false;
        }
        return true;
    }
}
