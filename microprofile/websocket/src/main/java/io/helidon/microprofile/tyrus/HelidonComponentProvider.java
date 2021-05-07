/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import org.glassfish.tyrus.core.ComponentProvider;

/**
 * Class HelidonComponentProvider. A service provider for Tyrus to create and destroy
 * beans using CDI.
 */
public class HelidonComponentProvider extends ComponentProvider {

    @Override
    public boolean isApplicable(Class<?> c) {
        BeanManager beanManager = CDI.current().getBeanManager();
        return beanManager.getBeans(c).size() > 0;
    }

    @Override
    public <T> Object create(Class<T> c) {
        return CDI.current().select(c).get();
    }

    @Override
    public boolean destroy(Object o) {
        try {
            CDI.current().destroy(o);
        } catch (UnsupportedOperationException | IllegalStateException e) {
            return false;
        }
        return true;
    }
}
