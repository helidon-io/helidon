/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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
 */
package io.helidon.messaging.connectors.jms.shim;

import javax.jms.Queue;
import javax.jms.Topic;

import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Message;

final class ShimUtil {
    private ShimUtil() {
    }

    static <T> T call(JavaxJmsCallable<T> callable) throws JMSException {
        try {
            return callable.invoke();
        } catch (javax.jms.JMSException e) {
            throw exception(e);
        }
    }

    static void run(JavaxJmsRunnable runnable) throws JMSException {
        try {
            runnable.run();
        } catch (javax.jms.JMSException e) {
            throw exception(e);
        }
    }

    static <T> T callRuntime(JavaxJmsRuntimeCallable<T> callable) throws JMSRuntimeException {
        try {
            return callable.invoke();
        } catch (javax.jms.JMSRuntimeException e) {
            throw exception(e);
        }
    }

    static void runRuntime(JavaxJmsRuntimeRunnable runnable) throws JMSRuntimeException {
        try {
            runnable.run();
        } catch (javax.jms.JMSRuntimeException e) {
            throw exception(e);
        }
    }

    static JMSRuntimeException exception(javax.jms.JMSRuntimeException source) {
        return new JMSRuntimeException(source.getMessage(), source.getErrorCode(), source);
    }

    static JMSException exception(javax.jms.JMSException source) {
        JMSException result = new JMSException(source.getMessage(), source.getErrorCode());
        result.setLinkedException(source.getLinkedException());
        result.addSuppressed(source);
        return result;
    }

    static javax.jms.JMSException exception(JMSException source) {
        javax.jms.JMSException result = new javax.jms.JMSException(source.getMessage(), source.getErrorCode());
        result.setLinkedException(source.getLinkedException());
        result.addSuppressed(source);
        return result;
    }

    static javax.jms.Destination destination(Destination destination) {
        if (destination instanceof JakartaDestination jakartaDestination) {
            return jakartaDestination.unwrap();
        }
        if (destination instanceof javax.jms.Destination javaxDestination) {
            return javaxDestination;
        }
        throw new RuntimeException("Destination was not created correctly, cannot convert to javax.jms.Destination: "
                                           + destination);
    }

    static Topic topic(jakarta.jms.Topic topic) {
        if (topic instanceof JakartaTopic jakartaTopic) {
            return jakartaTopic.unwrap();
        }
        if (topic instanceof javax.jms.Topic javaxTopic) {
            return javaxTopic;
        }
        throw new RuntimeException("Topic was not created correctly, cannot convert to javax.jms.Topic: " + topic);
    }

    static Queue queue(jakarta.jms.Queue queue) {
        if (queue instanceof JakartaQueue jakartaQueue) {
            return jakartaQueue.unwrap();
        }
        if (queue instanceof javax.jms.Topic) {
            return (javax.jms.Queue) queue;
        }
        throw new RuntimeException("Queue was not created correctly, cannot convert to javax.jms.Queue: " + queue);
    }

    static javax.jms.Message message(Message message) {
        if (message instanceof JakartaMessage jakartaMessage) {
            return jakartaMessage.unwrap();
        }
        if (message instanceof javax.jms.Message javaxMessage) {
            return javaxMessage;
        }
        throw new RuntimeException("Message was not created correctly, cannot convert to javax.jms.Message: " + message);
    }

    interface JavaxJmsRuntimeCallable<T> {
        T invoke() throws javax.jms.JMSRuntimeException;
    }

    interface JavaxJmsCallable<T> {
        T invoke() throws javax.jms.JMSException;
    }

    interface JavaxJmsRunnable {
        void run() throws javax.jms.JMSException;
    }

    interface JavaxJmsRuntimeRunnable {
        void run() throws javax.jms.JMSRuntimeException;
    }
}
