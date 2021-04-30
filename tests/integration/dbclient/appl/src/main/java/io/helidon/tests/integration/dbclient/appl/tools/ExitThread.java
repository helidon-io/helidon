/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.dbclient.appl.tools;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.webserver.WebServer;

/**
 * Exits JPA MP application after short delay.
 */
public class ExitThread implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ExitThread.class.getName());

    /**
     * Starts application exit thread.
     *
     * @param server web server instance to shut down
     */
    public static final void start(final WebServer server) {
        new Thread(new ExitThread(server)).start();
    }

    private final WebServer server;

    private ExitThread(final WebServer server) {
        this.server = server;
    }

    /**
     * Wait few seconds and terminate web server.
     */
    @Override
    public void run() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ie) {
            LOGGER.log(Level.WARNING, ie, () -> String.format("Thread was interrupted: %s", ie.getMessage()));
        } finally {
            server.shutdown();
        }
    }

}
