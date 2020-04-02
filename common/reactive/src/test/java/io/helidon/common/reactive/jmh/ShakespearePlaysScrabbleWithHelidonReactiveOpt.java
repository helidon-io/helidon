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

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * Shakespeare plays Scrabble with Helidon Reactive.
 * Based on the work of Jose Paumard (C) 2019
 * https://github.com/JosePaumard/jdk8-stream-rx-comparison-reloaded
 */
public class ShakespearePlaysScrabbleWithHelidonReactiveOpt extends ShakespearePlaysScrabble {


    public static void main(String[] args) throws Throwable {
        ShakespearePlaysScrabbleWithHelidonReactiveOpt s = new ShakespearePlaysScrabbleWithHelidonReactiveOpt();
        s.init();
        System.out.println(s.measureThroughput());

        Options opt = new OptionsBuilder()
                .include(ShakespearePlaysScrabbleWithHelidonReactiveOpt.class.getSimpleName())
                .forks(1)
                .warmupIterations(5)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(5)
                .measurementTime(TimeValue.seconds(1))
                .timeUnit(TimeUnit.MILLISECONDS)
                .mode(Mode.SampleTime)
                .build();

        new Runner(opt).run();
    }

    static Multi<Integer> chars(String s) {
        return Multi.range(0, s.length()).map(idx -> (int)s.charAt(idx));
    }

    static <T> T get(Single<T> source) {
        try {
            return source.get();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unused")
    @Benchmark
    public List<Entry<Integer, List<String>>> measureThroughput() throws Exception {

        //  to compute the score of a given word
        Function<Integer, Integer> scoreOfALetter = letter -> letterScores[letter - 'a'];

        // score of the same letters in a word
        Function<Entry<Integer, MutableLong>, Integer> letterScore =
                entry ->
                        letterScores[entry.getKey() - 'a'] *
                        Integer.min(
                                (int)entry.getValue().get(),
                                scrabbleAvailableLetters[entry.getKey() - 'a']
                            )
                    ;


        Function<String, Multi<Integer>> toIntegerFlowable =
                ShakespearePlaysScrabbleWithHelidonReactiveOpt::chars;

        // Histogram of the letters in a given word
        Function<String, Single<HashMap<Integer, MutableLong>>> histoOfLetters =
                word -> toIntegerFlowable.apply(word)
                            .collect(
                                HashMap::new,
                                (HashMap<Integer, MutableLong> map, Integer value) ->
                                    {
                                        MutableLong newValue = map.get(value) ;
                                        if (newValue == null) {
                                            newValue = new MutableLong();
                                            map.put(value, newValue);
                                        }
                                        newValue.incAndSet();
                                    }

                            ) ;

        // number of blanks for a given letter
        Function<Entry<Integer, MutableLong>, Long> blank =
                entry ->
                        Long.max(
                            0L,
                            entry.getValue().get() -
                            scrabbleAvailableLetters[entry.getKey() - 'a']
                        )
                    ;

        // number of blanks for a given word
        Function<String, Single<Long>> nBlanks =
                word ->
                            histoOfLetters.apply(word)
                            .flatMapIterable(HashMap::entrySet)
                            .map(blank)
                            .reduce(Long::sum)
                    ;


        // can a word be written with 2 blanks?
        Function<String, Single<Boolean>> checkBlanks =
                word -> nBlanks.apply(word)
                            .map(l -> l <= 2L) ;

        // score taking blanks into account letterScore1
        Function<String, Single<Integer>> score2 =
                word ->
                        histoOfLetters.apply(word)
                            .flatMapIterable(
                                    HashMap::entrySet
                            )
                            .map(letterScore)
                            .reduce(Integer::sum)
                            ;

        // Placing the word on the board
        // Building the streams of first and last letters
        Function<String, Multi<Integer>> first3 =
                word -> chars(word).limit(3) ;
        Function<String, Multi<Integer>> last3 =
                word -> chars(word).skip(3) ;


        // Stream to be maxed
        Function<String, Multi<Integer>> toBeMaxed =
            word -> Multi.concat(first3.apply(word), last3.apply(word))
            ;

        // Bonus for double letter
        Function<String, Single<Integer>> bonusForDoubleLetter =
            word -> toBeMaxed.apply(word)
                        .map(scoreOfALetter)
                    .reduce(Integer::max)
                    ;

        // score of the word put on the board
        Function<String, Single<Integer>> score3 =
            word ->
                Multi.concat(
                    Multi.from(score2.apply(word)),
                    Multi.from(bonusForDoubleLetter.apply(word))
                )
                .reduce(Integer::sum)
                .map(v -> v * 2 + (word.length() == 7 ? 50 : 0))
                ;

        Function<Function<String, Single<Integer>>, Single<TreeMap<Integer, List<String>>>> buildHistoOnScore =
                score -> Multi.from(shakespeareWords)
                                .filter(scrabbleWords::contains)
                                .filter(word -> get(checkBlanks.apply(word)))
                                .collect(
                                    () -> new TreeMap<Integer, List<String>>(Comparator.reverseOrder()),
                                    (TreeMap<Integer, List<String>> map, String word) -> {
                                        Integer key = get(score.apply(word)) ;
                                        List<String> list = map.get(key) ;
                                        if (list == null) {
                                            list = new ArrayList<>() ;
                                            map.put(key, list) ;
                                        }
                                        list.add(word) ;
                                    }
                                ) ;

        // best key / value pairs
        List<Entry<Integer, List<String>>> finalList2 =
                    buildHistoOnScore.apply(score3)
                    .flatMapIterable(
                            TreeMap::entrySet
                    )
                    .limit(3)
                    .collect(
                            (Supplier<ArrayList<Entry<Integer, List<String>>>>) ArrayList::new,
                            ArrayList::add
                    )
                    .get() ;


//        System.out.println(finalList2);

        return finalList2 ;
    }
}