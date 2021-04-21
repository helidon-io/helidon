/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
 * Provides classes and interfaces that integrate <a
 * href="https://jcp.org/en/jsr/detail?id=907">JTA</a> version 1.2
 * into <a
 * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html">CDI</a>
 * version 2.0 using <a href="http://narayana.io/">Narayana</a> as the
 * underlying implementation.
 */
module io.helidon.integrations.jta.cdi {
    requires java.transaction;
    requires java.annotation;
    requires java.sql;
    requires java.rmi;
    requires jakarta.interceptor.api;
    requires jakarta.inject.api;
    requires jakarta.enterprise.cdi.api;
    requires cdi;    // org.jboss.narayana.jta
    requires jta;    //org.jboss.narayana.jta.jta
    requires common; // org.jboss.narayana.common
    requires arjuna; // org.jboss.narayana.arjunacore

    exports io.helidon.integrations.jta.cdi;

    provides javax.enterprise.inject.spi.Extension
            with io.helidon.integrations.jta.cdi.NarayanaExtension;
}
