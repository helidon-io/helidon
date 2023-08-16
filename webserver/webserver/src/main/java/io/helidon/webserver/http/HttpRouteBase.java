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

package io.helidon.webserver.http;

import java.util.List;

import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatchers;

abstract class HttpRouteBase implements HttpRoute {
    PathMatchers.PrefixMatchResult acceptsPrefix(HttpPrologue prologue) {
        throw new IllegalStateException("This is not a list route");
    }

    List<HttpRouteBase> routes() {
        throw new IllegalStateException("This is not a list route");
    }

    boolean isList() {
        return false;
    }
}
