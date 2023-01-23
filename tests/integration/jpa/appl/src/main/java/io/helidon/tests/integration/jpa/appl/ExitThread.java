/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;

/**
 * Exits JPA MP application after short delay.
 */
public class ExitThread implements Runnable {

    private static final System.Logger LOGGER = System.getLogger(ExitThread.class.getName());

    /**
     * Starts application exit thread.
     */
    public static final void start() {
        new Thread(new ExitThread()).start();
    }

    /**
     * Wait few seconds and terminate Java VM.
     */
    @Override
    public void run() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ie) {
            LOGGER.log(Level.WARNING, () -> String.format("Thread was interrupted: %s", ie.getMessage()), ie);
        } finally {
            System.exit(0);
        }
    }
    
}
