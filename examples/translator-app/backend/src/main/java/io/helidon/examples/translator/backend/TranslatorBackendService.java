/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.examples.translator.backend;

import java.util.HashMap;
import java.util.Map;

import io.helidon.webserver.BadRequestException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Translator backend service.
 */
public class TranslatorBackendService implements Service {

    private static final String CZECH = "czech";
    private static final String SPANISH = "spanish";
    private static final String CHINESE = "chinese";
    private static final String HINDI = "hindi";
    private static final String ITALIAN = "italian";
    private static final String FRENCH = "french";

    private static final String SEPARATOR = ".";
    private static final Map<String, String> TRANSLATED_WORDS_REPOSITORY = new HashMap<>();

    static {
        //translation for word "cloud"
        TRANSLATED_WORDS_REPOSITORY.put("cloud" + SEPARATOR + CZECH, "oblak");
        TRANSLATED_WORDS_REPOSITORY.put("cloud" + SEPARATOR + SPANISH, "nube");
        TRANSLATED_WORDS_REPOSITORY.put("cloud" + SEPARATOR + CHINESE, "云");
        TRANSLATED_WORDS_REPOSITORY.put("cloud" + SEPARATOR + HINDI, "बादल");
        TRANSLATED_WORDS_REPOSITORY.put("cloud" + SEPARATOR + ITALIAN, "nube");
        TRANSLATED_WORDS_REPOSITORY.put("cloud" + SEPARATOR + FRENCH, "nuage");

        //one two three four five six seven eight nine ten
        //jedna dvě tři čtyři pět šest sedm osm devět deset
        //uno dos tres cuatro cinco seis siete ocho nueve diez
        //一二三四五六七八九十
        //एक दो तीन चार पांच छ सात आठ नौ दस
        // uno due tre quattro cinque sei sette otto nove dieci
        // un deux trois quatre cinq six sept huit neuf dix

        //translation for word "one"
        TRANSLATED_WORDS_REPOSITORY.put("one" + SEPARATOR + CZECH, "jedna");
        TRANSLATED_WORDS_REPOSITORY.put("one" + SEPARATOR + SPANISH, "uno");
        TRANSLATED_WORDS_REPOSITORY.put("one" + SEPARATOR + CHINESE, "一");
        TRANSLATED_WORDS_REPOSITORY.put("one" + SEPARATOR + HINDI, "एक");
        TRANSLATED_WORDS_REPOSITORY.put("one" + SEPARATOR + ITALIAN, "uno");
        TRANSLATED_WORDS_REPOSITORY.put("one" + SEPARATOR + FRENCH, "un");
        //translation for word "two"
        TRANSLATED_WORDS_REPOSITORY.put("two" + SEPARATOR + CZECH, "dvě");
        TRANSLATED_WORDS_REPOSITORY.put("two" + SEPARATOR + SPANISH, "dos");
        TRANSLATED_WORDS_REPOSITORY.put("two" + SEPARATOR + CHINESE, "二");
        TRANSLATED_WORDS_REPOSITORY.put("two" + SEPARATOR + HINDI, "दो");
        TRANSLATED_WORDS_REPOSITORY.put("two" + SEPARATOR + ITALIAN, "due");
        TRANSLATED_WORDS_REPOSITORY.put("two" + SEPARATOR + FRENCH, "deux");
        //translation for word "three"
        TRANSLATED_WORDS_REPOSITORY.put("three" + SEPARATOR + CZECH, "tři");
        TRANSLATED_WORDS_REPOSITORY.put("three" + SEPARATOR + SPANISH, "tres");
        TRANSLATED_WORDS_REPOSITORY.put("three" + SEPARATOR + CHINESE, "三");
        TRANSLATED_WORDS_REPOSITORY.put("three" + SEPARATOR + HINDI, "तीन");
        TRANSLATED_WORDS_REPOSITORY.put("three" + SEPARATOR + ITALIAN, "tre");
        TRANSLATED_WORDS_REPOSITORY.put("three" + SEPARATOR + FRENCH, "trois");
        //translation for word "four"
        TRANSLATED_WORDS_REPOSITORY.put("four" + SEPARATOR + CZECH, "čtyři");
        TRANSLATED_WORDS_REPOSITORY.put("four" + SEPARATOR + SPANISH, "cuatro");
        TRANSLATED_WORDS_REPOSITORY.put("four" + SEPARATOR + CHINESE, "四");
        TRANSLATED_WORDS_REPOSITORY.put("four" + SEPARATOR + HINDI, "चार");
        TRANSLATED_WORDS_REPOSITORY.put("four" + SEPARATOR + ITALIAN, "quattro");
        TRANSLATED_WORDS_REPOSITORY.put("four" + SEPARATOR + FRENCH, "quatre");
        //translation for word "five"
        TRANSLATED_WORDS_REPOSITORY.put("five" + SEPARATOR + CZECH, "pět");
        TRANSLATED_WORDS_REPOSITORY.put("five" + SEPARATOR + SPANISH, "cinco");
        TRANSLATED_WORDS_REPOSITORY.put("five" + SEPARATOR + CHINESE, "五");
        TRANSLATED_WORDS_REPOSITORY.put("five" + SEPARATOR + HINDI, "पांच");
        TRANSLATED_WORDS_REPOSITORY.put("five" + SEPARATOR + ITALIAN, "cinque");
        TRANSLATED_WORDS_REPOSITORY.put("five" + SEPARATOR + FRENCH, "cinq");
        //translation for word "six"
        TRANSLATED_WORDS_REPOSITORY.put("six" + SEPARATOR + CZECH, "šest");
        TRANSLATED_WORDS_REPOSITORY.put("six" + SEPARATOR + SPANISH, "seis");
        TRANSLATED_WORDS_REPOSITORY.put("six" + SEPARATOR + CHINESE, "六");
        TRANSLATED_WORDS_REPOSITORY.put("six" + SEPARATOR + HINDI, "छ");
        TRANSLATED_WORDS_REPOSITORY.put("six" + SEPARATOR + ITALIAN, "sei");
        TRANSLATED_WORDS_REPOSITORY.put("six" + SEPARATOR + FRENCH, "six");
        //translation for word "seven"
        TRANSLATED_WORDS_REPOSITORY.put("seven" + SEPARATOR + CZECH, "sedm");
        TRANSLATED_WORDS_REPOSITORY.put("seven" + SEPARATOR + SPANISH, "siete");
        TRANSLATED_WORDS_REPOSITORY.put("seven" + SEPARATOR + CHINESE, "七");
        TRANSLATED_WORDS_REPOSITORY.put("seven" + SEPARATOR + HINDI, "सात");
        TRANSLATED_WORDS_REPOSITORY.put("seven" + SEPARATOR + ITALIAN, "sette");
        TRANSLATED_WORDS_REPOSITORY.put("seven" + SEPARATOR + FRENCH, "sept");
        //translation for word "eight"
        TRANSLATED_WORDS_REPOSITORY.put("eight" + SEPARATOR + CZECH, "osm");
        TRANSLATED_WORDS_REPOSITORY.put("eight" + SEPARATOR + SPANISH, "ocho");
        TRANSLATED_WORDS_REPOSITORY.put("eight" + SEPARATOR + CHINESE, "八");
        TRANSLATED_WORDS_REPOSITORY.put("eight" + SEPARATOR + HINDI, "आठ");
        TRANSLATED_WORDS_REPOSITORY.put("eight" + SEPARATOR + ITALIAN, "otto");
        TRANSLATED_WORDS_REPOSITORY.put("eight" + SEPARATOR + FRENCH, "huit");
        //translation for word "nine"
        TRANSLATED_WORDS_REPOSITORY.put("nine" + SEPARATOR + CZECH, "devět");
        TRANSLATED_WORDS_REPOSITORY.put("nine" + SEPARATOR + SPANISH, "nueve");
        TRANSLATED_WORDS_REPOSITORY.put("nine" + SEPARATOR + CHINESE, "九");
        TRANSLATED_WORDS_REPOSITORY.put("nine" + SEPARATOR + HINDI, "नौ");
        TRANSLATED_WORDS_REPOSITORY.put("nine" + SEPARATOR + ITALIAN, "nove");
        TRANSLATED_WORDS_REPOSITORY.put("nine" + SEPARATOR + FRENCH, "neuf");
        //translation for word "ten"
        TRANSLATED_WORDS_REPOSITORY.put("ten" + SEPARATOR + CZECH, "deset");
        TRANSLATED_WORDS_REPOSITORY.put("ten" + SEPARATOR + SPANISH, "diez");
        TRANSLATED_WORDS_REPOSITORY.put("ten" + SEPARATOR + CHINESE, "十");
        TRANSLATED_WORDS_REPOSITORY.put("ten" + SEPARATOR + HINDI, "दस");
        TRANSLATED_WORDS_REPOSITORY.put("ten" + SEPARATOR + ITALIAN, "dieci");
        TRANSLATED_WORDS_REPOSITORY.put("ten" + SEPARATOR + FRENCH, "dix");
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get(this::getText);
    }

    private void getText(ServerRequest request, ServerResponse response) {

        String query = request.queryParams().first("q")
                .orElseThrow(() -> new BadRequestException("missing query parameter 'q'"));
        String language = request.queryParams().first("lang")
                .orElseThrow(() -> new BadRequestException("missing query parameter 'lang'"));
        String translation;
        switch (language) {
        case CZECH:
            translation = TRANSLATED_WORDS_REPOSITORY.get(query + SEPARATOR + CZECH);
            break;
        case SPANISH:
            translation = TRANSLATED_WORDS_REPOSITORY.get(query + SEPARATOR + SPANISH);
            break;
        case CHINESE:
            translation = TRANSLATED_WORDS_REPOSITORY.get(query + SEPARATOR + CHINESE);
            break;
        case HINDI:
            translation = TRANSLATED_WORDS_REPOSITORY.get(query + SEPARATOR + HINDI);
            break;
        case ITALIAN:
            translation = TRANSLATED_WORDS_REPOSITORY.get(query + SEPARATOR + ITALIAN);
            break;
        case FRENCH:
            translation = TRANSLATED_WORDS_REPOSITORY.get(query + SEPARATOR + FRENCH);
            break;
        default:
            response.status(404)
                    .send(String.format(
                            "Language '%s' not in supported. Supported languages: %s, %s, %s, %s.",
                            language,
                            CZECH, SPANISH, CHINESE, HINDI));
            return;
        }

        if (translation != null) {
            response.send(translation);
        } else {
            response.status(404)
                    .send(String.format("Word '%s' not in the dictionary", query));
        }
    }
}
