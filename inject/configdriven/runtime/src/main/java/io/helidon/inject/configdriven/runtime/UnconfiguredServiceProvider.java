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

package io.helidon.inject.configdriven.runtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.config.Config;
import io.helidon.inject.api.ContextualServiceQuery;
import io.helidon.inject.api.DependenciesInfo;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.Phase;
import io.helidon.inject.api.ServiceInfo;
import io.helidon.inject.api.ServiceProviderBindable;
import io.helidon.inject.configdriven.api.NamedInstance;
import io.helidon.inject.runtime.HelidonInjectionPlan;

/**
 * Used by root service providers when there are no services that have been configured.
 *
 * @param <T>   the service type
 * @param <CB>  the config bean type
 */
class UnconfiguredServiceProvider<T, CB> extends ConfigDrivenServiceProviderBase<T, CB> {
    private final ConfigDrivenServiceProviderBase<T, CB> delegate;

    /**
     * Default Constructor.
     *
     * @param delegate the root delegate
     */
    UnconfiguredServiceProvider(ConfigDrivenServiceProviderBase<T, CB> delegate) {
        super(delegate.instanceId());
        assert (delegate.isRootProvider());
        this.delegate = Objects.requireNonNull(delegate);
        rootProvider(delegate);
        assert (rootProvider().orElseThrow() == delegate);
    }

    @Override
    protected Optional<T> maybeActivate(ContextualServiceQuery query) {
        return Optional.empty();
    }

    @Override
    public ServiceInfo serviceInfo() {
        return delegate.serviceInfo();
    }

    @Override
    public Phase currentActivationPhase() {
        return delegate.currentActivationPhase();
    }

    @Override
    public DependenciesInfo dependencies() {
        return delegate.dependencies();
    }

    @Override
    public Optional<InjectionServices> injectionServices() {
        return delegate.injectionServices();
    }

    @Override
    protected String identitySuffix() {
        return delegate.identitySuffix();
    }

    @Override
    public String name(boolean simple) {
        return delegate.name(simple);
    }

    @Override
    public Optional<ServiceProviderBindable<T>> serviceProviderBindable() {
        return delegate.serviceProviderBindable();
    }

    @Override
    public boolean isCustom() {
        return delegate.isCustom();
    }

    @Override
    public boolean isRootProvider() {
        return false;
    }

    @Override
    public Optional<T> first(ContextualServiceQuery query) {
        // the entire point of this class is to really ensure that we do not resolve injection points!
        return Optional.empty();
    }

    @Override
    public Class<?> serviceType() {
        return delegate.serviceType();
    }

    @Override
    public Map<String, HelidonInjectionPlan> getOrCreateInjectionPlan(boolean resolveIps) {
        return super.getOrCreateInjectionPlan(resolveIps);
    }

    @Override
    public CB configBean() {
        throw new NullPointerException("Config bean is not available on root config driven provider.");
    }

    @Override
    protected boolean drivesActivation() {
        return delegate.drivesActivation();
    }

    @Override
    protected void doPreDestroying(LogEntryAndResult logEntryAndResult) {
        delegate.doPreDestroying(logEntryAndResult);
    }

    @Override
    protected void doDestroying(LogEntryAndResult logEntryAndResult) {
        delegate.doDestroying(logEntryAndResult);
    }

    @Override
    protected void onFinalShutdown() {
        delegate.onFinalShutdown();
    }

    @Override
    public List<NamedInstance<CB>> createConfigBeans(Config config) {
        return List.of();
    }

    @Override
    public Class<CB> configBeanType() {
        return delegate.configBeanType();
    }

    @Override
    protected ConfigDrivenServiceProviderBase<T, CB> createInstance(NamedInstance<CB> configBean) {
        return delegate.createInstance(configBean);
    }
}
