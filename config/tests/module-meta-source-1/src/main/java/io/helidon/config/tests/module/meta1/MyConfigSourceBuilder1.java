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

package io.helidon.config.tests.module.meta1;

import io.helidon.config.Config;
import io.helidon.config.spi.AbstractSource;
import io.helidon.config.spi.ConfigSource;

/**
 * Testing implementation of config source builder.
 */
public class MyConfigSourceBuilder1
        extends AbstractSource.Builder<MyConfigSourceBuilder1, MyEndpoint1, ConfigSource> {

    private final MyEndpoint1 endpoint;
    private boolean myProp3;

    /**
     * Initialize builder.
     *
     * @param endpoint endpoint
     */
    private MyConfigSourceBuilder1(MyEndpoint1 endpoint) {
        super(MyEndpoint1.class);
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

    @Override
    public MyConfigSourceBuilder1 config(Config metaConfig) {
        metaConfig.get("myProp3").asBoolean().ifPresent(this::myProp3);
        return super.config(metaConfig);
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

    @Override
    protected MyEndpoint1 target() {
        return endpoint;
    }

    /**
     * Creates new source instance.
     *
     * @return new source instance
     */
    public ConfigSource build() {
        return new MyConfigSource1(endpoint, myProp3);
    }

}
