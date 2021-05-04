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

package io.helidon.security.examples.jersey;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Test of hello world example.
 */
public class JerseyBuilderMainTest extends JerseyMainTest {
    @BeforeAll
    public static void initClass() throws Throwable {
        JerseyBuilderMain.main(null);
    }

    @AfterAll
    public static void destroyClass() throws InterruptedException {
        stopServer(JerseyBuilderMain.getHttpServer());
    }

    @Override
    protected int getPort() {
        return JerseyBuilderMain.getHttpServer().port();
    }
}
