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
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Publisher;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public abstract class AbstractChannel {

    protected String incomingChannelName;
    protected String outgoingChannelName;

    protected Bean<?> bean;
    private ChannelRouter router;
    protected Method method;
    protected Object beanInstance;
    protected BeanManager beanManager;
    protected Config config;
    protected Type type;
    public boolean connected = false;



    public AbstractChannel(String incomingChannelName, String outgoingChannelName, Method method, ChannelRouter router) {
        this.incomingChannelName = incomingChannelName;
        this.outgoingChannelName = outgoingChannelName;
        this.router = router;
        this.method = method;
    }

    abstract void validate();

    protected abstract void connect();

    public void init(BeanManager beanManager, Config config) {
        this.beanInstance = getBeanInstance(bean, beanManager);
        this.beanManager = beanManager;
        this.config = config;
    }

    public Method getMethod() {
        return method;
    }

    public Object getBeanInstance() {
        return beanInstance;
    }

    public void setDeclaringBean(Bean bean) {
        this.bean = bean;
    }

    public Class<?> getDeclaringType() {
        return method.getDeclaringClass();
    }

    public String getIncomingChannelName() {
        return incomingChannelName;
    }

    public String getOutgoingChannelName() {
        return outgoingChannelName;
    }

    protected PublisherBuilder getPublisherBuilder() {
        try {
            Object returnInstance = method.invoke(beanInstance);
            if (returnInstance instanceof Publisher) {
                // Called once at assembly time.
                return ReactiveStreams.fromPublisher((Publisher) returnInstance);
            } else if (returnInstance instanceof PublisherBuilder) {
                // Called once at assembly time.
                return (PublisherBuilder) returnInstance;
            } else if (returnInstance instanceof Message) {
                //TODO: Supported method signatures in the spec - Message !!!
                // Called for each request made by the subscriber
                throw new UnsupportedOperationException("Not implemented yet!!");
            } else {
                //TODO: Supported method signatures in the spec - Any type
                // Called for each request made by the subscriber
                throw new UnsupportedOperationException("Not implemented yet!!");
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    protected Publisher getPublisher() {
        try {
            Object returnInstance = method.invoke(beanInstance);
            if (returnInstance instanceof Publisher) {
                // Called once at assembly time.
                return (Publisher) returnInstance;
            } else if (returnInstance instanceof PublisherBuilder) {
                // Called once at assembly time.
                return ((PublisherBuilder) returnInstance).buildRs();
            } else if (returnInstance instanceof Message) {
                //TODO: Supported method signatures in the spec - Message !!!
                // Called for each request made by the subscriber
                throw new UnsupportedOperationException("Not implemented yet!!");
            } else {
                //TODO: Supported method signatures in the spec - Any type
                // Called for each request made by the subscriber
                throw new UnsupportedOperationException("Not implemented yet!!");
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    protected Object getBeanInstance(Bean<?> bean, BeanManager beanManager) {
        javax.enterprise.context.spi.Context context = beanManager.getContext(bean.getScope());
        Object instance = context.get(bean);
        if (instance == null) {
            CreationalContext creationalContext = beanManager.createCreationalContext(bean);
            instance = beanManager.getReference(bean, bean.getBeanClass(), creationalContext);
        }
        if (instance == null) {
            throw new DeploymentException("Instance of bean " + bean.getName() + " not found");
        }
        return instance;
    }

    public ChannelRouter getRouter() {
        return router;
    }

    public enum Type {
        /**
         * Invoke at: assembly time
         * <pre>Processor&lt;Message&lt;I>, Message&lt;O>> method();</pre>
         * <pre>Processor&lt;I, O> method();</pre>
         */
        PROCESSOR_VOID_2_PROCESSOR(true),
        /**
         * Invoke at: Assembly time -
         * <pre>ProcessorBuilder&lt;Message&lt;I>, Message&lt;O>> method();</pre>
         * <pre></pre>
         */
        PROCESSOR_VOID_2_PROCESSOR_BUILDER(true),
        /**
         * Invoke at: assembly time
         * <pre>Publisher&lt;Message&lt;O>> method(Message&lt;I> msg);</pre>
         * <pre>Publisher&lt;O> method(I payload);</pre>
         */
        PROCESSOR_PUBLISHER_2_PUBLISHER(true),
        /**
         * Invoke at: assembly time
         * <pre>PublisherBuilder&lt;O> method(PublisherBuilder&lt;I> pub);</pre>
         */
        PROCESSOR_PUBLISHER_BUILDER_2_PUBLISHER_BUILDER(true),
        /**
         * Invoke at: every incoming
         * <pre>Publisher&lt;Message&lt;O>> method(Message&lt;I>msg);</pre>
         * <pre>Publisher&lt;O> method(I payload);</pre>
         */
        PROCESSOR_MSG_2_PUBLISHER(false),
        /**
         * Invoke at: every incoming
         * <pre>Message&lt;O> method(Message&lt;I> msg)</pre>
         * <pre>O method(I payload)</pre>
         */
        PROCESSOR_MSG_2_MSG(false),
        /**
         * Invoke at: every incoming
         * <pre>CompletionStage&lt;Message&lt;O>> method(Message&lt;I> msg)</pre>
         * <pre>CompletionStage&lt;O> method(I payload)</pre>
         */
        PROCESSOR_MSG_2_COMPL_STAGE(false),


        /**
         * Invoke at: assembly time
         * <pre>Subscriber&lt;Message&lt;I>> method()</pre>
         * <pre>Subscriber&lt;I> method()</pre>
         */
        INCOMING_VOID_2_SUBSCRIBER(true),
        /**
         * Invoke at: assembly time
         * <pre>SubscriberBuilder&lt;Message&lt;I>> method()</pre>
         * <pre>SubscriberBuilder&lt;I> method()</pre>
         */
        INCOMING_VOID_2_SUBSCRIBER_BUILDER(true),
        /**
         * Invoke at: every incoming
         * <pre>void method(I payload)</pre>
         */
        INCOMING_MSG_2_VOID(false),
        /**
         * Invoke at: every incoming
         * <pre>CompletionStage&lt;?> method(Message&lt;I>msg)</pre>
         * <pre>CompletionStage&lt;?> method(I payload)</pre>
         */
        INCOMING_MSG_2_COMPLETION_STAGE(false);


        private boolean invokeAtAssembly;

        Type(boolean invokeAtAssembly) {
            this.invokeAtAssembly = invokeAtAssembly;
        }

        public boolean isInvokeAtAssembly() {
            return invokeAtAssembly;
        }
    }
}
