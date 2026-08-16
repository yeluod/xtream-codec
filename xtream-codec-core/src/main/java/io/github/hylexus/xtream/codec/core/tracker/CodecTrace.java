/*
 * Copyright 2024-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.hylexus.xtream.codec.core.tracker;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 一次编解码调试跟踪结果。
 *
 * @author hylexus
 * @author Codex (AI)
 * @since 0.9.0
 */
public class CodecTrace {
    private CodecTraceDirection direction = CodecTraceDirection.UNKNOWN;
    private @Nullable String entityClass;
    private @Nullable String payloadHex;
    private final CodecTraceNode root = new CodecTraceNode(CodecTraceNodeKind.ROOT, "root", null);
    private final List<CodecTraceDiagnostic> diagnostics = new ArrayList<>();

    public CodecTraceDirection getDirection() {
        return direction;
    }

    public CodecTrace setDirection(CodecTraceDirection direction) {
        this.direction = direction;
        return this;
    }

    public @Nullable String getEntityClass() {
        return entityClass;
    }

    public CodecTrace setEntityClass(@Nullable String entityClass) {
        this.entityClass = entityClass;
        this.root.setJavaType(entityClass);
        return this;
    }

    public @Nullable String getPayloadHex() {
        return payloadHex;
    }

    public CodecTrace setPayloadHex(@Nullable String payloadHex) {
        this.payloadHex = payloadHex;
        this.root.setHex(payloadHex);
        return this;
    }

    public CodecTraceNode getRoot() {
        return root;
    }

    public List<CodecTraceDiagnostic> getDiagnostics() {
        return diagnostics;
    }

    public CodecTrace addDiagnostic(CodecTraceDiagnostic diagnostic) {
        this.diagnostics.add(diagnostic);
        this.root.addDiagnostic(diagnostic);
        return this;
    }

    public void visit(BiConsumer<Integer, CodecTraceNode> consumer) {
        visitNode(0, this.root, consumer);
    }

    private static void visitNode(int level, CodecTraceNode node, BiConsumer<Integer, CodecTraceNode> consumer) {
        consumer.accept(level, node);
        for (final CodecTraceNode child : node.getChildren()) {
            visitNode(level + 1, child, consumer);
        }
    }
}
