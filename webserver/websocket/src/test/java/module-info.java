/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
/**
 * WebSocket support for Helidon webserver tests.
 */
open module helidon.webserver.websocket.test {

    requires io.helidon.webserver.websocket;

    requires java.logging;
    requires java.net.http;
    requires hamcrest.all;
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    requires org.glassfish.tyrus.client;
    requires org.glassfish.tyrus.container.jdk.client;
    requires org.glassfish.tyrus.core;

    exports io.helidon.webserver.websocket.test;
}