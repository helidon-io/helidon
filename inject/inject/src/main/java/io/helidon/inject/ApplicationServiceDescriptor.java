/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceDescriptor;

/**
 * Support for {@link Application} as a service provider. This activator manages service provider that
 * always returns the same {@link io.helidon.inject.Application} instance.
 * <p>
 * As applications cannot be injected, so the service descriptor does not need to be public.
 */
class ApplicationServiceDescriptor implements ServiceDescriptor<Application> {
    private static final TypeName APP_TYPE = TypeName.create(Application.class);
    private final TypeName appType;
    private final Set<Qualifier> qualifiers;
    private final String appName;

    private ApplicationServiceDescriptor(Class<?> appClass, String appName, Set<Qualifier> qualifiers) {
        this.appType = TypeName.create(appClass);
        this.appName = appName;
        this.qualifiers = qualifiers;
    }

    static io.helidon.inject.ApplicationServiceDescriptor create(Application app, String appName) {
        return new ApplicationServiceDescriptor(app.getClass(), appName, Set.of(Qualifier.createNamed(appName)));
    }

    @Override
    public TypeName serviceType() {
        return appType;
    }

    @Override
    public Set<TypeName> contracts() {
        return Set.of(APP_TYPE);
    }

    @Override
    public Set<Qualifier> qualifiers() {
        return qualifiers;
    }

    @Override
    public TypeName scope() {
        return Injection.Singleton.TYPE_NAME;
    }

    @Override
    public String toString() {
        return "Service descriptor of application \"" + appName + "\"";
    }
}
