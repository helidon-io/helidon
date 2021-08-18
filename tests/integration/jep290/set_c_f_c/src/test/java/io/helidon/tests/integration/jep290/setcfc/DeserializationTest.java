/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.jep290.setcfc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Random;

import io.helidon.common.SerializationConfig;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeserializationTest {
    private static String testString;

    @BeforeAll
    static void configureDeserialization() {
        SerializationConfig.builder()
                .onWrongConfig(SerializationConfig.Action.CONFIGURE)
                .onNoConfig(SerializationConfig.Action.CONFIGURE)
                .filterPattern(ConfiguredInPattern.class.getName())
                .ignoreFiles(false)
                .traceSerialization(SerializationConfig.TraceOption.NONE)
                .build()
                .configure();

        testString = "Hello_" + new Random().nextInt(10);
    }

    @Test
    void testConfiguredInFile() throws IOException, ClassNotFoundException {
        ConfiguredInFile object = new ConfiguredInFile(testString);

        ByteArrayOutputStream ob = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(ob);

        oos.writeObject(object);
        oos.close();

        byte[] bytes = ob.toByteArray();

        ByteArrayInputStream ib = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(ib);
        Object o = ois.readObject();

        assertThat(o, CoreMatchers.instanceOf(ConfiguredInFile.class));

        object = (ConfiguredInFile) o;

        assertThat(object.text(), is(testString));
    }

    @Test
    void testConfiguredInPattern() throws IOException, ClassNotFoundException {
        ConfiguredInPattern object = new ConfiguredInPattern(testString);

        ByteArrayOutputStream ob = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(ob);

        oos.writeObject(object);
        oos.close();

        byte[] bytes = ob.toByteArray();

        ByteArrayInputStream ib = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(ib);
        Object o = ois.readObject();

        assertThat(o, CoreMatchers.instanceOf(ConfiguredInPattern.class));

        object = (ConfiguredInPattern) o;

        assertThat(object.text(), is(testString));
    }

    @Test
    void testNotConfigured() throws IOException, ClassNotFoundException {
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
