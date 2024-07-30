/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
 * Provides classes and interfaces to assist in the development of
 * {@link javax.sql.DataSource}-related CDI portable extensions.
 *
 * @see
 * io.helidon.integrations.datasource.cdi.AbstractDataSourceExtension
 */
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.integrations.datasource.cdi {

    requires transitive jakarta.cdi;
    requires transitive jakarta.inject;
    requires transitive java.sql;
    requires transitive io.helidon.integrations.cdi.configurable;
    requires transitive microprofile.config.api;
    exports io.helidon.integrations.datasource.cdi;

}
