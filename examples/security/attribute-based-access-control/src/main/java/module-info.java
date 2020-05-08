/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
 * Example of attribute based access control.
 */
module io.helidon.security.examples.abac {
    // CDI API
    requires jakarta.enterprise.cdi.api;
    // implementation of expression language to use (used by the abac provider: policy expression language
    requires jakarta.el.api;
    requires io.helidon.microprofile.bundle;
    // needed for security components and restrictions of this module
    requires io.helidon.security;
    requires io.helidon.security.annotations;
    requires io.helidon.security.abac.time;
    requires io.helidon.security.abac.role;
    requires io.helidon.security.abac.policy;
    requires io.helidon.security.abac.scope;

    // needed for jersey to start without a lot of errors (hk2 actually)
    requires java.xml.bind;

    // java util logging
    requires java.logging;

    exports io.helidon.security.examples.abac;
}
