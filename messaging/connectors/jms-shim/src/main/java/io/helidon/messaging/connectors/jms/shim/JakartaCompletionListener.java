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

import jakarta.jms.CompletionListener;
import jakarta.jms.JMSException;
import jakarta.jms.Message;

class JakartaCompletionListener implements CompletionListener {
    private final javax.jms.CompletionListener delegate;

    JakartaCompletionListener(javax.jms.CompletionListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onCompletion(Message message) {
        delegate.onCompletion(ShimUtil.message(message));
    }

    @Override
    public void onException(Message message, Exception exception) {
        javax.jms.Message msg = ShimUtil.message(message);

        if (exception instanceof JMSException jmsException) {
            delegate.onException(msg, ShimUtil.exception(jmsException));
        } else {
            delegate.onException(msg, exception);
        }
    }
}
