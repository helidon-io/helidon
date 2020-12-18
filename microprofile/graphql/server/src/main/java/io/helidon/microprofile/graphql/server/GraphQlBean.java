/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.enterprise.context.control.ActivateRequestContext;
import javax.enterprise.inject.spi.CDI;

class GraphQlBean {
    @ActivateRequestContext
    Object runGraphQl(Class<?> clazz, Method method, Object[] arguments)
            throws InvocationTargetException, IllegalAccessException {
        Object instance = CDI.current().select(clazz).get();
        return method.invoke(instance, arguments);
    }
}
