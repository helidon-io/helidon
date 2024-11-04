/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.helidon.service.inject.InjectConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

// test that the generated main class exists and works as expected
public class MainClassTest {
    @Test
    public void testMain() throws NoSuchMethodException {
        // this class is expected to be code generated based on CustomMain @Injection.Main annotation
        ApplicationMain appMain = new ApplicationMain();

        assertThat(appMain, instanceOf(CustomMain.class));

        Class<ApplicationMain> theClass = ApplicationMain.class;

        assertThat("The class must be public, to be a candidate for Main class",
                   Modifier.isPublic(theClass.getModifiers()));

        // the class must have the following two methods (when not using the maven plugin):
        // public static void main(String[] args) {}
        // protected void serviceDescriptors(InjectConfig.Builder config) {}
        Method mainMethod = theClass.getMethod("main", String[].class);
        assertThat("The main method must be public", Modifier.isPublic(mainMethod.getModifiers()));
        assertThat("The main method must be static", Modifier.isStatic(mainMethod.getModifiers()));
        assertThat("The main method must return void", mainMethod.getReturnType(), equalTo(void.class));

        Method serviceDescriptorMethod = theClass.getDeclaredMethod("serviceDescriptors", InjectConfig.Builder.class);
        assertThat("The service descriptors method must be protected",
                   Modifier.isProtected(serviceDescriptorMethod.getModifiers()));
        assertThat("The service descriptors method must not be static",
                   !Modifier.isStatic(serviceDescriptorMethod.getModifiers()));
        assertThat("The service descriptors method must return void",
                   serviceDescriptorMethod.getReturnType(),
                   equalTo(void.class));
    }
}
