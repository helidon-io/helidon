/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package io.helidon.microprofile.messaging.channel;

import java.lang.reflect.Method;
import java.util.Optional;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import io.helidon.config.Config;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;

abstract class AbstractMethod {

    private String incomingChannelName;
    private String outgoingChannelName;

    private Bean<?> bean;
    private Method method;
    private Object beanInstance;
    private MethodSignatureType type;
    private Acknowledgment.Strategy ackStrategy;


    AbstractMethod(Method method) {
        this.method = method;
        type = MethodSignatureResolver.create(method).resolve();
        resolveAckStrategy();
    }

    void validate() {
        Optional.ofNullable(method.getAnnotation(Acknowledgment.class))
                .map(Acknowledgment::value)
                .filter(s -> !type.getSupportedAckStrategies().contains(s))
                .ifPresent(strategy -> {
                    throw new RuntimeException(
                            String.format("Acknowledgment strategy %s is not supported for method signature: %s",
                                    strategy, type));
                });
    }

    public void init(BeanManager beanManager, Config config) {
        this.beanInstance = ChannelRouter.lookup(bean, beanManager);
    }

    public Method getMethod() {
        return method;
    }

    Object getBeanInstance() {
        return beanInstance;
    }

    void setDeclaringBean(Bean bean) {
        this.bean = bean;
    }

    Class<?> getDeclaringType() {
        return method.getDeclaringClass();
    }

    String getIncomingChannelName() {
        return incomingChannelName;
    }

    String getOutgoingChannelName() {
        return outgoingChannelName;
    }

    void setIncomingChannelName(String incomingChannelName) {
        this.incomingChannelName = incomingChannelName;
    }

    void setOutgoingChannelName(String outgoingChannelName) {
        this.outgoingChannelName = outgoingChannelName;
    }

    public MethodSignatureType getType() {
        return type;
    }

    public void setType(MethodSignatureType type) {
        this.type = type;
    }

    public Acknowledgment.Strategy getAckStrategy() {
        return ackStrategy;
    }

    private void resolveAckStrategy() {
        ackStrategy =
                Optional.ofNullable(method.getAnnotation(Acknowledgment.class))
                        .map(Acknowledgment::value)
                        .orElse(type.getDefaultAckType());
    }
}
