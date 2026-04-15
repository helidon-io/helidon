/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.config.metadata.model;

import java.util.ArrayList;
import java.util.List;

import io.helidon.config.metadata.model.CmModel.CmOption;
import io.helidon.config.metadata.model.CmNode.CmOptionNode;
import io.helidon.config.metadata.model.CmNode.CmPathNode;
import io.helidon.config.metadata.model.CmNodeImpl.CmOptionNodeImpl;
import io.helidon.config.metadata.model.CmNodeImpl.CmPathNodeImpl;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class CmNodeImplTest {
    @Test
    void testEqualsMatchesCompareToAcrossNodeKinds() {
        var first = pathNode(null, "root.shared", "shared", List.of());
        var second = optionNode(null, "root.shared", "shared", List.of());
        var other = pathNode(null, "root.other", "other", List.of());

        assertThat(first.compareTo(second), is(0));
        assertThat(second.compareTo(first), is(0));
        assertThat(first, is(equalTo(second)));
        assertThat(second, is(equalTo(first)));
        assertThat(first.hashCode(), is(second.hashCode()));
        assertThat(first, is(not(other)));
    }

    @Test
    void testVisitTraversesDepthFirstInChildOrder() {
        var rc = new ArrayList<CmNode>();
        var root = pathNode(null, "root", "root", rc);
        var r1c = new ArrayList<CmNode>();
        var r1 = pathNode(root, "root.left", "left", r1c);
        r1c.add(optionNode(r1, "root.left.leaf", "leaf", List.of()));
        var r2 = optionNode(root, "root.right", "right", List.of());
        rc.add(r1);
        rc.add(r2);

        var events = new ArrayList<String>();
        root.visit(new CmNode.Visitor<List<String>>() {
            @Override
            public boolean visit(CmNode node, List<String> visited) {
                visited.add("visit:" + node.path());
                return true;
            }

            @Override
            public void postVisit(CmNode node, List<String> visited) {
                visited.add("post:" + node.path());
            }
        }, events);

        assertThat(events, contains(
                "visit:root",
                "visit:root.left",
                "visit:root.left.leaf",
                "post:root.left.leaf",
                "post:root.left",
                "visit:root.right",
                "post:root.right",
                "post:root"));
    }

    @Test
    void testVisitSkipsChildrenWhenVisitorReturnsFalse() {
        var rc = new ArrayList<CmNode>();
        var root = pathNode(null, "root", "root", rc);
        var r1c = new ArrayList<CmNode>();
        var r1 = pathNode(root, "root.skipped", "skipped", r1c);
        r1c.add(optionNode(r1, "root.skipped.leaf", "leaf", List.of()));
        var r2 = optionNode(root, "root.sibling", "sibling", List.of());
        rc.add(r1);
        rc.add(r2);

        var events = new ArrayList<String>();
        root.visit(new CmNode.Visitor<List<String>>() {
            @Override
            public boolean visit(CmNode node, List<String> visited) {
                visited.add("visit:" + node.path());
                return !node.path().equals("root.skipped");
            }

            @Override
            public void postVisit(CmNode node, List<String> visited) {
                visited.add("post:" + node.path());
            }
        }, events);

        assertThat(events, contains(
                "visit:root",
                "visit:root.skipped",
                "post:root.skipped",
                "visit:root.sibling",
                "post:root.sibling",
                "post:root"));
    }

    static CmPathNode pathNode(CmNode parent, String path, String key, List<CmNode> children) {
        return new CmPathNodeImpl(parent, path, key, List.of(), children);
    }

    static CmOptionNode optionNode(CmNode parent, String path, String key, List<CmNode> children) {
        return new CmOptionNodeImpl(parent, path, key, List.of(), children, CmOption.builder().key(key).build(), null);
    }
}
