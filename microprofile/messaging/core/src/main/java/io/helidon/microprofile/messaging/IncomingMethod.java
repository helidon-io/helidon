/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;

import io.helidon.common.Errors;
import io.helidon.config.Config;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Subscriber;

/**
 * Subscriber method with reference to processor method.
 * <p>Example:
 * <pre>{@code
 *     @Incoming("channel-name")
 *     public void exampleIncomingMethod(String msg) {
 *         ...
 *     }
 * }</pre>
 */
class IncomingMethod extends AbstractMessagingMethod {

    private Subscriber<? super Object> subscriber;

    IncomingMethod(AnnotatedMethod<?> method, Errors.Collector errors) {
        super(method.getJavaMember(), errors);
        super.setIncomingChannelName(method.getAnnotation(Incoming.class).value());
    }

    @Override
    void validate() {
        super.validate();
        if (getIncomingChannelName() == null || getIncomingChannelName().trim().isEmpty()) {
            super.errors().fatal(String.format("Missing channel name in annotation @Incoming on method %s",
                    getMethod().toString()));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        if (getType().isInvokeAtAssembly()) {
            switch (getType()) {
                case INCOMING_SUBSCRIBER_MSG_2_VOID:
                case INCOMING_SUBSCRIBER_PAYL_2_VOID:
                    Subscriber<? super Object> originalPaylSubscriber = invoke();
                    Subscriber<? super Object> unwrappedSubscriber =
                            UnwrapProcessor.of(this.getMethod(), originalPaylSubscriber);
                    subscriber = new ProxySubscriber<>(this, unwrappedSubscriber);
                    break;
                case INCOMING_SUBSCRIBER_BUILDER_MSG_2_VOID:
                case INCOMING_SUBSCRIBER_BUILDER_PAYL_2_VOID:
                    SubscriberBuilder<? super Object, ?> originalSubscriberBuilder = invoke();
                    Subscriber<? super Object> unwrappedBuilder =
                            UnwrapProcessor.of(this.getMethod(), originalSubscriberBuilder.build());
                    subscriber = new ProxySubscriber<>(this, unwrappedBuilder);
                    break;
                default:
                    throw new UnsupportedOperationException(String
                            .format("Not implemented signature %s", getType()));
            }

        } else {
            // Invoke on each message subscriber
            subscriber = new InternalSubscriber(this);
        }
    }

    Subscriber<? super Object> getSubscriber() {
        return subscriber;
    }

}
