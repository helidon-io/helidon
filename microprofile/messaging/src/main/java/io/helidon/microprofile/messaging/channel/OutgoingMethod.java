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
import io.helidon.microprofile.messaging.reactive.InternalPublisher;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.reactivestreams.Publisher;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;

public class OutgoingMethod extends AbstractChannel {

    private static final Logger LOGGER = Logger.getLogger(OutgoingMethod.class.getName());

    private Publisher publisher;

    public OutgoingMethod(AnnotatedMethod method, ChannelRouter router) {
        super(method.getJavaMember(), router);
        super.outgoingChannelName = method.getAnnotation(Outgoing.class).value();
    }

    @Override
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        // TODO: Rewrite with enum
        try {
            Class<?> returnType = method.getReturnType();
            if (returnType.equals(Publisher.class)) {
                publisher = (Publisher) method.invoke(beanInstance);
            } else if (returnType.equals(PublisherBuilder.class)) {
                publisher = ((PublisherBuilder) method.invoke(beanInstance)).buildRs();
            } else {
                publisher = new InternalPublisher(method, beanInstance);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public void validate() {
        if (outgoingChannelName == null || outgoingChannelName.trim().isEmpty()) {
            throw new DeploymentException("Missing channel name in annotation @Outgoing, method: "
                    + method.toString());
        }
        if (method.getReturnType().equals(Void.TYPE)) {
            throw new DeploymentException("Method annotated as @Outgoing channel cannot have return type void, method: "
                    + method.toString());
        }
    }

    public Publisher getPublisher() {
        return publisher;
    }
}
