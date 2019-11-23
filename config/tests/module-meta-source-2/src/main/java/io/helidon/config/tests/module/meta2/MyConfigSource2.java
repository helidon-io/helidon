/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.tests.module.meta2;

import java.util.Objects;
import java.util.Optional;

import io.helidon.config.ConfigException;
import io.helidon.config.objectmapping.Value;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;

/**
 * Testing implementation of config source.
 */
public class MyConfigSource2 implements ConfigSource {

    private final MyEndpoint2 endpoint;
    private final boolean myProp3;

    /**
     * Initialize source.
     *
     * @param endpoint endpoint
     * @param myProp3  prop3
     */
    public MyConfigSource2(MyEndpoint2 endpoint,
                           boolean myProp3) {
        this.endpoint = endpoint;
        this.myProp3 = myProp3;
    }

    /**
     * Creates new source from props.
     *
     * @param myProp1 prop1
     * @param myProp2 prop2
     * @param myProp3 prop3
     * @return new source instance
     */
    public static MyConfigSource2 from(@Value(key = "myProp1") String myProp1,
                                       @Value(key = "myProp2") int myProp2,
                                       @Value(key = "myProp3") boolean myProp3) {
        return new MyConfigSource2(new MyEndpoint2(myProp1, myProp2), myProp3);
    }

    @Override
    public Optional<ConfigNode.ObjectNode> load() throws ConfigException {
        return Optional.of(ConfigNode.ObjectNode.builder()
                                   .addValue(endpoint.getMyProp1(), Objects.toString(endpoint.getMyProp2()))
                                   .addValue("enabled", Objects.toString(myProp3))
                                   .build());
    }

    @Override
    public String toString() {
        return "MyConfigSource2{"
                + "endpoint=" + endpoint
                + ", myProp3=" + myProp3
                + '}';
    }
}
