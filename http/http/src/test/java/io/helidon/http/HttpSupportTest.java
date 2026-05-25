/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.parameters.Parameters;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpSupportTest {
    @Test
    void formParamsReturnsSuppliedParameters() {
        var params = Parameters.builder("form-params")
                .add("name", "value")
                .build();

        assertThat(HttpSupport.formParams(() -> Optional.of(params)), sameInstance(params));
    }

    @Test
    void formParamsReturnsEmptyParametersWhenMissing() {
        Parameters params = HttpSupport.formParams(Optional::empty);

        assertThat(params.component(), is("form-params"));
        assertThat(params.isEmpty(), is(true));
    }

    @Test
    void formParamsPreservesHttpException() {
        var cause = new HttpException("too large", Status.REQUEST_ENTITY_TOO_LARGE_413);

        var thrown = assertThrows(HttpException.class,
                                  () -> HttpSupport.formParams(() -> {
                                      throw cause;
                                  }));

        assertThat(thrown, sameInstance(cause));
    }

    @Test
    void formParamsWrapsRuntimeExceptionAsBadRequest() {
        var cause = new IllegalStateException("broken");

        var thrown = assertThrows(BadRequestException.class,
                                  () -> HttpSupport.formParams(() -> {
                                      throw cause;
                                  }));

        assertThat(thrown.getMessage(), is("Failed to read form parameters."));
        assertThat(thrown.getCause(), sameInstance(cause));
    }

    @Test
    void lazyFormParamsReadsOnceOnAccess() {
        var reads = new AtomicInteger();
        var params = Parameters.builder("form-params")
                .add("name", "value")
                .build();

        var lazy = HttpSupport.lazyFormParams(() -> {
            reads.incrementAndGet();
            return Optional.of(params);
        });

        assertThat(reads.get(), is(0));
        assertThat(lazy.get(), sameInstance(params));
        assertThat(lazy.get(), sameInstance(params));
        assertThat(reads.get(), is(1));
    }

    @Test
    void cookieCreatesCookiePair() {
        assertThat(HttpSupport.cookie("name", "value"), is("name=value"));
    }

    @Test
    void cookieRejectsInvalidName() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> HttpSupport.cookie("bad;name", "value"));

        assertThat(thrown.getMessage(), is("Cookie parameter name has invalid value."));
    }

    @Test
    void cookieRejectsInvalidValue() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> HttpSupport.cookie("session", "bad;value"));

        assertThat(thrown.getMessage(), is("Cookie parameter session has invalid value."));
    }

    @Test
    void cookieHeaderJoinsCookiePairs() {
        assertThat(HttpSupport.cookieHeader(List.of("first=one", "second=two")), is("first=one; second=two"));
    }

    @Test
    void paramValueReturnsFirstValue() {
        var params = Parameters.builder("test")
                .add("name", "value")
                .build();

        assertThat(HttpSupport.paramValue(params, "name", "Test parameter"), is("value"));
    }

    @Test
    void paramValueReturnsEmptyStringWhenPresentWithoutValues() {
        var params = Parameters.builder("test")
                .add("name")
                .build();

        assertThat(HttpSupport.paramValue(params, "name", "Test parameter"), is(""));
    }

    @Test
    void paramValueFailsWhenMissing() {
        var params = Parameters.empty("test");

        var thrown = assertThrows(BadRequestException.class,
                                  () -> HttpSupport.paramValue(params, "name", "Test parameter"));

        assertThat(thrown.getMessage(), is("Test parameter name is not present in the request."));
    }

    @Test
    void paramOptionalValueReturnsEmptyStringWhenPresentWithoutValues() {
        var params = Parameters.builder("test")
                .add("name")
                .build();

        assertThat(HttpSupport.paramOptionalValue(params, "name"), is(Optional.of("")));
    }

    @Test
    void paramOptionalValueIsEmptyWhenMissing() {
        var params = Parameters.empty("test");

        assertThat(HttpSupport.paramOptionalValue(params, "name"), is(Optional.empty()));
    }
}
