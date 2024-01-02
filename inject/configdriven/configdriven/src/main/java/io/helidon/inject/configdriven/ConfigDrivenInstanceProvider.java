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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.inject.ActivationRequest;
import io.helidon.inject.InjectionResolver;
import io.helidon.inject.InjectionServiceProviderException;
import io.helidon.inject.RegistryServiceProvider;
import io.helidon.inject.ServiceProviderBase;
import io.helidon.inject.Services;
import io.helidon.inject.service.InjectionContext;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceDescriptor;

class ConfigDrivenInstanceProvider<T, CB>
        extends ServiceProviderBase<T>
        implements InjectionResolver {

    private final CB beanInstance;
    private final String instanceId;
    private final ConfigDrivenServiceProvider<T, CB> root;
    private final TypeName configBeanType;
    private final Set<Qualifier> qualifiers;

    ConfigDrivenInstanceProvider(Services services,
                                 ServiceDescriptor<T> descriptor,
                                 ConfigDrivenServiceProvider<T, CB> root,
                                 String name,
                                 CB instance) {
        super(services, descriptor);

        this.configBeanType = root.configBeanType();
        this.beanInstance = instance;
        this.instanceId = name;
        this.root = root;
        this.qualifiers = Set.of(Qualifier.createNamed(name));
    }

    @Override
    public Optional<RegistryServiceProvider<?>> rootProvider() {
        return Optional.of(root);
    }

    // note that all responsibilities to resolve is delegated to the root provider
    @Override
    public Optional<Object> resolve(Ip ipInfo,
                                    Services services,
                                    RegistryServiceProvider<?> serviceProvider,
                                    boolean resolveIps) {

        Lookup dep = Lookup.create(ipInfo);
        Lookup criteria = Lookup.builder()
                .addContract(configBeanType)
                .build();
        if (!dep.matchesContracts(criteria)) {
            return Optional.empty();    // we are being injected with neither a config bean nor a service that matches ourselves
        }

        // if we are here then we are asking for a config bean for ourselves, or a managed instance
        if (!dep.qualifiers().isEmpty()) {
            throw new InjectionServiceProviderException("cannot use qualifiers while injecting config beans for self", this);
        }

        return Optional.of(beanInstance);
    }

    @Override
    public String toString() {
        return "Config Driven Instance for: " + serviceInfo().serviceType().fqName() + "{" + instanceId
                + "}[" + currentActivationPhase() + "]";
    }

    @Override
    public Set<Qualifier> qualifiers() {
        return qualifiers;
    }

    @Override
    protected String id(boolean fq) {
        return super.id(fq) + "{" + instanceId + "}";
    }

    @Override
    protected void prepareDependency(Services services, Map<Ip, Supplier<?>> injectionPlan, Ip injectionPoint) {
        // it the type is this bean's type and it does not have any additional qualifier,
        // inject instance

        if (injectionPoint.contract().equals(configBeanType) && injectionPoint.qualifiers().isEmpty()) {
            // we are injecting the config bean that drives this instance
            injectionPlan.put(injectionPoint, () -> beanInstance);
            return;
        }

        super.prepareDependency(services, injectionPlan, injectionPoint);
    }

    @Override
    protected void injectionContext(InjectionContext injectionContext) {
        super.injectionContext(injectionContext);
    }

    CB beanInstance() {
        return beanInstance;
    }

    void activate() {
        super.activate(ActivationRequest.builder()
                               .targetPhase(services().limitRuntimePhase())
                               .build());
    }

}
