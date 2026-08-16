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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * 编解码跟踪事件记录器。
 *
 * @author hylexus
 * @author Codex (AI)
 * @since 0.9.0
 */
public class CodecTraceRecorder {
    private final CodecTrace trace = new CodecTrace();
    private final Deque<Integer> coordinateBaseStack = new ArrayDeque<>();
    private final Deque<Integer> temporaryBufferCursorStack = new ArrayDeque<>();
    private int rootStartAbsolute;
    private int cursor;

    public CodecTrace trace() {
        return trace;
    }

    public void begin(CodecTraceDirection direction, int rootStartAbsolute, @Nullable String entityClass) {
        this.trace.setDirection(direction);
        this.rootStartAbsolute = rootStartAbsolute;
        this.cursor = 0;
        this.coordinateBaseStack.clear();
        this.temporaryBufferCursorStack.clear();
        if (entityClass != null) {
            this.trace.setEntityClass(entityClass);
        }
        this.trace.getRoot().setByteRange(0, null);
    }

    public void pushCoordinateBase(int currentBufferOffset) {
        this.coordinateBaseStack.push(this.currentCoordinateBase() + currentBufferOffset);
    }

    public void popCoordinateBase() {
        this.coordinateBaseStack.pop();
    }

    int beginTemporaryBuffer() {
        final int previousCursor = this.cursor;
        this.temporaryBufferCursorStack.push(previousCursor);
        this.cursor = 0;
        return previousCursor;
    }

    void endTemporaryBuffer(int previousCursor) {
        final int expectedCursor = this.temporaryBufferCursorStack.pop();
        if (expectedCursor != previousCursor) {
            throw new IllegalStateException("临时缓冲区游标恢复顺序不正确");
        }
        this.cursor = previousCursor;
    }

    void relocateTemporaryChildren(CodecTraceNode parent, int fromIndex, int toIndex, int targetStart) {
        final List<CodecTraceNode> children = parent.getChildren();
        if (fromIndex < 0 || toIndex > children.size() || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException("无效的 Trace 子节点范围");
        }
        for (int i = fromIndex; i < toIndex; i++) {
            shiftSubtree(children.get(i), targetStart);
        }
    }

    public void finish(@Nullable String payloadHex, int rootEndAbsolute) {
        this.trace.setPayloadHex(payloadHex);
        this.trace.getRoot()
                .setByteRange(0, Math.max(rootEndAbsolute - this.rootStartAbsolute, this.cursor))
                .setStatus(CodecTraceStatus.SUCCESS);
    }

    public void fail(Throwable throwable, @Nullable Integer absoluteOffset) {
        final Integer relativeOffset = absoluteOffset == null ? null : Math.max(absoluteOffset - this.rootStartAbsolute, 0);
        final CodecTraceDiagnostic diagnostic = new CodecTraceDiagnostic(
                "ERROR",
                throwable.getMessage() == null ? throwable.getClass().getName() : throwable.getMessage(),
                this.trace.getRoot().getId(),
                relativeOffset,
                throwable.getClass().getName()
        );
        this.trace.getRoot().setStatus(CodecTraceStatus.ERROR);
        this.trace.addDiagnostic(diagnostic);
    }

    public CodecTraceNode enterNode(CodecTraceNode parent, CodecTraceNodeKind kind, String name) {
        final CodecTraceNode node = new CodecTraceNode(kind, name, parent)
                .setByteRange(this.cursor, null);
        parent.addChild(node);
        return node;
    }

    public void exitNode(CodecTraceNode node) {
        completeContainerNode(node);
    }

    public CodecTraceNode addLeaf(CodecTraceNodeKind kind, CodecTraceNode parent, String name,
                                  @Nullable Object value, @Nullable String hex, @Nullable String codecType,
                                  @Nullable String javaType, @Nullable String fieldDesc,
                                  @Nullable Integer absoluteStart, @Nullable Integer absoluteEnd) {
        final ByteRange byteRange = this.normalizeRange(parent.getByteStart(), absoluteStart, absoluteEnd, hex);
        final CodecTraceNode node = new CodecTraceNode(kind, name, parent)
                .setValue(value)
                .setHex(hex)
                .setCodecType(codecType)
                .setJavaType(javaType)
                .setByteRange(byteRange.start(), byteRange.end())
                .setStatus(CodecTraceStatus.SUCCESS)
                .putAttribute("fieldDesc", fieldDesc);
        parent.addChild(node);
        this.cursor = Math.max(this.cursor, byteRange.end());
        expandParents(parent, byteRange.end());
        return node;
    }

    public void updateLeaf(CodecTraceNode node, @Nullable Object value, @Nullable String hex, @Nullable Integer absoluteStart, @Nullable Integer absoluteEnd) {
        final ByteRange byteRange = this.normalizeRange(node.getByteStart(), absoluteStart, absoluteEnd, hex);
        node.setValue(value)
                .setHex(hex)
                .setByteRange(byteRange.start(), byteRange.end())
                .setStatus(CodecTraceStatus.SUCCESS);
        this.cursor = Math.max(this.cursor, byteRange.end());
        expandParents(node, byteRange.end());
    }

    public void updateContainerNode(CodecTraceNode node, @Nullable Object value, @Nullable String hex, @Nullable Integer absoluteEnd) {
        final Integer byteStart = node.getByteStart();
        final int start = byteStart == null ? this.cursor : byteStart;
        final int end = absoluteEnd == null
                ? start + bytesLength(hex)
                : Math.max(this.currentCoordinateBase() + absoluteEnd - this.rootStartAbsolute, start);
        node.setValue(value)
                .setHex(hex)
                .setByteRange(start, end)
                .setStatus(CodecTraceStatus.SUCCESS);
        this.cursor = Math.max(this.cursor, end);
        expandParents(node, end);
    }

    public @Nullable CodecTraceNode parentOf(CodecTraceNode node) {
        final String parentId = node.getParentId();
        return parentId == null ? null : findNode(this.trace.getRoot(), parentId);
    }

    private int currentCoordinateBase() {
        return this.coordinateBaseStack.peek() == null ? 0 : this.coordinateBaseStack.peek();
    }

    private void completeContainerNode(CodecTraceNode node) {
        final Integer byteStart = node.getByteStart();
        final int start = byteStart == null ? this.cursor : byteStart;
        int end = this.cursor;
        for (final CodecTraceNode child : node.getChildren()) {
            if (child.getByteEnd() != null) {
                end = Math.max(end, child.getByteEnd());
            }
        }
        node.setByteRange(start, Math.max(end, start)).setStatus(CodecTraceStatus.SUCCESS);
        expandParents(Objects.requireNonNullElse(parentOf(node), this.trace.getRoot()), Math.max(end, start));
    }

    private void expandParents(@Nullable CodecTraceNode node, int byteEnd) {
        CodecTraceNode current = node;
        while (current != null) {
            final Integer start = current.getByteStart();
            final Integer end = current.getByteEnd();
            if (start == null) {
                current.setByteRange(0, byteEnd);
            } else if (end == null || byteEnd > end) {
                current.setByteRange(start, byteEnd);
            }
            current = parentOf(current);
        }
    }

    private static @Nullable CodecTraceNode findNode(CodecTraceNode node, String id) {
        if (id.equals(node.getId())) {
            return node;
        }
        for (final CodecTraceNode child : node.getChildren()) {
            final CodecTraceNode result = findNode(child, id);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static int bytesLength(@Nullable String hex) {
        if (hex == null || hex.isEmpty()) {
            return 0;
        }
        return hex.length() / 2;
    }

    private static void shiftSubtree(CodecTraceNode node, int offset) {
        final Integer byteStart = node.getByteStart();
        final Integer byteEnd = node.getByteEnd();
        node.setByteRange(
                byteStart == null ? null : byteStart + offset,
                byteEnd == null ? null : byteEnd + offset
        );
        for (final CodecTraceNode child : node.getChildren()) {
            shiftSubtree(child, offset);
        }
    }

    private ByteRange normalizeRange(@Nullable Integer anchorStart, @Nullable Integer absoluteStart, @Nullable Integer absoluteEnd, @Nullable String hex) {
        if (absoluteStart == null) {
            final int start = anchorStart == null ? this.cursor : anchorStart;
            return new ByteRange(start, start + bytesLength(hex));
        }

        final int relativeStart = Math.max(this.currentCoordinateBase() + absoluteStart - this.rootStartAbsolute, 0);
        final int length = absoluteEnd == null ? bytesLength(hex) : Math.max(absoluteEnd - absoluteStart, 0);
        final int end = absoluteEnd == null ? relativeStart + length : Math.max(this.currentCoordinateBase() + absoluteEnd - this.rootStartAbsolute, relativeStart);
        return new ByteRange(relativeStart, end);
    }

    private record ByteRange(int start, int end) {
    }
}
