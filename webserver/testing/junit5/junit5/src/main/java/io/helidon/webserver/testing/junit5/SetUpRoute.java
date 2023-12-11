/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver.testing.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.webserver.WebServer;

/**
 * A static method configuring router (and/or socket) for the server.
 * Multiple methods may exist, each configuring a different socket (see {@link #value()}).
 *
 * Supported signatures:
 * {@code static void routing(HttpRouting.Builder builder)}
 * {@code static void routing(HttpRouting.Builder builder, ListenerConfiguration.Builder builder)}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SetUpRoute {
    /**
     * Socket name for this router.
     *
     * @return name of the socket
     */
    String value() default WebServer.DEFAULT_SOCKET_NAME;
}
