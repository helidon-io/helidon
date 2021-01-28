/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BiConsumer;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import io.helidon.common.Errors;
import io.helidon.config.Config;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Message;

abstract class AbstractMessagingMethod implements MessagingMethod {

    private String incomingChannelName;
    private String outgoingChannelName;

    private Bean<?> bean;
    private Object beanInstance;
    private MethodSignatureType type;
    private final Method method;
    private final Errors.Collector errors;
    private Acknowledgment.Strategy ackStrategy;
    private BiConsumer<MessagingMethod, Object> afterInvokeCallback;
    private BiConsumer<MessagingMethod, Message<?>> beforeInvokeCallback;
    private FailureCallback onFailureCallback;


    AbstractMessagingMethod(Method method, Errors.Collector errors) {
        this.method = method;
        this.errors = errors;
        Optional<MethodSignatureType> signatureType = MethodSignatureResolver
                .create(method)
                .resolve();
        if (signatureType.isPresent()) {
            this.type = signatureType.get();
            resolveAckStrategy();
        } else {
            errors.fatal("Unsupported method signature " + method);
        }
    }

    void validate() {
        if (type == null) {
            // already failed on unsupported signature
            return;
        }
        Optional.ofNullable(method.getAnnotation(Acknowledgment.class))
                .map(Acknowledgment::value)
                .filter(s -> !type.getSupportedAckStrategies().contains(s))
                .ifPresent(strategy -> {
                    errors.fatal(String.format("Acknowledgment strategy %s is not supported for method signature: %s",
                            strategy, type));
                });
    }

    void init(BeanManager beanManager, Config config) {
        this.beanInstance = ChannelRouter.lookup(bean, beanManager);
    }

    public String getName() {
        return method.getName();
    }

    public Method getMethod() {
        return method;
    }

    Errors.Collector errors() {
        return errors;
    }

    public Object getBeanInstance() {
        return beanInstance;
    }

    void setDeclaringBean(Bean<?> bean) {
        this.bean = bean;
    }

    Class<?> getDeclaringType() {
        return method.getDeclaringClass();
    }

    public String getIncomingChannelName() {
        return incomingChannelName;
    }

    public String getOutgoingChannelName() {
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

    void setType(MethodSignatureType type) {
        this.type = type;
    }

    public Acknowledgment.Strategy getAckStrategy() {
        return ackStrategy;
    }

    @SuppressWarnings("unchecked")
    <T> T invoke(Object... args) {
        try {
            return (T) this.getMethod().invoke(this.getBeanInstance(), args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new DeploymentException(e);
        }
    }

    private void resolveAckStrategy() {
        ackStrategy =
                Optional.ofNullable(method.getAnnotation(Acknowledgment.class))
                        .map(Acknowledgment::value)
                        .orElse(type.getDefaultAckType());
    }


    void beforeInvoke(Object incoming) {
        if (this.beforeInvokeCallback != null) {
            beforeInvokeCallback.accept(this, (Message<?>) incoming);
        }
    }

    void afterInvoke(Object incoming, Object outgoing) {
        if (this.afterInvokeCallback != null) {
            // if possible sneak message context to outgoing message
            if (incoming != outgoing
                    && incoming != null
                    && outgoing instanceof Message
            ) {
                MessageContext.copy((Message<?>) incoming, (Message<?>) outgoing);
            }
            afterInvokeCallback.accept(this, outgoing);
        }
    }

    void onFailure(Message<?> incoming, Throwable e) {
        if (this.onFailureCallback != null) {
            onFailureCallback.accept(this, incoming, e);
        }
    }

    void beforeInvokeCallback(BiConsumer<MessagingMethod, Message<?>> callback) {
        this.beforeInvokeCallback = callback;
    }

    void afterInvokeCallback(BiConsumer<MessagingMethod, Object> callback) {
        this.afterInvokeCallback = callback;
    }

    void failureCallback(FailureCallback failureCallback) {
        this.onFailureCallback = failureCallback;
    }
}
