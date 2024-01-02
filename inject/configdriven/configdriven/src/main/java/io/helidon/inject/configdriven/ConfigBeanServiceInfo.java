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

package io.helidon.inject.configdriven;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceInfo;

class ConfigBeanServiceInfo implements ServiceInfo {
    private final TypeName beanType;
    private final Set<Qualifier> qualifiers;

    ConfigBeanServiceInfo(TypeName beanType, String name) {
        this.beanType = beanType;
        this.qualifiers = Set.of(Qualifier.createNamed(name));
    }

    @Override
    public TypeName serviceType() {
        return beanType;
    }

    @Override
    public Set<Qualifier> qualifiers() {
        return qualifiers;
    }

    @Override
    public Set<TypeName> scopes() {
        return Set.of(Injection.Singleton.TYPE_NAME);
    }
}
