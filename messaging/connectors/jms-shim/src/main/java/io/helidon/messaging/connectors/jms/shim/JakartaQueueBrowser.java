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
 */
package io.helidon.messaging.connectors.jms.shim;

import java.util.Enumeration;

import javax.jms.Message;

import jakarta.jms.JMSException;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;

import static io.helidon.messaging.connectors.jms.shim.ShimUtil.call;
import static io.helidon.messaging.connectors.jms.shim.ShimUtil.run;

/**
 * Exposes Jakarta API, delegates to javax API.
 */
class JakartaQueueBrowser implements QueueBrowser {
    private final javax.jms.QueueBrowser delegate;

    JakartaQueueBrowser(javax.jms.QueueBrowser delegate) {
        this.delegate = delegate;
    }

    @Override
    public Queue getQueue() throws JMSException {
        return JakartaJms.create(call(delegate::getQueue));
    }

    @Override
    public String getMessageSelector() throws JMSException {
        return call(delegate::getMessageSelector);
    }

    @Override
    public Enumeration getEnumeration() throws JMSException {
        Enumeration original = call(delegate::getEnumeration);
        return new Enumeration() {
            @Override
            public boolean hasMoreElements() {
                return original.hasMoreElements();
            }

            @Override
            public Object nextElement() {
                return JakartaJms.create((Message) original.nextElement());
            }
        };
    }

    @Override
    public void close() throws JMSException {
        run(delegate::close);
    }
}
