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

import io.helidon.pico.ExternalContracts;
import io.helidon.pico.tests.plain.interceptor.IA;
import io.helidon.pico.tests.plain.interceptor.IB;
import io.helidon.pico.tests.plain.interceptor.InterceptorBasedAnno;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * This test case is applying {@link InterceptorBasedAnno} (an {@link io.helidon.pico.InterceptedTrigger}) on only
 * the {@link IB} interface.
 * <p>
 * Also note that interception was triggered by the presence of the {@link InterceptorBasedAnno} trigger.
 */
@Singleton
@Named("ClassY")
@ExternalContracts(value = Closeable.class, moduleNames = {"test1", "test2"})
@SuppressWarnings("unused")
public class YImpl implements IB, Closeable {

//    YImpl() {
//    }

    @Inject
    // will be intercepted
    YImpl(Optional<IA> optionalIA) {
        assert (optionalIA.isPresent() && optionalIA.get().getClass().getName().contains("XImpl"));
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
    // will be intercepted
    public void close() throws IOException, RuntimeException {
        throw new IOException("forced");
    }

    // will not be intercepted
    public long methodX(String arg1,
                        int arg2,
                        boolean arg3) throws IOException, RuntimeException, AssertionError {
        return 101;
    }

    // will not be intercepted
    String methodY() {
        return "methodY";
    }

    // will not be intercepted
    protected String methodZ() {
        return "methodZ";
    }

    // will not be intercepted
    protected void throwRuntimeException() {
        throw new RuntimeException("forced");
    }

}
