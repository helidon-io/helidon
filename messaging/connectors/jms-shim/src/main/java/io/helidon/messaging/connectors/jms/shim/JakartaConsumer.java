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

import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

import static io.helidon.messaging.connectors.jms.shim.ShimUtil.callRuntime;
import static io.helidon.messaging.connectors.jms.shim.ShimUtil.runRuntime;

/**
 * Exposes Jakarta API, delegates to javax API.
 */
class JakartaConsumer implements JMSConsumer {
    private final javax.jms.JMSConsumer delegate;

    JakartaConsumer(javax.jms.JMSConsumer delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getMessageSelector() {
        return delegate.getMessageSelector();
    }

    @Override
    public MessageListener getMessageListener() throws JMSRuntimeException {
        return JakartaJms.create(callRuntime(delegate::getMessageListener));
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSRuntimeException {
        runRuntime(() -> delegate.setMessageListener(JavaxJms.create(listener)));
    }

    @Override
    public Message receive() {
        return JakartaJms.create(delegate.receive());
    }

    @Override
    public Message receive(long timeout) {
        return JakartaJms.create(delegate.receive(timeout));
    }

    @Override
    public Message receiveNoWait() {
        return JakartaJms.create(delegate.receiveNoWait());
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public <T> T receiveBody(Class<T> c) {
        return delegate.receiveBody(c);
    }

    @Override
    public <T> T receiveBody(Class<T> c, long timeout) {
        return delegate.receiveBody(c, timeout);
    }

    @Override
    public <T> T receiveBodyNoWait(Class<T> c) {
        return delegate.receiveBodyNoWait(c);
    }
}
