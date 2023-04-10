/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
 * Provides classes and interfaces that integrate the
 * provider-independent parts of <a
 * href="https://jakarta.ee/specifications/persistence/3.0/"
 * target="_parent">JPA</a> into CDI.
 *
 * @see io.helidon.integrations.cdi.jpa.JpaExtension
 *
 * @see io.helidon.integrations.cdi.jpa.PersistenceUnitInfoBean
 */
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.integrations.cdi.jpa {

    // Static metamodel generation requires access to java.compiler at
    // compile time only.
    requires static java.compiler;
  
    requires jakarta.xml.bind;

    requires jakarta.inject; // automatic module
    requires jakarta.interceptor.api; // automatic module

    requires io.helidon.integrations.cdi.delegates;
    requires io.helidon.integrations.cdi.referencecountedcontext;

    requires transitive jakarta.cdi; // automatic module
    requires transitive jakarta.annotation; // automatic module
    requires transitive jakarta.persistence; // automatic module
    requires transitive java.sql;

    // JTA is optional at runtime, as well as the modules that support
    // it.
    requires static jakarta.transaction; // automatic module
    requires static io.helidon.integrations.jta.jdbc;

    exports io.helidon.integrations.cdi.jpa;
    exports io.helidon.integrations.cdi.jpa.jaxb;

    provides jakarta.enterprise.inject.spi.Extension with
        io.helidon.integrations.cdi.jpa.JpaExtension, io.helidon.integrations.cdi.jpa.PersistenceExtension;
}
