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

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.microprofile.config.MpConfig;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * Subscriber with reference to {@link org.eclipse.microprofile.reactive.messaging.Incoming @Incoming}
 * /{@link org.eclipse.microprofile.reactive.messaging.Outgoing @Outgoing} annotated method
 */
public class IncomingSubscriber implements Subscriber<Message<?>> {
    private Object beanInstance;
    private Bean<?> bean;
    private final Method method;
    private final String channelName;
    private Map<String, Bean<?>> incomingConnectorFactories;
    private BeanManager beanManager;
    private io.helidon.config.Config config;

    public IncomingSubscriber(AnnotatedMethod method, Map<String, Bean<?>> incomingConnectorFactories) {
        this.method = method.getJavaMember();
        this.channelName = method.getAnnotation(Incoming.class).value();
        this.incomingConnectorFactories = incomingConnectorFactories;
    }

    public void subscribe(BeanManager beanManager, io.helidon.config.Config config) {
        this.beanInstance = getBeanInstance(bean, beanManager);
        this.beanManager = beanManager;
        this.config = config;
        io.helidon.config.Config channelConfig = config.get("mp.messaging.incoming").get(channelName);

        String connectorName = channelConfig.get("connector").asString().get();
        ((IncomingConnectorFactory) getBeanInstance(incomingConnectorFactories.get(connectorName), beanManager))
                .getPublisherBuilder(MpConfig.builder().config(channelConfig).build())
                .buildRs()
                .subscribe(this);
    }

    public void setDeclaringBean(Bean bean) {
        this.bean = bean;
    }

    public Class<?> getDeclaringType() {
        return method.getDeclaringClass();
    }

    @Override
    public void onNext(Message<?> message) {
        try {
            Context parentContext = Context.create();
            Context context = Context
                    .builder()
                    .parent(parentContext)
                    .id(parentContext.id() + ":message-" + UUID.randomUUID().toString())
                    .build();
            Contexts.runInContext(context, () -> method.invoke(beanInstance, message));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onError(Throwable t) {
        //TODO: Error propagation
    }

    @Override
    public void onSubscribe(Subscription s) {
    }

    @Override
    public void onComplete() {
    }

    private Object getBeanInstance(Bean<?> bean, BeanManager beanManager) {
        javax.enterprise.context.spi.Context context = beanManager.getContext(bean.getScope());
        Object instance = context.get(bean);
        if (instance == null) {
            CreationalContext creationalContext = beanManager.createCreationalContext(bean);
            return beanManager.getReference(bean, bean.getBeanClass(), creationalContext);
        }
        return instance;
    }

}
