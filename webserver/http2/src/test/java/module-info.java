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
open module helidon.webserver.http2.test {

    requires io.helidon.webserver;
    requires java.logging;
    requires java.net.http;
    requires hamcrest.all;
    requires org.junit.jupiter.api;
    requires io.helidon.webserver.http2;
    requires io.helidon.webclient;
}