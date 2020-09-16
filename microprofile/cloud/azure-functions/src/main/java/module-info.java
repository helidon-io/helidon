/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 * Azure Functions.
 */
module io.helidon.microprofile.cloud.azurefunctions {
    requires java.logging;
    requires io.helidon.common;
    requires io.helidon.microprofile.cloud.common;
    requires transitive azure.functions.java.library;
    requires jakarta.enterprise.cdi.api;

    exports io.helidon.microprofile.cloud.azurefunctions;
}
