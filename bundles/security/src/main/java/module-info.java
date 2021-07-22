/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
 * Bundle module to simplify application dependencies.
 */
module io.helidon.security.bundle {
    requires transitive io.helidon.security;
    requires transitive io.helidon.security.annotations;
    requires transitive io.helidon.config.encryption;
    requires transitive io.helidon.security.providers.header;
    requires transitive io.helidon.security.providers.httpauth;
    requires transitive io.helidon.security.providers.httpsign;
    requires transitive io.helidon.security.providers.jwt;
    requires transitive io.helidon.security.providers.abac;
    requires transitive io.helidon.security.abac.time;
    requires transitive io.helidon.security.abac.policy;
    requires transitive io.helidon.security.abac.role;
    requires transitive io.helidon.security.abac.scope;
    requires transitive io.helidon.security.providers.oidc;
}
