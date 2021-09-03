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
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.reactivestreams.Processor;

class ProcessorMethod extends AbstractMessagingMethod {

    private Processor<Object, Object> processor;
    private UniversalChannel outgoingChannel;

    ProcessorMethod(AnnotatedMethod<?> method, Errors.Collector errors) {
        super(method.getJavaMember(), errors);
        super.setIncomingChannelName(method.getAnnotation(Incoming.class).value());
        super.setOutgoingChannelName(method.getAnnotation(Outgoing.class).value());
    }

    @Override
    public void init(BeanManager beanManager, Config config) {
        super.init(beanManager, config);
        if (getType().isInvokeAtAssembly()) {
            processor = new ProxyProcessor(this);
        } else {
            // Create brand new subscriber
            processor = new InternalProcessor(this);
        }
    }

    @Override
    public void validate() {
        super.validate();

        if (getIncomingChannelName() == null || getIncomingChannelName().trim().isEmpty()) {
            super.errors().fatal(String.format("Missing channel name in annotation @Incoming on method %s", getMethod()));
        }
        if (getOutgoingChannelName() == null || getOutgoingChannelName().trim().isEmpty()) {
            super.errors().fatal(String.format("Missing channel name in annotation @Outgoing on method %s", getMethod()));
        }
        if (this.getMethod().getParameterTypes().length > 1) {
            super.errors()
                    .fatal(String
                            .format("Bad processor method signature, wrong number of parameters, only one or none allowed.%s",
                                    getMethod()));
        }
    }

    Processor<Object, Object> getProcessor() {
        return processor;
    }

    UniversalChannel getOutgoingChannel() {
        return outgoingChannel;
    }

    void setOutgoingChannel(UniversalChannel outgoingChannel) {
        this.outgoingChannel = outgoingChannel;
    }

}
