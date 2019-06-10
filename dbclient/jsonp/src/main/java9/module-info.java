/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 * Helidon DB JSON-P Mapper.
 */
module io.helidon.dbclient.jsonp {
    requires java.logging;
    requires io.helidon.dbclient;
    requires java.json;

    exports io.helidon.dbclient.jsonp;

    provides io.helidon.dbclient.spi.DbMapperProvider with io.helidon.dbclient.jsonp.JsonProcessingMapperProvider;
}

