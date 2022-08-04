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
import io.helidon.webserver.spi.UpgradeCodecProvider;

module io.helidon.webserver.http2 {

    exports io.helidon.webserver.http2;

    requires io.helidon.webserver;
    requires io.netty.transport;
    requires io.netty.codec.http2;
    requires io.netty.handler;
    requires io.netty.codec.http;
    requires io.netty.buffer;
    requires java.logging;
    requires io.netty.common;
    requires io.netty.codec;

    provides UpgradeCodecProvider
            with io.helidon.webserver.http2.Http2UpgradeCodecProvider;
}