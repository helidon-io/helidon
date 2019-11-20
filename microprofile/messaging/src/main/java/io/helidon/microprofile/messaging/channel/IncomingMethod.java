/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import io.helidon.config.Config;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Subscriber;

/**
 * Subscriber method with reference to processor method.
 * <p/>Example:
 * <pre>{@code
 *     @Incoming("channel-name")
 *     public void exampleIncomingMethod(String msg) {
 *         ...
 *     }
 * }</pre>
 */
class IncomingMethod extends AbstractMethod {

    private static final Logger LOGGER = Logger.getLogger(IncomingMethod.class.getName());

    private Subscriber subscriber;

    IncomingMethod(AnnotatedMethod method) {
        super(method.getJavaMember());
        super.setIncomingChannelName(method.getAnnotation(Incoming.class).value());
        resolveSignatureType();
    }

    void validate() {
        if (getIncomingChannelName() == null || getIncomingChannelName().trim().isEmpty()) {
            throw new DeploymentException(String
                    .format("Missing channel name in annotation @Incoming on method %s", getMethod().toString()));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        if (getType().isInvokeAtAssembly()) {
            try {
                switch (getType()) {
                    case INCOMING_VOID_2_SUBSCRIBER:
                        subscriber = UnwrapProcessor.of(this.getMethod(), (Subscriber) getMethod()
                                .invoke(getBeanInstance()));
                        break;
                    case INCOMING_VOID_2_SUBSCRIBER_BUILDER:
                        subscriber = UnwrapProcessor.of(this.getMethod(),
                                ((SubscriberBuilder) getMethod().invoke(getBeanInstance())).build());
                        break;
                    default:
                        throw new UnsupportedOperationException(String
                                .format("Not implemented signature %s", getType()));
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } else {
            // Invoke on each message subscriber
            subscriber = new InternalSubscriber(getMethod(), getBeanInstance());
        }
    }

    public Subscriber getSubscriber() {
        return subscriber;
    }

    protected void resolveSignatureType() {
        Class<?> returnType = this.getMethod().getReturnType();
        Class<?> parameterType;
        if (this.getMethod().getParameterTypes().length == 1) {
            parameterType = this.getMethod().getParameterTypes()[0];
        } else if (this.getMethod().getParameterTypes().length == 0) {
            parameterType = Void.TYPE;
        } else {
            throw new DeploymentException(String
                    .format("Unsupported parameters on incoming method %s", getMethod()));
        }

        if (Void.TYPE.equals(parameterType)) {
            if (Subscriber.class.equals(returnType)) {
                setType(MethodSignatureType.INCOMING_VOID_2_SUBSCRIBER);
            } else if (SubscriberBuilder.class.equals(returnType)) {
                setType(MethodSignatureType.INCOMING_VOID_2_SUBSCRIBER_BUILDER);
            }
        } else {
            if (CompletionStage.class.equals(returnType)) {
                setType(MethodSignatureType.INCOMING_MSG_2_COMPLETION_STAGE);
// Uncomment when TCK issue is solved https://github.com/eclipse/microprofile-reactive-messaging/issues/79
// see io.helidon.microprofile.messaging.inner.BadSignaturePublisherPayloadBean
            } else /*if (Void.TYPE.equals(returnType))*/ {
                setType(MethodSignatureType.INCOMING_MSG_2_VOID);
//            } else {
//                throw new DeploymentException("Not supported method signature.");
            }
        }

        if (Objects.isNull(getType())) {
            throw new DeploymentException(String
                    .format("Unsupported incoming method signature %s", getMethod()));
        }
    }

}
