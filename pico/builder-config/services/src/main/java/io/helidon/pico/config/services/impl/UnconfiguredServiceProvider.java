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

package io.helidon.pico.config.services.impl;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.pico.config.services.AbstractConfiguredServiceProvider;
import io.helidon.pico.config.spi.ConfigBeanAttributeVisitor;
import io.helidon.pico.config.spi.ConfigResolver;
import io.helidon.pico.ActivationPhase;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.spi.ext.Dependencies;
import io.helidon.pico.spi.ext.InjectionPlan;

/**
 * Used by root service providers when there are no services that have been configured.
 *
 * @param <T>   the service type
 * @param <CB>  the config bean type
 */
public class UnconfiguredServiceProvider<T, CB> extends AbstractConfiguredServiceProvider<T, CB> {
    private final AbstractConfiguredServiceProvider<T, CB> delegate;

    /**
     * Ctor.
     *
     * @param delegate the root delegate
     */
    public UnconfiguredServiceProvider(AbstractConfiguredServiceProvider<T, CB> delegate) {
        assert (Objects.nonNull(delegate) && delegate.isRootProvider());
        this.delegate = Objects.requireNonNull(delegate);
        rootProvider(delegate);
        assert (rootProvider() == delegate);
    }

    @Override
    protected T maybeActivate(InjectionPointInfo ipInfoCtx, boolean expected) {
        return null;
    }

    @Override
    public DefaultServiceInfo serviceInfo() {
        return delegate.serviceInfo();
    }

    @Override
    public ActivationPhase currentActivationPhase() {
        return delegate.currentActivationPhase();
    }

    @Override
    public Dependencies dependencies() {
        return delegate.dependencies();
    }

    @Override
    public PicoServices picoServices() {
        return delegate.picoServices();
    }

    @Override
    protected String identitySuffix() {
        return "{" + delegate.identity() + "}";
    }

    @Override
    public ServiceProviderBindable serviceProviderBindable() {
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
    public T get(InjectionPointInfo ipInfoCtx, ServiceInfo criteria, boolean expected) {
        // the entire point of this class is to really ensure that we do not resolve injection points!
        return null;
    }

    @Override
    public Optional<Config> getRawConfig() {
        return delegate.getRawConfig();
    }

    @Override
    public Class<?> getServiceType() {
        return delegate.getServiceType();
    }

    @Override
    public Map<String, InjectionPlan<Object>> getOrCreateInjectionPlan(boolean resolveIps) {
        return super.getOrCreateInjectionPlan(resolveIps);
    }

    @Override
    public CB toConfigBean(Config cfg, ConfigResolver resolver) {
        return delegate.toConfigBean(cfg, resolver);
    }

    @Override
    public <R> void visitAttributes(CB configBean, ConfigBeanAttributeVisitor visitor, R userDefinedContext) {
        delegate.visitAttributes(configBean, visitor, userDefinedContext);
    }

    @Override
    public void resolveFrom(Config config, ConfigResolver resolver) {
        delegate.resolveFrom(config, resolver);
    }

    @Override
    public String getConfigBeanInstanceId(CB configBean) {
        return delegate.getConfigBeanInstanceId(configBean);
    }

    @Override
    public CB getConfigBean() {
        return null;
    }

    @Override
    public void setConfigBeanInstanceId(CB configBean, String val) {
        delegate.setConfigBeanInstanceId(configBean, val);
    }

    @Override
    protected AbstractConfiguredServiceProvider<T, CB> createInstance(Object configBean) {
        throw new UnsupportedOperationException();
    }
}
