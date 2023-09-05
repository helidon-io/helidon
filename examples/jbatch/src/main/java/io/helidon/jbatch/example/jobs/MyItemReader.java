/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.jbatch.example.jobs;

import java.util.StringTokenizer;

import javax.batch.api.chunk.AbstractItemReader;


/**
 * Example Item Reader.
 */
public class MyItemReader extends AbstractItemReader {

    private final StringTokenizer tokens;

    /**
     * Constructor for Item Reader.
     */
    public MyItemReader() {
        tokens = new StringTokenizer("1,2,3,4,5,6,7,8,9,10", ",");
    }

    /**
     * Perform read Item.
     * @return Stage result.
     */
    @Override
    public MyInputRecord readItem() {
        if (tokens.hasMoreTokens()) {
            return new MyInputRecord(Integer.valueOf(tokens.nextToken()));
        }
        return null;
    }
}