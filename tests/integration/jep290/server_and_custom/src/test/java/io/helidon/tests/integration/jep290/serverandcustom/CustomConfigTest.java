/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.jep290.serverandcustom;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Random;

import io.helidon.common.SerializationConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.WebServerConfig;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class CustomConfigTest {
    private static String testString;

    @SetUpServer
    static void setup(WebServerConfig.Builder requiredToBeFoundByExtension) {
        // first set up deserialization filter using a builder
        SerializationConfig.builder()
                .traceSerialization(SerializationConfig.TraceOption.FULL)
                .filterPattern(ConfiguredInBuilder.class.getName())
                .build()
                .configure();
        testString = "Hello_" + new Random().nextInt(10);
    }

    @Test
    void testConfigured() throws IOException, ClassNotFoundException {
        ConfiguredInBuilder object = new ConfiguredInBuilder(testString);

        ByteArrayOutputStream ob = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(ob);

        oos.writeObject(object);
        oos.close();

        byte[] bytes = ob.toByteArray();

        ByteArrayInputStream ib = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(ib);
        Object o = ois.readObject();

        assertThat(o, CoreMatchers.instanceOf(ConfiguredInBuilder.class));

        object = (ConfiguredInBuilder) o;

        assertThat(object.text(), is(testString));
    }

    @Test
    void testNotConfigured() throws IOException {
        NotConfigured object = new NotConfigured(testString);

        ByteArrayOutputStream ob = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(ob);

        oos.writeObject(object);
        oos.close();

        byte[] bytes = ob.toByteArray();

        ByteArrayInputStream ib = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(ib);

        assertThrows(InvalidClassException.class, ois::readObject);

    }
}
