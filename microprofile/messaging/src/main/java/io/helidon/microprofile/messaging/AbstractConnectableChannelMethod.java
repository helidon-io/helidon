/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.messaging;

import io.helidon.config.Config;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import java.lang.reflect.Method;

public abstract class AbstractConnectableChannelMethod {

    protected final String channelName;

    protected Bean<?> bean;
    private ChannelRouter router;
    protected Method method;
    protected Object beanInstance;
    protected BeanManager beanManager;
    protected Config config;

    public AbstractConnectableChannelMethod(String channelName, Method method, ChannelRouter router) {
        this.router = router;
        this.method = method;
        this.channelName = channelName;
        validate();
    }

    abstract void validate();

    protected abstract void connect();

    public void init(BeanManager beanManager, Config config) {
        this.beanInstance = getBeanInstance(bean, beanManager);
        this.beanManager = beanManager;
        this.config = config;
    }

    public void setDeclaringBean(Bean bean) {
        this.bean = bean;
    }

    public Class<?> getDeclaringType() {
        return method.getDeclaringClass();
    }

    public String getChannelName() {
        return channelName;
    }

    protected Object getBeanInstance(Bean<?> bean, BeanManager beanManager) {
        javax.enterprise.context.spi.Context context = beanManager.getContext(bean.getScope());
        Object instance = context.get(bean);
        if (instance == null) {
            CreationalContext creationalContext = beanManager.createCreationalContext(bean);
            instance = beanManager.getReference(bean, bean.getBeanClass(), creationalContext);
        }
        if (instance == null) {
            throw new DeploymentException("Instance of bean " + bean.getName() + " not found");
        }
        return instance;
    }

    public ChannelRouter getRouter() {
        return router;
    }
}
