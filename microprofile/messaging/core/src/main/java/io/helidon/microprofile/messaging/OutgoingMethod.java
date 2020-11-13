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

import java.lang.reflect.InvocationTargetException;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import io.helidon.common.Errors;
import io.helidon.config.Config;

import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.reactivestreams.Publisher;

class OutgoingMethod extends AbstractMessagingMethod {

    private Publisher<?> publisher;

    OutgoingMethod(AnnotatedMethod<?> method, Errors.Collector errors) {
        super(method.getJavaMember(), errors);
        super.setOutgoingChannelName(method.getAnnotation(Outgoing.class).value());
    }

    @Override
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        if (getType().isInvokeAtAssembly()) {
            try {
                switch (getType()) {
                    case OUTGOING_PUBLISHER_MSG_2_VOID:
                        publisher = (Publisher<?>) getMethod().invoke(getBeanInstance());
                        break;
                    case OUTGOING_PUBLISHER_PAYL_2_VOID:
                        publisher = new WrappingPublisher((Publisher<?>) getMethod().invoke(getBeanInstance()));
                        break;
                    case OUTGOING_PUBLISHER_BUILDER_MSG_2_VOID:
                        publisher = ((PublisherBuilder<?>) getMethod().invoke(getBeanInstance()))
                                .buildRs();
                        break;
                    case OUTGOING_PUBLISHER_BUILDER_PAYL_2_VOID:
                        publisher = new WrappingPublisher(((PublisherBuilder<?>) getMethod().invoke(getBeanInstance()))
                                .buildRs());
                        break;
                    default:
                        throw new UnsupportedOperationException(String
                                .format("Not implemented signature %s", getType()));
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new DeploymentException(e);
            }
        } else {
            // Invoke on each request publisher
            publisher = new InternalPublisher(getMethod(), getBeanInstance());
        }
    }

    @Override
    void validate() {
        super.validate();
        if (getOutgoingChannelName() == null || getOutgoingChannelName().trim().isEmpty()) {
            super.errors().fatal(String
                    .format("Missing channel name in annotation @Outgoing, method: %s", getMethod()));
        }
        if (getMethod().getReturnType().equals(Void.TYPE)) {
            super.errors().fatal(String.format("Method annotated as @Outgoing channel cannot have return type void, method: %s",
                    getMethod()));
        }
    }

    Publisher<?> getPublisher() {
        return publisher;
    }

}
