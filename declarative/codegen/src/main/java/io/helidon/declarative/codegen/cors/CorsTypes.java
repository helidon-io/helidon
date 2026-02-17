/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.cors;

import io.helidon.common.types.TypeName;

final class CorsTypes {
    static final TypeName CORS_DEFAULTS = TypeName.create("io.helidon.webserver.cors.Cors.Defaults");
    static final TypeName CORS_ALLOWED_ORIGINS = TypeName.create("io.helidon.webserver.cors.Cors.AllowOrigins");
    static final TypeName CORS_ALLOWED_METHODS = TypeName.create("io.helidon.webserver.cors.Cors.AllowMethods");
    static final TypeName CORS_ALLOWED_HEADERS = TypeName.create("io.helidon.webserver.cors.Cors.AllowHeaders");
    static final TypeName CORS_EXPOSE_HEADERS = TypeName.create("io.helidon.webserver.cors.Cors.ExposeHeaders");
    static final TypeName CORS_ALLOW_CREDENTIALS = TypeName.create("io.helidon.webserver.cors.Cors.AllowCredentials");
    static final TypeName CORS_MAX_AGE_SECONDS = TypeName.create("io.helidon.webserver.cors.Cors.MaxAgeSeconds");

    static final TypeName CORS_PATH_CONFIG = TypeName.create("io.helidon.webserver.cors.CorsPathConfig");

    private CorsTypes() {
    }
}
