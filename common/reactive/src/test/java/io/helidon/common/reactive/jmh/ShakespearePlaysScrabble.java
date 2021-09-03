/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive.jmh;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.openjdk.jmh.annotations.*;

/**
 * Original copyright Jose Paumard, 2019.
 * https://github.com/JosePaumard/jdk8-stream-rx-comparison-reloaded
 */
@State(Scope.Benchmark)
public class ShakespearePlaysScrabble {

    public static final int[] letterScores = {
    // a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p,  q, r, s, t, u, v, w, x, y,  z
       1, 3, 3, 2, 1, 4, 2, 4, 1, 8, 5, 1, 3, 1, 1, 3, 10, 1, 1, 1, 1, 4, 4, 8, 4, 10} ;

    public static final int[] scrabbleAvailableLetters = {
     // a, b, c, d,  e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y, z
        9, 2, 2, 1, 12, 2, 3, 2, 9, 1, 1, 4, 2, 6, 8, 2, 1, 6, 4, 6, 4, 2, 2, 1, 2, 1} ;

    static class MutableLong {
        long value;
        long get() {
            return value;
        }

        MutableLong set(long l) {
            value = l;
            return this;
        }

        MutableLong incAndSet() {
            value++;
            return this;
        }

        MutableLong add(MutableLong other) {
            value += other.value;
            return this;
        }
    }

    public Set<String> scrabbleWords;
    public Set<String> shakespeareWords;

    @Setup
    public void init() {
        scrabbleWords = readScrabbleWords() ;
        shakespeareWords = readShakespeareWords() ;
    }


    public static Set<String> readScrabbleWords() {
        Set<String> scrabbleWords = new HashSet<>();

        try (BufferedReader bin = new BufferedReader(new InputStreamReader(ShakespearePlaysScrabble.class.getResourceAsStream("/ospd.txt"), StandardCharsets.UTF_8))) {
            String line = null;
            while ((line = bin.readLine()) != null) {
                scrabbleWords.add(line.toLowerCase());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return scrabbleWords;
    }

    public static Set<String> readShakespeareWords() {
        Set<String> shakespeareWords = new HashSet<>();

        try (BufferedReader bin = new BufferedReader(new InputStreamReader(ShakespearePlaysScrabble.class.getResourceAsStream("/words.shakespeare.txt"), StandardCharsets.UTF_8))) {
            String line = null;
            while ((line = bin.readLine()) != null) {
                shakespeareWords.add(line.toLowerCase());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return shakespeareWords;
    }

}