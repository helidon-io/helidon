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
 * Provides classes and interfaces that help to integrate <a
 * href="https://javaee.github.io/javaee-spec/javadocs/javax/persistence/package-summary.html"
 * target="_parent">JPA</a> into <a href="http://weld.cdi-spec.org/"
 * target="_parent">Weld</a>-based CDI 2.0 SE environments.
 *
 * @see
 * io.helidon.integrations.cdi.jpa.weld.WeldJpaInjectionServices
 */
module io.helidon.integrations.cdi.jpa.weld {
    requires java.transaction;
    requires java.annotation;
    requires java.sql;
    requires java.persistence;
    requires jakarta.interceptor.api;
    requires jakarta.inject.api;
    requires jakarta.enterprise.cdi.api;
    requires weld.spi;

    exports io.helidon.integrations.cdi.jpa.weld;
}
