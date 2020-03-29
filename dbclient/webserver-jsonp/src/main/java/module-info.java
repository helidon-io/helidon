/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
 * JSON-P support to map DB Client responses to webserver responses.
 */
module io.helidon.dbclient.webserver.jsonp {
    requires java.logging;
    requires java.json;
    requires io.helidon.common.http;
    requires io.helidon.media.common;
    requires io.helidon.webserver;
    requires io.helidon.dbclient;

    exports io.helidon.dbclient.webserver.jsonp;
}
