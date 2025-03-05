/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.data.sql.common;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.data.DataException;

record SqlDriverImpl(Class<? extends Driver> driverClass, Driver driver) implements SqlDriver {
    static SqlDriver create(SqlConfigBlueprint dataConfig) {
        // Try URL as 1st option
        if (dataConfig.connectionString().isPresent()) {
            String url = dataConfig.connectionString().get();
            try {
                Driver driver = DriverManager.getDriver(url);
                return new SqlDriverImpl(driver.getClass(), driver);
            } catch (SQLException e) {
                throw new DataException(String.format("No %s supporting JDBC driver found on classpath", url), e);
            }
        }
        // Try driver class as 2nd option
        if (dataConfig.jdbcDriverClassName().isPresent()) {
            String driverClass = dataConfig.jdbcDriverClassName().get();
            AtomicReference<Driver> driverReference = new AtomicReference<>(null);
            DriverManager.drivers().forEach(driver -> {
                if (driverClass.equals(driver.getClass().getName())) {
                    driverReference.compareAndSet(null, driver);
                }
            });
            if (driverReference.get() != null) {
                return new SqlDriverImpl(driverReference.get().getClass(), driverReference.get());
            } else {
                throw new DataException(String.format("No %s JDBC driver found on classpath", driverClass));
            }
        }
        // Grab 1st driver available on classpath as a fallback
        Optional<? extends Driver> maybeDriver = DriverManager.drivers().findFirst();
        if (maybeDriver.isPresent()) {
            Driver driver = maybeDriver.get();
            return new SqlDriverImpl(driver.getClass(), driver);
        } else {
            throw new DataException("No JDBC driver found on classpath");
        }
    }
}
