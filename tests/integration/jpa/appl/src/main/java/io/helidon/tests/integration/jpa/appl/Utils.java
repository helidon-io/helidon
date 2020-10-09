/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.appl;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Test utilities.
 */
public class Utils {

    /* Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(Utils.class.getName());

    private Utils() {
        throw new IllegalStateException("No instances of this class are allowed!");
    }

    /**
     * Close database connection.
     *
     * @param connection database connection
     */
    public static void closeConnection(final Connection connection) {
        try {
            connection.close();
        } catch (SQLException ex) {
            LOGGER.warning(() -> String.format("Could not close database connection: %s", ex.getMessage()));
        }
    }

}
