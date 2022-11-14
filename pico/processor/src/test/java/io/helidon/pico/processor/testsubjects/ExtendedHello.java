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

import java.io.IOException;

import io.helidon.pico.Contract;
import io.helidon.pico.testsubjects.hello.Hello;
import io.helidon.pico.testsubjects.hello.MyTestQualifier;

import jakarta.inject.Named;
import jdk.jfr.Category;
import jdk.jfr.Experimental;
import jdk.jfr.Label;

@Contract
@Named("ExtendedHello contract")
@Experimental
@Category("test")
@MyTestQualifier(value = "MyCompileTimeInheritableTestQualifier", extendedValue = "test 1")
public interface ExtendedHello extends Hello {

    @Named("sayHello")
    @Label("label")
    @MyTestQualifier(value = "MyCompileTimeInheritableTestQualifier", extendedValue = "test 2")
    String sayHello(@Named("arg1") String arg1, boolean arg2) throws IOException, RuntimeException;

}
