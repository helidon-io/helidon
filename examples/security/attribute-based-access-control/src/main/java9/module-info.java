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

/**
 * Example of attribute based access control.
 */
module io.helidon.security.examples.abac {
    // bundle of security providers and tools
    requires io.helidon.security.bundle;
    // jersey integration is separately packaged
    requires io.helidon.security.adapter.jersey;
    // implementation of expression language to use (used by the abac provider: policy expression language
    requires javax.el;


    // webserver jersey integration (also transitively provides webserver)
    requires io.helidon.webserver.jersey;
    // needed for jersey to start without a lot of errors (hk2 actually)
    requires java.xml.bind;

    // java util logging
    requires java.logging;

    exports io.helidon.security.examples.abac;
}
