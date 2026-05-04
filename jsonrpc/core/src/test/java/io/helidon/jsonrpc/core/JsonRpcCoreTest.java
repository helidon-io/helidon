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
package io.helidon.jsonrpc.core;

import java.time.Duration;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.json.binding.Json;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class JsonRpcCoreTest {

    @Test
    void testJsonUtilUsesHelidonJsonBinding() {
        JsonObject jsonObject = JsonUtil.toJsonObject(new StartStopResult("RUNNING"));
        StartStopResult actual = JsonUtil.fromJson(jsonObject, StartStopResult.class);

        assertThat(jsonObject.stringValue("status").orElseThrow(), is("RUNNING"));
        assertThat(actual.status(), is("RUNNING"));
    }

    @Test
    void testParamsSupportHelidonJsonAccessAndBinding() {
        JsonRpcParams objectParams = JsonRpcParams.create(JsonObject.builder()
                                                                    .set("when", "NOW")
                                                                    .set("duration", "PT0S")
                                                                    .build());
        StartStopParams params = objectParams.as(StartStopParams.class);

        assertThat(objectParams.getString("when"), is("NOW"));
        assertThat(params.when(), is("NOW"));
        assertThat(params.duration(), is(Duration.ZERO));
    }

    @Test
    void testResultAndErrorSupportHelidonJsonValues() {
        JsonRpcResult arrayResult = JsonRpcResult.create(JsonArray.create(JsonString.create("ready"),
                                                                          JsonNumber.create(7)));
        JsonRpcResult objectResult = JsonRpcResult.create(JsonObject.builder()
                                                                    .set("status", "RUNNING")
                                                                    .build());
        JsonRpcResult scalarResult = JsonRpcResult.create(JsonNumber.create(45));
        JsonRpcError error = JsonRpcError.create(42, "failure", JsonUtil.toJsonObject(new ErrorData("alpha", 7)));
        StartStopResult result = objectResult.as(StartStopResult.class);
        ErrorData errorData = error.dataAs(ErrorData.class).orElseThrow();

        assertThat(arrayResult.getString(0), is("ready"));
        assertThat(arrayResult.get(1).asNumber().intValue(), is(7));
        assertThat(result.status(), is("RUNNING"));
        assertThat(scalarResult.as(Integer.class), is(45));
        assertThat(errorData.name(), is("alpha"));
        assertThat(errorData.count(), is(7));
    }

    @Json.Entity
    record StartStopParams(String when, Duration duration) {
    }

    @Json.Entity
    record StartStopResult(String status) {
    }

    @Json.Entity
    record ErrorData(String name, int count) {
    }
}
