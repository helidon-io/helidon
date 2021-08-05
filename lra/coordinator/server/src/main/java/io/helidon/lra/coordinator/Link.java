/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *  
 */

package io.helidon.lra.coordinator;

import java.net.URI;
import java.util.StringJoiner;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Link {

    private static final Logger LOGGER = Logger.getLogger(Link.class.getName());

    //Example: rel="status"
    static final Pattern LINK_PROP_PATTERN = Pattern.compile("\\s*(?<key>[a-zA-Z]+)=\"(?<value>[^\"]*)\"\\s*");
    static final Pattern URI_PROP_PATTERN = Pattern.compile("\\s*<(?<value>[^>]+)>\\s*");

    private URI uri;
    private String rel;
    private String title;
    private String type;

    //<http://127.0.0.1:8080/lraresource/status>; rel="status"; title="status URI"; type="text/plain"
    static Link valueOf(String linkVal) {
        String[] tokens = linkVal.split(";");
        Link link = new Link();

        for (String token : tokens) {

            Matcher uriMatcher = URI_PROP_PATTERN.matcher(token);
            if (uriMatcher.find()) {
                link.uri = URI.create(uriMatcher.group("value"));
            }
            Matcher propMatcher = LINK_PROP_PATTERN.matcher(token);
            if (propMatcher.find()) {
                String key = propMatcher.group("key");
                String value = propMatcher.group("value");
                switch (key) {
                    case "rel":
                        link.rel = value;
                        break;
                    case "title":
                        link.title = value;
                        break;
                    case "type":
                        link.type = value;
                        break;
                    default:
                        LOGGER.fine(() -> "Unexpected link property " + key + ": " + value);
                }
            }
        }
        return link;
    }

    public URI uri() {
        return uri;
    }

    public String rel() {
        return rel;
    }

    public String title() {
        return title;
    }

    public String type() {
        return type;
    }

    @Override
    public String toString() {
        return new StringJoiner("; ", "<" + this.uri.toString() + ">", "")
                .add("rel=\"" + rel + "\"")
                .add("title=\"" + title + "\"")
                .add("type=\"" + type + "\"")
                .toString();
    }
}
