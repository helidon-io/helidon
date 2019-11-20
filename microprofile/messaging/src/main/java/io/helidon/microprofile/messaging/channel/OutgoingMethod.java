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

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import io.helidon.config.Config;

import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.reactivestreams.Publisher;

class OutgoingMethod extends AbstractMethod {

    private Publisher publisher;

    OutgoingMethod(AnnotatedMethod method) {
        super(method.getJavaMember());
        super.setOutgoingChannelName(method.getAnnotation(Outgoing.class).value());
        resolveSignatureType();
    }

    @Override
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        if (getType().isInvokeAtAssembly()) {
            try {
                switch (getType()) {
                    case OUTGOING_VOID_2_PUBLISHER:
                        publisher = (Publisher) getMethod().invoke(getBeanInstance());
                        break;
                    case OUTGOING_VOID_2_PUBLISHER_BUILDER:
                        publisher = ((PublisherBuilder) getMethod().invoke(getBeanInstance())).buildRs();
                        break;
                    default:
                        throw new UnsupportedOperationException(String
                                .format("Not implemented signature %s", getType()));
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } else {
            // Invoke on each request publisher
            publisher = new InternalPublisher(getMethod(), getBeanInstance());
        }
    }

    void validate() {
        if (getOutgoingChannelName() == null || getOutgoingChannelName().trim().isEmpty()) {
            throw new DeploymentException(String
                    .format("Missing channel name in annotation @Outgoing, method: %s", getMethod()));
        }
        if (getMethod().getReturnType().equals(Void.TYPE)) {
            throw new DeploymentException(String
                    .format("Method annotated as @Outgoing channel cannot have return type void, method: %s", getMethod()));
        }
    }

    public Publisher getPublisher() {
        return publisher;
    }

    private void resolveSignatureType() {
        Class<?> returnType = this.getMethod().getReturnType();
        if (this.getMethod().getParameterTypes().length != 0) {
            throw new DeploymentException(String
                    .format("Unsupported parameters on outgoing method %s", getMethod()));
        }

        if (Void.class.isAssignableFrom(returnType)) {
            setType(null);
        } else if (Publisher.class.isAssignableFrom(returnType)) {
            setType(MethodSignatureType.OUTGOING_VOID_2_PUBLISHER);
        } else if (PublisherBuilder.class.isAssignableFrom(returnType)) {
            setType(MethodSignatureType.OUTGOING_VOID_2_PUBLISHER_BUILDER);
        } else if (CompletionStage.class.isAssignableFrom(returnType)) {
            setType(MethodSignatureType.OUTGOING_VOID_2_COMPLETION_STAGE);
        } else {
            setType(MethodSignatureType.OUTGOING_VOID_2_MSG);
        }

        if (Objects.isNull(getType())) {
            throw new DeploymentException(String
                    .format("Unsupported outgoing method signature %s", getMethod()));
        }
    }

}
