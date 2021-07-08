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
 *
 */

package io.helidon.microprofile.lra;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class IndexerTest {

    private Index index;

    @Test
    @Disabled
    void name() throws IOException, NoSuchMethodException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Indexer indexer = new Indexer();
        indexer.index(cl.getResourceAsStream("io/helidon/microprofile/lra/IndexerTest$TestInnerClass.class"));
        indexer.index(cl.getResourceAsStream("io/helidon/microprofile/lra/IndexerTest$TestSuperClass.class"));
        indexer.index(cl.getResourceAsStream("io/helidon/microprofile/lra/IndexerTest$TestInterface.class"));
        indexer.index(cl.getResourceAsStream("io/helidon/microprofile/lra/TestApplication$CommonAfter.class"));
        index = indexer.complete();

        Map<String, AnnotationInstance> annotations = new HashMap<>();
        Method m = TestInnerClass.class.getMethod("ok");
        ClassInfo clazz = index.getClassByName(DotName.createSimple(m.getDeclaringClass().getName()));
        // deepScanLraMethod(clazz, annotations, m.getName());
        System.out.println();
        System.out.println(
                annotations.entrySet().stream().map(e -> e.getKey() + " : " + e.getValue().toString()).collect(Collectors.joining("\n"))
        );

        // System.out.println("Class level @LRA: " + deepScanClassLevelLraAnnotation(clazz));
    }


    //@LRA(cancelOn = Response.Status.ACCEPTED)
    static class TestInnerClass extends TestSuperClass implements TestInterface {

        @Override
        @LRA(LRA.Type.REQUIRES_NEW)
        public void ok() {

        }
    }

    //@LRA(cancelOn = Response.Status.BAD_GATEWAY)
    static class TestSuperClass {
        @AfterLRA
        @LRA(LRA.Type.NEVER)
        void ok() {

        }
    }

    @LRA(cancelOn = Response.Status.CONFLICT)
    interface TestInterface {

        @AfterLRA
        @LRA(LRA.Type.MANDATORY)
        void ok();
    }
}
