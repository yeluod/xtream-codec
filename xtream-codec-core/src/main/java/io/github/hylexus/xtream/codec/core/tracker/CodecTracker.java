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

import io.github.hylexus.xtream.codec.common.bean.BeanPropertyMetadata;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;

/**
 * 编解码调试跟踪器。
 *
 * @author hylexus
 * @author Codex (AI)
 */
@SuppressWarnings("NullAway")
public class CodecTracker {
    private final CodecTraceRecorder recorder;
    private final Deque<TraceScope> scopes = new ArrayDeque<>();
    private final Deque<NodeOverrideScope> nodeOverrides = new ArrayDeque<>();
    private @Nullable CodecTraceNode deepestIncompleteNode;
    private @Nullable Throwable recordedFailure;
    private boolean tracing;

    public CodecTracker() {
        this.recorder = new CodecTraceRecorder();
    }

    /**
     * 返回结构化编解码跟踪结果。
     *
     * @since 0.9.0
     */
    public CodecTrace getTrace() {
        return this.recorder.trace();
    }

    /**
     * 返回面向 Web 调试页面的跟踪视图。
     *
     * @since 0.9.0
     */
    public CodecTraceView toTraceView() {
        return CodecTraceView.from(this.getTrace());
    }

    public boolean isTracing() {
        return tracing;
    }

    public void beginEncode(int rootStartAbsolute, @Nullable String entityClass) {
        this.recorder.begin(CodecTraceDirection.ENCODE, rootStartAbsolute, entityClass);
        this.scopes.clear();
        this.nodeOverrides.clear();
        this.deepestIncompleteNode = null;
        this.recordedFailure = null;
        this.tracing = true;
    }

    public void beginDecode(int rootStartAbsolute, @Nullable String entityClass) {
        this.recorder.begin(CodecTraceDirection.DECODE, rootStartAbsolute, entityClass);
        this.scopes.clear();
        this.nodeOverrides.clear();
        this.deepestIncompleteNode = null;
        this.recordedFailure = null;
        this.tracing = true;
    }

    public void finishTrace(@Nullable String payloadHex, int rootEndAbsolute) {
        this.recorder.finish(payloadHex, rootEndAbsolute);
        if (!this.scopes.isEmpty()) {
            final IllegalStateException error = new IllegalStateException("编解码跟踪存在未关闭的 scope");
            final CodecTraceNode deepestNode = this.scopes.peek().node;
            while (!this.scopes.isEmpty()) {
                final TraceScope scope = this.scopes.pop();
                scope.node.setStatus(CodecTraceStatus.ERROR);
                scope.completed = true;
            }
            this.recorder.failNode(deepestNode, error, rootEndAbsolute);
            this.recorder.markFailed();
        }
        this.nodeOverrides.clear();
        this.deepestIncompleteNode = null;
        this.recordedFailure = null;
        this.tracing = false;
    }

    /**
     * 打开一个 slice 坐标 scope，关闭时自动恢复上一级坐标。
     *
     * @since 0.9.0
     */
    public CoordinateScope openCoordinateBase(int currentBufferOffset) {
        this.recorder.pushCoordinateBase(currentBufferOffset);
        return new CoordinateScope();
    }

    /**
     * 开始记录一个从零开始计数的临时缓冲区。
     *
     * @since 0.9.0
     */
    public TemporaryBufferScope openTemporaryBuffer() {
        return new TemporaryBufferScope(this.recorder.beginTemporaryBuffer());
    }

    /**
     * 标记当前 scope 已有的子节点，供后续获取或迁移本次操作新增的节点。
     *
     * @since 0.9.0
     */
    public TraceCheckpoint checkpoint() {
        final CodecTraceNode parent = this.activeNode();
        return new TraceCheckpoint(parent, parent.getChildren().size());
    }

    public void recordFailure(Throwable throwable, int absoluteOffset) {
        if (this.recordedFailure != throwable) {
            if (this.deepestIncompleteNode == null) {
                this.recorder.fail(throwable, absoluteOffset);
            } else {
                this.recorder.failNode(this.deepestIncompleteNode, throwable, absoluteOffset);
                this.recorder.markFailed();
            }
        } else {
            this.recorder.markFailed();
        }
        this.scopes.clear();
        this.nodeOverrides.clear();
        this.deepestIncompleteNode = null;
        this.recordedFailure = null;
        this.tracing = false;
    }

    /**
     * 为下一次进入的节点指定 Map entry item 类型。
     *
     * @since 0.9.0
     */
    public NodeOverrideScope overrideNextMapEntryItem(MapEntryItemType type) {
        final NodeOverrideScope scope = new NodeOverrideScope(null, type);
        this.nodeOverrides.push(scope);
        return scope;
    }

    /**
     * 为下一次进入的节点指定名称。
     *
     * @since 0.9.0
     */
    public NodeOverrideScope overrideNextNodeName(String name) {
        final NodeOverrideScope scope = new NodeOverrideScope(name, null);
        this.nodeOverrides.push(scope);
        return scope;
    }

    /**
     * 创建一个内部字段 scope。scope 栈负责维护新节点的父子关系。
     *
     * @apiNote 调用方应使用 try-with-resources 管理生命周期，标准异常收尾由 tracker 统一完成；仅在需要恢复、
     * 删除节点、转换异常或继续执行时显式捕获异常。不要引入 lambda executor、回调模板或通用执行器，
     * 以免隐藏编解码控制流和异常位置。
     *
     * @since 0.9.0
     */
    public TraceScope enterScope(CodecTraceNodeKind kind, String name, @Nullable String javaType,
                                 @Nullable String processorType, @Nullable String fieldDesc, int localStart) {
        final CodecTraceNode parent = this.activeNode();
        final NodeOverrideScope nodeOverride = this.consumeNodeOverride();
        final MapEntryItemType mapItemType = nodeOverride == null ? null : nodeOverride.mapItemType;
        if (mapItemType != null && parent.getKind() != CodecTraceNodeKind.MAP_ENTRY) {
            throw new IllegalStateException("Map entry item 节点必须位于 MAP_ENTRY 节点下");
        }
        final CodecTraceNodeKind actualKind = parent.getKind() == CodecTraceNodeKind.MAP_ENTRY
                ? CodecTraceNodeKind.MAP_ENTRY_ITEM
                : kind;
        final String actualName = parent.getKind() == CodecTraceNodeKind.MAP_ENTRY
                ? mapEntryItemName(mapItemType)
                : nodeOverride != null && nodeOverride.name != null ? nodeOverride.name : name;
        final CodecTraceNode node = this.recorder.enterNode(parent, actualKind, actualName, localStart)
                .setJavaType(javaType)
                .setProcessorType(processorType)
                .putAttribute("fieldDesc", fieldDesc);
        if (mapItemType != null) {
            node.putAttribute("mapItemType", mapItemType);
        }
        final TraceScope scope = new TraceScope(node, this.recorder.currentCoordinateBase(), localStart);
        this.scopes.push(scope);
        return scope;
    }

    /**
     * 使用属性元数据创建字段 scope。
     *
     * @since 0.7.0
     */
    public TraceScope enterScope(CodecTraceNodeKind kind, BeanPropertyMetadata metadata,
                                 Class<?> processorType, int localStart) {
        return this.enterScope(
                kind,
                metadata.name(),
                metadata.rawClass().getTypeName(),
                processorType.getSimpleName(),
                metadata.xtreamFieldAnnotation().desc(),
                localStart
        );
    }

    /**
     * 创建一个不改变当前 scope 的延迟完成节点。
     *
     * @since 0.9.0
     */
    public DeferredNode deferNode(CodecTraceNodeKind kind, String name, @Nullable String javaType,
                                  @Nullable String processorType, @Nullable String fieldDesc, int localStart) {
        final CodecTraceNode node = this.recorder.enterNode(this.activeNode(), kind, name, localStart)
                .setJavaType(javaType)
                .setProcessorType(processorType)
                .putAttribute("fieldDesc", fieldDesc);
        return new DeferredNode(node);
    }

    private void completeScope(TraceScope scope, @Nullable Object value, @Nullable String hex, int localEnd) {
        this.requireTopScope(scope);
        this.recorder.completeNode(scope.node, value, hex, scope.coordinateBase, scope.localStart, localEnd);
        this.scopes.pop();
        scope.completed = true;
    }

    private void failScope(TraceScope scope, Throwable throwable, int localOffset) {
        this.requireTopScope(scope);
        final CodecTraceNode failureNode = this.deepestIncompleteNode == null
                ? scope.node
                : this.deepestIncompleteNode;
        this.recorder.failNode(failureNode, throwable, scope.coordinateBase, localOffset);
        scope.node.setStatus(CodecTraceStatus.ERROR);
        this.scopes.pop();
        scope.completed = true;
        this.deepestIncompleteNode = null;
        this.recordedFailure = throwable;
    }

    private void closeScope(TraceScope scope) {
        this.requireTopScope(scope);
        if (!scope.completed) {
            scope.node.setStatus(CodecTraceStatus.ERROR);
            if (this.deepestIncompleteNode == null) {
                this.deepestIncompleteNode = scope.node;
            }
        }
        this.scopes.pop();
        scope.completed = true;
    }

    private void discardScope(TraceScope scope) {
        this.requireTopScope(scope);
        this.recorder.discardNode(scope.node);
        this.scopes.pop();
        scope.completed = true;
    }

    private void requireTopScope(TraceScope scope) {
        if (this.scopes.peek() != scope) {
            throw new IllegalStateException("Trace scope 的关闭顺序不正确");
        }
    }

    public void visit() {
        this.visit((level, node) -> System.out.println(("\t".repeat(level)) + "[==> " + level + "] " + node));
    }

    public void visit(BiConsumer<Integer, CodecTraceNode> consumer) {
        this.recorder.trace().visit(consumer);
    }

    private static String mapEntryItemName(@Nullable MapEntryItemType type) {
        if (type == null) {
            return ".item";
        }
        return "." + type.name().toLowerCase();
    }

    private CodecTraceNode activeNode() {
        final TraceScope scope = this.scopes.peek();
        return scope == null ? this.recorder.trace().getRoot() : scope.node;
    }

    private @Nullable NodeOverrideScope consumeNodeOverride() {
        final NodeOverrideScope scope = this.nodeOverrides.peek();
        if (scope == null || scope.consumed) {
            return null;
        }
        scope.consumed = true;
        return scope;
    }

    private void closeNodeOverride(NodeOverrideScope scope) {
        if (this.nodeOverrides.peek() != scope) {
            throw new IllegalStateException("Trace node override 的关闭顺序不正确");
        }
        this.nodeOverrides.pop();
        scope.closed = true;
    }

    public final class TemporaryBufferScope implements AutoCloseable {
        private final int previousCursor;
        private boolean closed;

        private TemporaryBufferScope(int previousCursor) {
            this.previousCursor = previousCursor;
        }

        @Override
        public void close() {
            if (!this.closed) {
                CodecTracker.this.recorder.endTemporaryBuffer(this.previousCursor);
                this.closed = true;
            }
        }
    }

    /**
     * 一次 slice 坐标范围的生命周期。
     *
     * @since 0.9.0
     */
    public final class CoordinateScope implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            if (!this.closed) {
                CodecTracker.this.recorder.popCoordinateBase();
                this.closed = true;
            }
        }
    }

    public enum MapEntryItemType {
        KEY, VALUE, VALUE_LENGTH
    }

    /**
     * 标记该值的字段节点应直接平铺到当前 trace 层级。
     *
     * @since 0.9.0
     */
    public interface FlattenedTrace {
    }

    /**
     * 下一次 trace 节点创建的元数据覆盖范围。
     *
     * @since 0.9.0
     */
    public final class NodeOverrideScope implements AutoCloseable {
        private final @Nullable String name;
        private final @Nullable MapEntryItemType mapItemType;
        private boolean consumed;
        private boolean closed;

        private NodeOverrideScope(@Nullable String name, @Nullable MapEntryItemType mapItemType) {
            this.name = name;
            this.mapItemType = mapItemType;
        }

        @Override
        public void close() {
            if (!this.closed) {
                CodecTracker.this.closeNodeOverride(this);
            }
        }
    }

    /**
     * 某次操作新增子节点的起始标记。
     *
     * @since 0.9.0
     */
    public final class TraceCheckpoint {
        private final CodecTraceNode parent;
        private final int childStart;
        private @Nullable Integer childEnd;

        private TraceCheckpoint(CodecTraceNode parent, int childStart) {
            this.parent = parent;
            this.childStart = childStart;
        }

        public DeferredNode requireSingleNode() {
            this.captureNewChildren();
            final int childEnd = this.capturedChildEnd();
            if (childEnd != this.childStart + 1) {
                throw new IllegalStateException("Trace checkpoint 预期仅新增一个节点，实际新增 " + (childEnd - this.childStart) + " 个");
            }
            return new DeferredNode(this.parent.getChildren().get(this.childStart));
        }

        /**
         * 封存 checkpoint 创建后新增的子节点范围，避免后续节点参与迁移。
         *
         * @since 0.9.0
         */
        public TraceCheckpoint captureNewChildren() {
            if (this.childEnd == null) {
                this.childEnd = this.parent.getChildren().size();
            }
            return this;
        }

        public void relocateNewChildren(int targetStart) {
            CodecTracker.this.recorder.relocateTemporaryChildren(
                    this.parent, this.childStart, this.capturedChildEnd(), targetStart
            );
        }

        private int capturedChildEnd() {
            if (this.childEnd == null) {
                throw new IllegalStateException("迁移 Trace 子节点前必须先调用 captureNewChildren()");
            }
            return this.childEnd;
        }
    }

    /**
     * 允许在节点创建后回填最终值和范围的句柄。
     *
     * @since 0.9.0
     */
    public final class DeferredNode {
        private final CodecTraceNode node;

        private DeferredNode(CodecTraceNode node) {
            this.node = node;
        }

        public void update(@Nullable Object value, @Nullable String hex, int localStart, int localEnd) {
            CodecTracker.this.recorder.updateLeaf(this.node, value, hex, localStart, localEnd);
        }
    }

    /**
     * 一次内部 trace 节点记录的生命周期。
     *
     * @since 0.9.0
     */
    public final class TraceScope implements AutoCloseable {
        private final CodecTraceNode node;
        private final int coordinateBase;
        private final int localStart;
        private boolean completed;

        private TraceScope(CodecTraceNode node, int coordinateBase, int localStart) {
            this.node = node;
            this.coordinateBase = coordinateBase;
            this.localStart = localStart;
        }

        public CodecTraceNode node() {
            return this.node;
        }

        public TraceScope complete(@Nullable Object value, int localEnd) {
            return this.complete(value, null, localEnd);
        }

        public TraceScope complete(@Nullable Object value, @Nullable String hex, int localEnd) {
            if (!this.completed) {
                CodecTracker.this.completeScope(this, value, hex, localEnd);
            }
            return this;
        }

        public TraceScope fail(Throwable throwable, int localOffset) {
            if (!this.completed) {
                CodecTracker.this.failScope(this, throwable, localOffset);
            }
            return this;
        }

        public TraceScope discard() {
            if (!this.completed) {
                CodecTracker.this.discardScope(this);
            }
            return this;
        }

        @Override
        public void close() {
            if (!this.completed) {
                CodecTracker.this.closeScope(this);
            }
        }
    }
}
