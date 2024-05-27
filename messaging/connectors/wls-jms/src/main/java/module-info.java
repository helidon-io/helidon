/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
 * Microprofile messaging Weblogic JMS connector.
 */
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.messaging.connectors.wls {

    requires io.helidon.messaging.connectors.jms;
    requires jakarta.messaging;
    requires java.naming;

    requires static jakarta.cdi;
    requires static jakarta.inject;

    requires transitive io.helidon.config.mp;
    requires transitive microprofile.config.api;


    exports io.helidon.messaging.connectors.wls;

}
