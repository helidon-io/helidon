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

import javax.jms.CompletionListener;
import javax.jms.JMSException;
import javax.jms.Message;

class JavaxCompletionListener implements CompletionListener {
    private final jakarta.jms.CompletionListener delegate;

    JavaxCompletionListener(jakarta.jms.CompletionListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onCompletion(Message message) {
        delegate.onCompletion(JakartaJms.create(message));
    }

    @Override
    public void onException(Message message, Exception exception) {
        if (exception instanceof JMSException jmsException) {
            delegate.onException(JakartaJms.create(message), ShimUtil.exception(jmsException));
        } else {
            delegate.onException(JakartaJms.create(message), exception);
        }
    }
}
