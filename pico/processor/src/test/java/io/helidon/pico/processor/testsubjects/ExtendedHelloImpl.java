/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.processor.testsubjects;

import io.helidon.pico.RunLevel;
import io.helidon.pico.testsubjects.hello.MyTestQualifier;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jdk.jfr.Label;

@Named("ExtendedHelloImpl")
@RunLevel(10)
public class ExtendedHelloImpl implements ExtendedHello {

    @Inject
    @MyTestQualifier(value = "ctor")
    ExtendedHelloImpl() {

    }

    @Override
    @Label("label b")
    @MyTestQualifier(value = "MyCompileTimeInheritableTestQualifier", extendedValue = "test 3")
    public String sayHello(
                    @Named("arg1 b")
                    @MyTestQualifier(extendedValue = "arg1 b")
                    String arg1,
                    @Named("arg2 b")
                    @MyTestQualifier(extendedValue = "arg1 b")
                    boolean arg2) {
        return null;
    }

    @Override
    public void sayHello() {

    }

}
