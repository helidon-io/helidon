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
 * Provides classes and interfaces that enable <a
 * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#transactional_observer_methods">transactional
 * observer methods</a> in <a
 * href="http://weld.cdi-spec.org/">Weld</a>-backed <a
 * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html">CDI</a> 2.0
 * SE implementations using the <a
 * href="http://narayana.io/">Narayana</a> engine.
 */
module io.helidon.integrations.jta.weld {
    requires java.transaction;
    requires java.logging;
    requires java.rmi;
    requires jakarta.enterprise.cdi.api;
    requires cdi;    // org.jboss.narayana.jta
    requires jta;    //org.jboss.narayana.jta.jta
    requires common; // org.jboss.narayana.common
    requires arjuna; // org.jboss.narayana.arjunacore
    requires weld.spi;

    exports io.helidon.integrations.jta.weld;

    provides org.jboss.weld.bootstrap.api.Service
            with io.helidon.integrations.jta.weld.NarayanaTransactionServices;
}
