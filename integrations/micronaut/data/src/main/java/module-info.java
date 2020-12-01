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
 * Integration with Micronaut Data.
 */
module io.helidon.integrations.micronaut.data {
    requires java.annotation;
    requires java.sql;

    requires io.helidon.integrations.micronaut.cdi;

    requires jakarta.enterprise.cdi.api;
    requires jakarta.interceptor.api;

    requires io.micronaut.inject;

    provides javax.enterprise.inject.spi.Extension with io.helidon.integrations.micronaut.cdi.data.MicronautDataCdiExtension;

    exports io.helidon.integrations.micronaut.cdi.data;
}