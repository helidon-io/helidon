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

package io.helidon.tests.integration.jep290.seto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Random;

import io.helidon.common.SerializationConfig;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DeserializationTest {
    private static final String TEST_STRING = "Hello_" + new Random().nextInt(10);;

    @BeforeAll
    static void configureDeserialization() {
        SerializationConfig.builder()
                .onWrongConfig(SerializationConfig.Action.IGNORE)
                .onNoConfig(SerializationConfig.Action.IGNORE)
                .ignoreFiles(false)
                .traceSerialization(SerializationConfig.TraceOption.NONE)
                .build()
                .configure();
    }

    @Test
    void testConfiguredInFile() throws IOException, ClassNotFoundException {
        ConfiguredInFile object = new ConfiguredInFile(TEST_STRING);

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

        assertThat(object.text(), is(TEST_STRING));
    }

    @Test
    void testNotConfigured() throws IOException, ClassNotFoundException {
        NotConfigured object = new NotConfigured(TEST_STRING);

        ByteArrayOutputStream ob = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(ob);

        oos.writeObject(object);
        oos.close();

        byte[] bytes = ob.toByteArray();

        ByteArrayInputStream ib = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(ib);

        Object o = ois.readObject();

        // we did not configure filter, so all should pass
        assertThat(o, CoreMatchers.instanceOf(NotConfigured.class));

        object = (NotConfigured) o;

        assertThat(object.text(), is(TEST_STRING));

    }
}
