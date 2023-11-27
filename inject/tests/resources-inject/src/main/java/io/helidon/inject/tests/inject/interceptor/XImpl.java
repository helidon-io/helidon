/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.inject.interceptor;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

import io.helidon.inject.service.Injection;
import io.helidon.inject.tests.inject.ClassNamedX;
import io.helidon.inject.tests.plain.interceptor.IA;
import io.helidon.inject.tests.plain.interceptor.IB;
import io.helidon.inject.tests.plain.interceptor.InterceptorBasedAnno;

/**
 * This test case is applying {@link InterceptorBasedAnno} (an {@link io.helidon.inject.service.Interception.Trigger})
 * <p>
 * Also note that interception was triggered by the presence of the {@link TestNamed} and {@link InterceptorBasedAnno} triggers.
 */
@Injection.Singleton
@Injection.ClassNamed(ClassNamedX.class)
@TestNamed("TestNamed-ClassX")
@Injection.ExternalContracts(value = Closeable.class)
@SuppressWarnings("unused")
public class XImpl implements IA, IB, Closeable {
    @Injection.Inject
    @InterceptorBasedAnno("<init>")
    // will be intercepted
    XImpl(Optional<IA> optionalIA) {
        assert (optionalIA.isEmpty());
    }

    // a decoy constructor (and will not be intercepted)
    @InterceptorBasedAnno("IA2")
    XImpl(IB ib) {
        throw new IllegalStateException("should not be here");
    }

    @Override
    // will be intercepted
    public void methodIA1() {
    }

    @InterceptorBasedAnno("IA2")
    @Override
    // will be intercepted
    public void methodIA2() {
    }

    @Injection.Named("methodIB")
    @InterceptorBasedAnno("IBSubAnno")
    @Override
    // will be intercepted
    public void methodIB(@Injection.Named("arg1") String val) {
    }

    @Injection.Named("methodIB2")
    @InterceptorBasedAnno("IBSubAnno")
    @Override
    // will be intercepted
    public String methodIB2(@Injection.Named("arg1") String val) {
        return val;
    }

    @InterceptorBasedAnno
    @Override
    public void close() throws IOException, RuntimeException {
        throw new IOException("forced");
    }

    // will be intercepted
    @InterceptorBasedAnno
    public long methodX(String arg1,
                        int arg2,
                        boolean arg3) throws IOException, RuntimeException, AssertionError {
        return 101;
    }

    // test of package private
    // will be intercepted
    @InterceptorBasedAnno
    String methodY() {
        return "methodY";
    }

    // test of protected
    // will be intercepted
    @InterceptorBasedAnno
    protected String methodZ() {
        return "methodZ";
    }

    // test of protected
    // will be intercepted
    @InterceptorBasedAnno
    protected void throwRuntimeException() {
        throw new RuntimeException("forced");
    }

}
