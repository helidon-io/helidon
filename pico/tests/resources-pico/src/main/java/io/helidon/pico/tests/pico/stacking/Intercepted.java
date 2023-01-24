/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tests.pico.stacking;

import io.helidon.pico.Contract;

@Contract
//@MyCompileTimeInheritableTestQualifier(value = "interface", extendedValue = "ev interface")
public interface Intercepted {

    Intercepted getInner();

//    @MyCompileTimeInheritableTestQualifier(value = "method", extendedValue = "ev method")
    String sayHello(
            /*@MyCompileTimeInheritableTestQualifier(value = "arg", extendedValue = "ev arg")*/ String arg);

    default void voidMethodWithNoArgs() {
    }

    default void voidMethodWithAnnotatedPrimitiveIntArg(
            /*@MyCompileTimeInheritableTestQualifier*/ int a) {
    }

    default int intMethodWithPrimitiveBooleanArg
            (boolean b) {
        return 1;
    }

    default byte[] byteArrayMethodWithAnnotatedPrimitiveCharArrayArg(
            /*@MyCompileTimeInheritableTestQualifier*/ char[] c) {
        return null;
    }

}
