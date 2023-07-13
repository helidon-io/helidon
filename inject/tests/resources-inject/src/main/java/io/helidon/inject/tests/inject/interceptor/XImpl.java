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

package io.helidon.inject.tests.inject.interceptor;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

import io.helidon.inject.api.InterceptedTrigger;
import io.helidon.inject.api.ClassNamed;
import io.helidon.inject.api.ExternalContracts;
import io.helidon.inject.tests.inject.ClassNamedX;
import io.helidon.inject.tests.plain.interceptor.IA;
import io.helidon.inject.tests.plain.interceptor.IB;
import io.helidon.inject.tests.plain.interceptor.InterceptorBasedAnno;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * This test case is applying {@link InterceptorBasedAnno} (an {@link InterceptedTrigger}) using the no-arg
 * constructor approach - all methods are intercepted.
 * <p>
 * Also note that interception was triggered by the presence of the {@link TestNamed} and {@link InterceptorBasedAnno} triggers.
 */
@Singleton
@ClassNamed(ClassNamedX.class)
@TestNamed("TestNamed-ClassX")
@ExternalContracts(value = Closeable.class, moduleNames = {"test1", "test2"})
@SuppressWarnings("unused")
public class XImpl implements IA, IB, Closeable {

    XImpl() {
    }

    @Inject
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

    @Named("methodIB")
    @InterceptorBasedAnno("IBSubAnno")
    @Override
    // will be intercepted
    public void methodIB(@Named("arg1") String val) {
    }

    @Named("methodIB2")
    @InterceptorBasedAnno("IBSubAnno")
    @Override
    // will be intercepted
    public String methodIB2(@Named("arg1") String val) {
        return val;
    }

    @InterceptorBasedAnno
    @Override
    public void close() throws IOException, RuntimeException {
        throw new IOException("forced");
    }

    // will be intercepted
    public long methodX(String arg1,
                        int arg2,
                        boolean arg3) throws IOException, RuntimeException, AssertionError {
        return 101;
    }

    // test of package private
    // will be intercepted
    String methodY() {
        return "methodY";
    }

    // test of protected
    // will be intercepted
    protected String methodZ() {
        return "methodZ";
    }

    // test of protected
    // will be intercepted
    protected void throwRuntimeException() {
        throw new RuntimeException("forced");
    }

}
