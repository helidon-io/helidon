/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.reactive.webserver.examples.tutorial;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.reactive.Single;
import io.helidon.reactive.media.common.ContentWriters;
import io.helidon.reactive.media.common.MessageBodyWriter;
import io.helidon.reactive.media.common.MessageBodyWriterContext;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;
import io.helidon.reactive.webserver.Service;
import io.helidon.reactive.webserver.examples.tutorial.user.User;

/**
 * Basic service for comments.
 */
public class CommentService implements Service {

    private static final String ROOM_PATH_ID = "room-id";

    private final ConcurrentHashMap<String, List<Comment>> data = new ConcurrentHashMap<>();

    @Override
    public void update(Routing.Rules routingRules) {
        routingRules.get((req, res) -> {
                    // Register a publisher for comment
                    res.registerWriter(new CommentWriter());
                    req.next();
                })
                    .get("/{" + ROOM_PATH_ID + "}", this::getComments)
                    .post("/{" + ROOM_PATH_ID + "}", this::addComment);
    }

    private void getComments(ServerRequest req, ServerResponse resp) {
        String roomId = req.path().param(ROOM_PATH_ID);
        //resp.headers().contentType(HttpMediaType.PLAINTEXT_UTF_8);
        List<Comment> comments = getComments(roomId);
        resp.send(comments);
    }

    /**
     * Returns all comments for the room or an empty list if room doesn't exist.
     *
     * @param roomId a room ID
     * @return a list of comments
     */
    public List<Comment> getComments(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return Collections.emptyList();
        }

        List<Comment> result = data.get(roomId);
        return result == null ? Collections.emptyList() : result;
    }

    private void addComment(ServerRequest req, ServerResponse resp) {
        String roomId = req.path().param(ROOM_PATH_ID);
        User user = req.context()
                       .get(User.class)
                       .orElse(User.ANONYMOUS);
        req.content()
           .as(String.class)
           .thenAccept(msg -> addComment(roomId, user, msg))
           .thenRun(resp::send)
           .exceptionally(t -> {
               req.next(t);
               return null;
           });
    }

    /**
     * Adds new comment into the comment-room.
     *
     * @param roomName a name of the comment-room
     * @param user     a user who provides the comment
     * @param message  a comment message
     */
    public void addComment(String roomName, User user, String message) {
        if (user == null) {
            user = User.ANONYMOUS;
        }
        List<Comment> comments = data.computeIfAbsent(roomName, k -> Collections.synchronizedList(new ArrayList<>()));
        comments.add(new Comment(user, message));
    }

    /**
     * Represents a single comment.
     */
    public static class Comment {
        private final User user;
        private final String message;

        Comment(User user, String message) {
            this.user = user;
            this.message = message;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            if (user != null) {
                result.append(user.getAlias());
            }
            result.append(": ");
            result.append(message);
            return result.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Comment)) {
                return false;
            }

            Comment comment = (Comment) o;

            if (user != null ? !user.equals(comment.user) : comment.user != null) {
                return false;
            }
            return message != null ? message.equals(comment.message) : comment.message == null;
        }

        @Override
        public int hashCode() {
            int result = user != null ? user.hashCode() : 0;
            result = 31 * result + (message != null ? message.hashCode() : 0);
            return result;
        }
    }

    private static final class CommentWriter implements MessageBodyWriter<List<Comment>> {
        private static final Http.HeaderValue CONTENT_TYPE_UTF_8 = Http.Header.createCached(Http.Header.CONTENT_TYPE,
                                                                                            HttpMediaType.PLAINTEXT_UTF_8.text());

        @Override
        public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
            if (List.class.isAssignableFrom(type.rawType())) {
                if (context.headers().isAccepted(MediaTypes.TEXT_PLAIN)) {
                    return PredicateResult.SUPPORTED;
                } else {
                    return PredicateResult.COMPATIBLE;
                }
            }
            return PredicateResult.NOT_SUPPORTED;
        }

        @Override
        public Flow.Publisher<DataChunk> write(Single<? extends List<Comment>> single,
                                               GenericType<? extends List<Comment>> type,
                                               MessageBodyWriterContext context) {
            context.headers().setIfAbsent(CONTENT_TYPE_UTF_8);
            return single.flatMap(this::publish);
        }

        Flow.Publisher<DataChunk> publish(List<Comment> comments) {
            String str = comments.stream()
                    .map(Comment::toString)
                    .collect(Collectors.joining("\n"));
            return ContentWriters.writeCharSequence(str + "\n", StandardCharsets.UTF_8);
        }

    }
}
