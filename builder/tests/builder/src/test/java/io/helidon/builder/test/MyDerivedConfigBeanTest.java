/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import io.helidon.builder.test.testsubjects.MyDerivedConfigBean;
import io.helidon.builder.test.testsubjects.MyDerivedConfigBeanImpl;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class MyDerivedConfigBeanTest {

    @Test
    void testIt() {
        assertThat(sort(MyDerivedConfigBeanImpl.__metaAttributes()).toString(),
                   equalTo("{__generated={version=1}, enabled={type=boolean}, name={deprecated=false, experimental=false, "
                                   + "kind=VALUE, "
                                   + "mergeWithParent=false, provider=false, required=true, type=io.helidon.config.metadata"
                                   + ".ConfiguredOption, value=io.helidon.config.metadata.ConfiguredOption.UNCONFIGURED}, "
                                   + "port={type=int}}"));

        MyDerivedConfigBean cfg = MyDerivedConfigBeanImpl.builder().name("test").build();
        assertThat(cfg.toString(),
                   equalTo("MyDerivedConfigBean(name=test, enabled=false, port=8080)"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Map<String, ?> sort(Map<String, ?> inMap) {
        Map<String, Object> result = new TreeMap<>();
        inMap.forEach((key, value) -> {
            if (value instanceof Map) {
                Object newVal = new TreeMap<>((Map) value);
                result.put(key, newVal);
            } else if (value instanceof Collection) {
                Object newVal = new ArrayList<>((Collection) value);
                result.put(key, newVal);
            } else {
                result.put(key, value);
            }
        });
        return result;
    }

}
