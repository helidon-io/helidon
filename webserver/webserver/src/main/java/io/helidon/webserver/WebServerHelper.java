/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Map;

/**
 * A set of helper methods for accessing WebServer functionality. This
 * is primarily used by tests in other packages that need to construct
 * WebServer objects, but should not know the implementation details
 * of webserver
 */
public class WebServerHelper {

    private WebServerHelper() {
        //not called
    }

    /**
     * Create an instance of RequestHeaders.
     *
     * @param headerMap a Map of request headers
     *
     * @return a new instance built with the passed headers
     */
    public static RequestHeaders constructRequestHeaders(Map<String, List<String>> headerMap) {
        return new HashRequestHeaders(headerMap);
    }
}
