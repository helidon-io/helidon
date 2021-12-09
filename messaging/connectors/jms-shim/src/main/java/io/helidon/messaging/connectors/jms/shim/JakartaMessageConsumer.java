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

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;

import static io.helidon.messaging.connectors.jms.shim.ShimUtil.call;
import static io.helidon.messaging.connectors.jms.shim.ShimUtil.run;

class JakartaMessageConsumer implements MessageConsumer {
    private final javax.jms.MessageConsumer delegate;

    JakartaMessageConsumer(javax.jms.MessageConsumer delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getMessageSelector() throws JMSException {
        return call(delegate::getMessageSelector);
    }

    @Override
    public MessageListener getMessageListener() throws JMSException {
        return JakartaJms.create(call(delegate::getMessageListener));
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException {
        run(() -> delegate.setMessageListener(JavaxJms.create(listener)));
    }

    @Override
    public Message receive() throws JMSException {
        return JakartaJms.create((javax.jms.Message) call(delegate::receive));
    }

    @Override
    public Message receive(long timeout) throws JMSException {
        return JakartaJms.create(call(() -> delegate.receive(timeout)));
    }

    @Override
    public Message receiveNoWait() throws JMSException {
        return JakartaJms.create(call(delegate::receiveNoWait));
    }

    @Override
    public void close() throws JMSException {
        run(delegate::close);
    }
}
