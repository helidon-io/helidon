/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.comments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.helidon.common.http.MediaType;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Basic service for comments.
 */
@SuppressWarnings("Duplicates")
public class CommentsService implements Service {

    private final ConcurrentHashMap<String, List<Comment>> topicsAndComments = new ConcurrentHashMap<>();

    @Override
    public void update(Routing.Rules routingRules) {
        routingRules
                .get("/{topic}", this::handleListComments)
                .post("/{topic}", this::handleAddComment);
    }

    private void handleListComments(ServerRequest req, ServerResponse resp) {
        String topic = req.path().param("topic");
        resp.headers().contentType(MediaType.TEXT_PLAIN.withCharset("UTF-8"));
        resp.send(listComments(topic));
    }

    private void handleAddComment(ServerRequest req, ServerResponse resp) {
        String topic = req.path().param("topic");

        String userName = req.context().get("user", String.class)
                .orElse("anonymous");

        req.content()
           .as(String.class)
           .thenAccept(msg -> addComment(msg, userName, topic))
           .thenRun(resp::send)
           .exceptionally(t -> {
               req.next(t);
               return null;
           });
    }

    /**
     * Adds new comment into the comment-room.
     *
     * @param message a comment message
     * @param fromUser    a user alias of the comment author
     */
    void addComment(String message, String fromUser, String toTopic) {
        ProfanityDetector.detectProfanity(message);
        if (fromUser == null) {
            fromUser = "anonymous";
        }
        List<Comment> comments =
                topicsAndComments.computeIfAbsent(toTopic, k -> Collections.synchronizedList(new ArrayList<>()));
        comments.add(new Comment(fromUser, message));
    }

    /**
     * List all comments in original order as a string with single comment on the line.
     *
     * @param roomName a name of the room
     * @return all comments, line-by-line
     */
    String listComments(String roomName) {
        List<Comment> comments = topicsAndComments.get(roomName);
        if (comments != null) {
            return comments.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining("\n"));
        } else {
            return "";
        }
    }

    private static class Comment {
        private final String userName;
        private final String message;

        Comment(String userName, String message) {
            this.userName = userName;
            this.message = message;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            if (userName != null) {
                result.append(userName);
            }
            result.append(": ");
            result.append(message);
            return result.toString();
        }
    }
}
