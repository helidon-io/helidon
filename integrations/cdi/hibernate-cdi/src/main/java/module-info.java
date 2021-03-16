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
 * Provides classes and interfaces for working with <a
 * href="http://hibernate.org/orm/" target="_parent">Hibernate</a> in
 * CDI.
 *
 * @see io.helidon.integrations.cdi.hibernate.CDISEJtaPlatformProvider
 *
 * @see io.helidon.integrations.cdi.hibernate.CDISEJtaPlatform
 */
module io.helidon.integrations.cdi.hibernate {
    requires java.transaction;
    requires java.sql;
    requires jakarta.inject.api;
    requires jakarta.enterprise.cdi.api;
    requires org.hibernate.orm.core;
    // needed only for native image, transitive dependency of hibernate-core
    requires dom4j;

    exports io.helidon.integrations.cdi.hibernate;
}
