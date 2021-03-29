/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

module io.helidon.integrations.oci.cdi {
    requires java.logging;

    requires jakarta.enterprise.cdi.api;

    requires io.helidon.config;
    requires io.helidon.integrations.oci.connect;
    requires io.helidon.common.serviceloader;
    requires io.helidon.microprofile.cdi;
    requires jakarta.inject.api;

    exports io.helidon.integrations.oci.cdi;

    uses io.helidon.integrations.oci.connect.spi.InjectionProvider;

    provides javax.enterprise.inject.spi.Extension
            with io.helidon.integrations.oci.cdi.OciCdiExtension;
}