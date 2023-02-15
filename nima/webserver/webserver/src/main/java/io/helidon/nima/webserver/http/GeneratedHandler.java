/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.http;

import io.helidon.common.http.Http;

/**
 * This class is only used by generated code.
 *
 * @deprecated please do not use directly, designed for generated code
 * @see io.helidon.nima.webserver.http1.Http1Route
 * @see io.helidon.nima.webserver.http.Handler
 */
@Deprecated(since = "4.0.0")
public interface GeneratedHandler extends Handler {
    /**
     * HTTP Method of this handler.
     *
     * @return method
     */
    Http.Method method();

    /**
     * Path this handler should be registered at.
     *
     * @return path, may include path parameter (template)
     */
    String path();


}
