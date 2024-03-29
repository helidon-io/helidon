/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.http;

/**
 * All types from this class are moved to top-level classes.
 * This "container" class will be reused later, kept here for deprecation purposes to point to the right place
 * for the time being.
 *
 * @deprecated please use the top level classes in this package
 * @see io.helidon.http.Method
 * @see io.helidon.http.Status
 * @see io.helidon.http.HeaderName
 * @see io.helidon.http.HeaderNames
 * @see io.helidon.http.Header
 * @see io.helidon.http.HeaderWriteable
 * @see io.helidon.http.HeaderValues
 * @see io.helidon.http.DateTime
 */
@Deprecated(since = "4.0.0")
public final class Http {
    private Http() {
    }
}
