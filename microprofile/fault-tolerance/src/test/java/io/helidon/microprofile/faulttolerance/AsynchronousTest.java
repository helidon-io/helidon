/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import java.util.concurrent.Future;

import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Class AsynchronousTest.
 */
public class AsynchronousTest extends FaultToleranceTest {

    @Test
    public void testAsync() throws Exception {
        AsynchronousBean bean = newBean(AsynchronousBean.class);
        assertThat(bean.getCalled(), is(false));
        Future<String> future = bean.async();
        future.get();
        assertThat(bean.getCalled(), is(true));
    }

    @Test
    public void testAsyncWithFallback() throws Exception {
        AsynchronousBean bean = newBean(AsynchronousBean.class);
        assertThat(bean.getCalled(), is(false));
        Future<String> future = bean.asyncWithFallback();
        String value = future.get();
        assertThat(bean.getCalled(), is(true));
        assertThat(value, is("fallback"));
    }

    @Test
    public void testAsyncNoGet() throws Exception {
        AsynchronousBean bean = newBean(AsynchronousBean.class);
        assertThat(bean.getCalled(), is(false));
        Future<String> future = bean.async();
        while (!future.isDone()) {
            Thread.sleep(100);
        }
        assertThat(bean.getCalled(), is(true));
    }

    @Test
    public void testNotAsync() throws Exception {
        AsynchronousBean bean = newBean(AsynchronousBean.class);
        assertThat(bean.getCalled(), is(false));
        Future<String> future = bean.notAsync();
        assertThat(bean.getCalled(), is(true));
        future.get();
    }

    @Test
    public void testAsyncError() throws Exception {
        assertThrows(FaultToleranceDefinitionException.class, () -> {
            AsynchronousBean bean = newBean(AsynchronousBean.class);
            bean.asyncError();
        });
    }
}
