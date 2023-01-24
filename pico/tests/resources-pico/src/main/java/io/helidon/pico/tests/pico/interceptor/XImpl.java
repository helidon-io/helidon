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

package io.helidon.pico.tests.pico.interceptor;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

import io.helidon.pico.tests.plain.interceptor.IA;
import io.helidon.pico.tests.plain.interceptor.IB;
import io.helidon.pico.tests.plain.interceptor.InterceptorBasedAnno;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("ClassX")
public class XImpl implements IA, IB, Closeable {

    XImpl() {
        // this is the one that will be used by interception
    }

    @Inject
    public XImpl(Optional<IA> optionalIA) {
        assert (optionalIA.isEmpty());
    }

    @Override
    public void methodIA1() {
    }

    @InterceptorBasedAnno("IA2")
    @Override
    public void methodIA2() {
    }

    @Named("methodIB")
    @InterceptorBasedAnno("IBSubAnno")
    @Override
    public void methodIB(
            @Named("arg1") String val) {
    }

    @InterceptorBasedAnno
    @Override
    public void close() throws IOException, RuntimeException {
        throw new IOException("forced");
    }

    public long methodX(String arg1, int arg2, boolean arg3) throws IOException, RuntimeException, AssertionError {
        return 101;
    }

    // test of package private
    String methodY() {
        return "methodY";
    }

    // test of protected
    protected String methodZ() {
        return "methodZ";
    }

    // test of protected
    protected void throwRuntimeException() {
        throw new RuntimeException("forced");
    }

}
