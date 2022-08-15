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

package io.helidon.nima.http.encoding.brotli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.stream.Stream;

class DictionaryTable {
    static final int[] HASH_WORDS;
    static final int[] HASH_LENGTHS;
    static final int[] BUCKETS;
    static final int[] DATA;
    static final DictWord[] WORDS;

    static final int[] SIZE_BITS_BY_LENGTH = new int[] {
            0, 0, 0, 0, 10, 10, 11, 11,
            10, 10, 10, 10, 10, 9, 9, 8,
            7, 7, 8, 7, 7, 6, 6, 5,
            5, 0, 0, 0, 0, 0, 0, 0
    };

    static final int[] OFFSETS_BY_LENGTHS = new int[] {
            0, 0, 0, 0, 0, 4096, 9216, 21504,
            35840, 44032, 53248, 63488, 74752, 87040, 93696, 100864,
            104704, 106752, 108928, 113536, 115968, 118528, 119872, 121280,
            122016, 122784, 122784, 122784, 122784, 122784, 122784, 122784
    };

    static final int DATA_SIZE = 122784;

    static {
        Properties properties = new Properties();

        try {
            // the arrays are too big to be part of java classes (compiler throws "code too large" exception)
            InputStream inputStream = DictionaryTable.class.getResourceAsStream("staticDictionary.properties");
            properties.load(inputStream);

            String[] temp;
            temp = properties.get("kStaticDictionaryHashWords").toString().split(",");
            HASH_WORDS = Stream.of(temp).mapToInt(Integer::parseInt).toArray();
            temp = properties.get("kStaticDictionaryHashLengths").toString().split(",");
            HASH_LENGTHS = Stream.of(temp).mapToInt(Integer::parseInt).toArray();
            temp = properties.get("kStaticDictionaryBuckets").toString().split(",");
            BUCKETS = Stream.of(temp).mapToInt(Integer::parseInt).toArray();
            temp = properties.get("kBrotliDictionaryData").toString().split(",");
            DATA = Stream.of(temp).mapToInt(Integer::parseInt).toArray();
            temp = properties.get("kStaticDictionaryWords").toString().split(",");
            WORDS = buildDictionaryWords(temp);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot initialize brotli dictionary", e);
        }
    }

    private static DictWord[] buildDictionaryWords(String[] temp) {
        if ((temp.length / 3) != 31705) {
            throw new IllegalArgumentException("Wrong number of Dictionary word in properties file, this is a critical issue");
        }
        DictWord[] res = new DictWord[temp.length / 3];
        int resCount = 0;
        for (int i = 0; i < temp.length; i += 3) {
            res[resCount++] = new DictWord(Integer.parseInt(temp[i]),
                                           Integer.parseInt(temp[i + 1]),
                                           Integer.parseInt(temp[i + 2]));
        }
        return res;
    }

}
