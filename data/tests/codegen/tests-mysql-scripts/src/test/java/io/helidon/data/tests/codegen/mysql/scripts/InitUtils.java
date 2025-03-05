/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.data.tests.codegen.mysql.scripts;

import java.sql.DriverManager;

class InitUtils {
    private static final System.Logger LOGGER = System.getLogger(InitUtils.class.getName());

    /**
     * Wait for database container to come up.
     *
     * @param check   container check to be executed periodically until no exception is thrown
     * @param timeout container start up timeout in seconds
     */
    @SuppressWarnings({"SleepWhileInLoop", "BusyWait"})
    static void waitForStart(StartCheck check, int timeout) {
        LOGGER.log(System.Logger.Level.TRACE, "Waiting for database server to come up");
        long endTm = 1000L * timeout + System.currentTimeMillis();
        while (true) {
            try {
                check.check();
                break;
            } catch (Throwable th) {
                if (System.currentTimeMillis() > endTm) {
                    throw new IllegalStateException("Database startup failed!", th);
                }
                LOGGER.log(System.Logger.Level.DEBUG, () -> String.format("Exception: %s", th.getMessage()), th);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /**
     * Wait for database container to come up.
     * With single check timeout modified.
     *
     * @param check        container check to be executed periodically until no exception is thrown
     * @param timeout      container start up timeout in seconds
     * @param checkTimeout single check timeout in seconds
     */
    static void waitForStart(StartCheck check, int timeout, int checkTimeout) {
        int currentLoginTimeout = DriverManager.getLoginTimeout();
        DriverManager.setLoginTimeout(checkTimeout);
        InitUtils.waitForStart(check, timeout);
        DriverManager.setLoginTimeout(currentLoginTimeout);
    }

    /**
     * Check code to be executed periodically while waiting for database container to come up.
     */
    @FunctionalInterface
    interface StartCheck {
        /**
         * Check whether database is already up and accepts connections.
         *
         * @throws Exception when check failed
         */
        void check() throws Exception;
    }

}
