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

import javax.jms.CompletionListener;
import javax.jms.ExceptionListener;
import javax.jms.MessageListener;
import javax.jms.ServerSessionPool;

/**
 * Main shim entry point, allows wrapping jakarta types to javax types.
 */
public final class JavaxJms {
    private JavaxJms() {
    }

    /**
     * Create a javax wrapper for the provided jakarta instance.
     * @param delegate jakarta namespace instance
     * @return shimmed javax namespace instance
     */
    public static CompletionListener create(jakarta.jms.CompletionListener delegate) {
        return new JavaxCompletionListener(delegate);
    }

    /**
     * Create a javax wrapper for the provided jakarta instance.
     * @param delegate jakarta namespace instance
     * @return shimmed javax namespace instance
     */
    public static ExceptionListener create(jakarta.jms.ExceptionListener delegate) {
        return new JavaxExceptionListener(delegate);
    }

    /**
     * Create a javax wrapper for the provided jakarta instance.
     * @param delegate jakarta namespace instance
     * @return shimmed javax namespace instance
     */
    public static MessageListener create(jakarta.jms.MessageListener delegate) {
        return new JavaxMessageListener(delegate);
    }

    /**
     * Create a javax wrapper for the provided jakarta instance.
     * @param delegate jakarta namespace instance
     * @return shimmed javax namespace instance
     */
    public static ServerSessionPool create(jakarta.jms.ServerSessionPool delegate) {
        return new JavaxSessionPool(delegate);
    }
}
