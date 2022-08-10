/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.socket;

/**
 * Information available for a connected socket.
 */
public interface SocketContext {
    /**
     * Remote peer information.
     *
     * @return peer info
     */
    PeerInfo remotePeer();

    /**
     * Local peer information.
     *
     * @return peer info
     */
    PeerInfo localPeer();

    /**
     * Whether the request is secure.
     *
     * @return whether secure
     */
    boolean isSecure();

    /**
     * Main socket id.
     * This is the server socket id for server side, connection id for client side.
     * @return socket id
     */
    String socketId();

    /**
     * Child socket id.
     * This is the connection socket id for server side. For client side, this may be additional
     * identification of a request (pipeline id, stream id).
     * @return child socket id, never null
     */
    String childSocketId();

    /**
     * Log a message with the current {@link #socketId()} and {@link #childSocketId()} to have
     * consistent logs mappable to sockets.
     *
     * @param logger logger to use
     * @param level log level to use
     * @param format format (can use string format pattern for the variables provided)
     * @param variables variables of the format
     */
    default void log(System.Logger logger,
                     System.Logger.Level level,
                     String format,
                     Object... variables) {
        if (logger.isLoggable(level)) {
            logger.log(level, message(format, variables));
        }
    }

    /**
     * Log a message with the current {@link #socketId()} and {@link #childSocketId()} to have
     * consistent logs mappable to sockets.
     *
     * @param logger logger to use
     * @param level log level to use
     * @param format format (can use string format pattern for the variables provided)
     * @param t throwable to log in addition to the message
     * @param variables variables of the format
     */
    default void log(System.Logger logger,
                     System.Logger.Level level,
                     String format,
                     Throwable t,
                     Object... variables) {
        if (logger.isLoggable(level)) {
            logger.log(level, message(format, variables), t);
        }
    }

    private String message(String format, Object... variables) {
        Object[] newVars = new Object[2 + variables.length];
        newVars[0] = socketId();
        newVars[1] = childSocketId();
        System.arraycopy(variables, 0, newVars, 2, variables.length);
        return String.format("[%s %s] " + format, newVars);
    }
}
