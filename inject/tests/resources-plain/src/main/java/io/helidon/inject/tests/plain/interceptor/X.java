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

package io.helidon.inject.tests.plain.interceptor;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

import io.helidon.inject.service.Injection;

@Injection.Singleton
@Injection.Named("ClassX")
public class X implements IA, IB, Closeable {
    @Injection.Inject
    public X(Optional<IA> optionalIA) {
        assert (optionalIA.isEmpty());
    }

    @Override
    public void methodIA1() {
    }

    @InterceptorBasedAnno("IA2")
    @Override
    public void methodIA2() {
    }

    @Injection.Named("methodIB2")
    @InterceptorBasedAnno("IBSubAnno")
    @Override
    public String methodIB2(@Injection.Named("arg1") String val) {
        return val;
    }

    @Injection.Named("methodIB")
    @InterceptorBasedAnno("IBSubAnno")
    @Override
    public void methodIB(@Injection.Named("arg1") String val) {
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
