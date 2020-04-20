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
 * Provides classes and interfaces for working with <a
 * href="https://www.eclipse.org/eclipselink/#jpa"
 * target="_parent">Eclipselink</a> in CDI.
 *
 * @see io.helidon.integrations.cdi.eclipselink.CDISEPlatform
 */
module io.helidon.integrations.cdi.eclipselink {
    requires java.management;

    requires java.transaction;
    requires jakarta.enterprise.cdi.api;
    requires jakarta.inject.api;
    requires java.sql;
    requires org.eclipse.persistence.jpa;
    requires org.eclipse.persistence.core;

    exports io.helidon.integrations.cdi.eclipselink;
}
