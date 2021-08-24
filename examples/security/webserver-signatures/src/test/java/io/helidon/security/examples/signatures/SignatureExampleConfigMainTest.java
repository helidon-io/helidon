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

package io.helidon.security.examples.signatures;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Unit test for {@link SignatureExampleBuilderMain}.
 */
public class SignatureExampleConfigMainTest extends SignatureExampleTest {
    private static int svc1Port;
    private static int svc2Port;

    @BeforeAll
    public static void initClass() {
        // override configuration to use random ports
        System.setProperty("server1.port", "0");
        System.setProperty("server2.port", "0");
        SignatureExampleConfigMain.main(null);
        svc1Port = SignatureExampleConfigMain.getService1Server().port();
        svc2Port = SignatureExampleConfigMain.getService2Server().port();
    }

    @AfterAll
    public static void destroyClass() throws InterruptedException {
        stopServer(SignatureExampleConfigMain.getService2Server());
        stopServer(SignatureExampleConfigMain.getService1Server());
    }

    @Override
    int getService1Port() {
        return svc1Port;
    }

    @Override
    int getService2Port() {
        return svc2Port;
    }
}
