/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.integration.webserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import io.helidon.webserver.WebServer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Helper class.
 */
class WebSecurityTestUtil {

    static void auditLogFinest() {
        // enable audit logging
        Logger l = Logger.getLogger("AUDIT");

        ConsoleHandler ch = new ConsoleHandler();
        ch.setFormatter(new SimpleFormatter());
        ch.setLevel(Level.FINEST);
        l.addHandler(ch);
        l.setUseParentHandlers(false);
        l.setLevel(Level.FINEST);
    }

    static void stopServer(WebServer server) throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);
        long t = System.currentTimeMillis();
        if (null == server) {
            return;
        }
        server.shutdown().thenAccept(webServer -> {
            long time = System.currentTimeMillis() - t;
            System.out.println("Stopped server in " + time + " millis");
            cdl.countDown();
        });
        //we must wait until server is shutdown, so another test class doesn't try to use the same port
        assertThat("Timeout while waiting for server to stop", cdl.await(5, TimeUnit.SECONDS), is(true));
    }
}
