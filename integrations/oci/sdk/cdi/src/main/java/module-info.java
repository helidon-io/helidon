/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 * Provides classes and interfaces that integrate any service client
 * from the <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/index.html"
 * target="_top">Oracle Cloud Infrastructure Java SDK</a> into CDI
 * 3.0-based applications.
 *
 * @see io.helidon.integrations.oci.sdk.cdi.OciExtension
 */
@SuppressWarnings({ "module", "requires-automatic", "requires-transitive-automatic" })
module io.helidon.integrations.oci.sdk.cdi {

    requires java.logging;
    requires transitive jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.interceptor.api;
    requires jakarta.ws.rs;
    requires microprofile.config.api;
    requires oci.java.sdk.common;
    requires oci.java.sdk.common.httpclient;

    exports io.helidon.integrations.oci.sdk.cdi;

    provides jakarta.enterprise.inject.spi.Extension
        with io.helidon.integrations.oci.sdk.cdi.OciExtension;

    opens io.helidon.integrations.oci.sdk.cdi to weld.core.impl;
}
