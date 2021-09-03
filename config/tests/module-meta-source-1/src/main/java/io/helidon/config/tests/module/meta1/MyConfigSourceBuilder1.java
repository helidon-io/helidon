/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config.tests.module.meta1;

import io.helidon.common.Builder;
import io.helidon.config.Config;

/**
 * Testing implementation of config source builder.
 */
public class MyConfigSourceBuilder1 implements Builder<MyConfigSource1> {

    private final MyEndpoint1 endpoint;
    private boolean myProp3;

    /**
     * Initialize builder.
     *
     * @param endpoint endpoint
     */
    private MyConfigSourceBuilder1(MyEndpoint1 endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Creates new instance of builder from props.
     *
     * @param myProp1 prop1
     * @param myProp2 prop2
     * @return new builder instance
     */
    public static MyConfigSourceBuilder1 from(String myProp1, int myProp2) {
        return new MyConfigSourceBuilder1(new MyEndpoint1(myProp1, myProp2));
    }

    /**
     * Creates new instance of builder from config.
     *
     * @param metaConfig config
     * @return new builder instance
     */
    public static MyConfigSourceBuilder1 from(Config metaConfig) {
        return from(metaConfig.get("myProp1").asString().get(),
                    metaConfig.get("myProp2").asInt().get())
                .config(metaConfig);
    }

    /**
     * Create an instance based on config.
     *
     * @param metaConfig meta config
     * @return new builder instance
     */
    public MyConfigSourceBuilder1 config(Config metaConfig) {
        metaConfig.get("myProp3").asBoolean().ifPresent(this::myProp3);
        return this;
    }

    /**
     * Set prop3.
     *
     * @param myProp3 prop3
     * @return this
     */
    public MyConfigSourceBuilder1 myProp3(boolean myProp3) {
        this.myProp3 = myProp3;
        return this;
    }

    /**
     * Creates new source instance.
     *
     * @return new source instance
     */
    public MyConfigSource1 build() {
        return new MyConfigSource1(endpoint, myProp3);
    }

}
