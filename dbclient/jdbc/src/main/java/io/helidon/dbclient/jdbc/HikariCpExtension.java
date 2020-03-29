/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient.jdbc;

import com.zaxxer.hikari.HikariConfig;

/**
 * Interceptor to handle connection pool configuration.
 */
public interface HikariCpExtension {
    /**
     * Set additional configuration option on DB client configuration.
     *
     * @param poolConfig client configuration instance
     */
    void configure(HikariConfig poolConfig);
}
