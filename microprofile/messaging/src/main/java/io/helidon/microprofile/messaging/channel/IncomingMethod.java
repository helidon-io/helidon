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

import io.helidon.config.Config;
import io.helidon.microprofile.messaging.reactive.InternalSubscriber;
import io.helidon.microprofile.messaging.reactive.UnwrapProcessor;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Subscriber;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/**
 * Subscriber with reference to {@link org.eclipse.microprofile.reactive.messaging.Incoming @Incoming}
 * /{@link org.eclipse.microprofile.reactive.messaging.Outgoing @Outgoing} annotated method
 */
public class IncomingMethod extends AbstractChannel {

    private static final Logger LOGGER = Logger.getLogger(IncomingMethod.class.getName());

    private Subscriber subscriber;

    public IncomingMethod(AnnotatedMethod method, ChannelRouter router) {
        super(method.getJavaMember(), router);
        super.incomingChannelName = method.getAnnotation(Incoming.class).value();
        resolveSignatureType();
    }

    void validate() {
        if (incomingChannelName == null || incomingChannelName.trim().isEmpty()) {
            throw new DeploymentException("Missing channel name in annotation @Incoming on method "
                    + method.toString());
        }
    }

    @Override
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        if (type.isInvokeAtAssembly()) {
            // Incoming methods returning custom subscriber
            try {
                switch (type) {
                    case INCOMING_VOID_2_SUBSCRIBER:
                        subscriber = UnwrapProcessor.of(this.method, (Subscriber) method.invoke(beanInstance));
                        break;
                    case INCOMING_VOID_2_SUBSCRIBER_BUILDER:
                        subscriber = UnwrapProcessor.of(this.method, ((SubscriberBuilder) method.invoke(beanInstance)).build());
                        break;
                    default:
                        //TODO: Implement rest of the method signatures supported by spec
                        throw new UnsupportedOperationException("Not implemented yet " + type);
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } else {
            // Create brand new subscriber
            subscriber = new InternalSubscriber(method, beanInstance);
        }
    }

    public Subscriber getSubscriber() {
        return subscriber;
    }

    protected void resolveSignatureType() {
        Class<?> returnType = this.method.getReturnType();
        Class<?> parameterType;
        if (this.method.getParameterTypes().length == 1) {
            parameterType = this.method.getParameterTypes()[0];
        } else if (this.method.getParameterTypes().length == 0) {
            parameterType = Void.TYPE;
        } else {
            throw new DeploymentException("Unsupported parameters on incoming method " + method);
        }

        if (Void.TYPE.equals(parameterType)) {
            if (Subscriber.class.equals(returnType)) {
                this.type = Type.INCOMING_VOID_2_SUBSCRIBER;
            } else if (SubscriberBuilder.class.equals(returnType)) {
                this.type = Type.INCOMING_VOID_2_SUBSCRIBER_BUILDER;
            }
        } else {
            if (CompletionStage.class.equals(returnType)) {
                this.type = Type.INCOMING_MSG_2_COMPLETION_STAGE;
            } else if (Void.TYPE.equals(returnType)) {
                this.type = Type.INCOMING_MSG_2_VOID;
            } else {
                //TODO: Remove when TCK issue is solved https://github.com/eclipse/microprofile-reactive-messaging/issues/79
                // see io.helidon.microprofile.messaging.inner.BadSignaturePublisherPayloadBean
                this.type = Type.INCOMING_MSG_2_VOID;
            }
        }

        if (Objects.isNull(type)) {
            throw new DeploymentException("Unsupported incoming method signature " + method);
        }
    }

}
