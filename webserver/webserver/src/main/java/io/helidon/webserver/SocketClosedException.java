/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

/**
 * Signals that socket was closed before server request it. There can be unsent response data.
 *
 * <p>The exception should be distributed in {@link ServerResponse#whenSent() server response completion stage} which
 * {@code completes exceptionally} in such case.
 */
public class SocketClosedException extends IllegalStateException {

    /**
     * Creates new instance.
     *
     * @param s a detail message
     */
    public SocketClosedException(String s) {
        super(s);
    }

    /**
     * Creates new instance.
     *
     * @param message a detail message
     * @param cause an original cause
     */
    public SocketClosedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates new instance.
     *
     * @param cause an original cause
     */
    public SocketClosedException(Throwable cause) {
        super(cause);
    }
}
