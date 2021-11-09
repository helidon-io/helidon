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

import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;

/**
 * Exposes Jakarta API, delegates to javax API.
 */
class JakartaExceptionListener implements ExceptionListener {
    private final javax.jms.ExceptionListener delegate;

    JakartaExceptionListener(javax.jms.ExceptionListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onException(JMSException exception) {
        delegate.onException(ShimUtil.exception(exception));
    }
}
