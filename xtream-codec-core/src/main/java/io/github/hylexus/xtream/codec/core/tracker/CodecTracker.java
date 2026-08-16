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
import io.github.hylexus.xtream.codec.core.FieldCodec;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
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
    private CodecTraceNode current;
    private boolean tracing;
    private @Nullable MapEntryItemType tempMapItemType;
    private @Nullable String tempFieldName;

    public CodecTracker() {
        this.recorder = new CodecTraceRecorder();
        this.current = this.recorder.trace().getRoot();
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
        this.current = this.recorder.trace().getRoot();
        this.tracing = true;
    }

    public void beginDecode(int rootStartAbsolute, @Nullable String entityClass) {
        this.recorder.begin(CodecTraceDirection.DECODE, rootStartAbsolute, entityClass);
        this.current = this.recorder.trace().getRoot();
        this.tracing = true;
    }

    public void finishTrace(@Nullable String payloadHex, int rootEndAbsolute) {
        this.recorder.finish(payloadHex, rootEndAbsolute);
        this.tracing = false;
    }

    /**
     * 进入一个从当前 ByteBuf 派生出来的 slice，后续子节点的 readerIndex 会按该 slice 起点折算到根报文坐标。
     *
     * @since 0.9.0
     */
    public void pushCoordinateBase(int currentBufferOffset) {
        this.recorder.pushCoordinateBase(currentBufferOffset);
    }

    /**
     * 离开当前 slice 坐标范围。
     *
     * @since 0.9.0
     */
    public void popCoordinateBase() {
        this.recorder.popCoordinateBase();
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
     * 将临时缓冲区中新产生的节点平移到正式报文坐标。
     *
     * @since 0.9.0
     */
    public void relocateTemporaryChildren(CodecTraceNode parent, int fromIndex, int toIndex, int targetStart) {
        this.recorder.relocateTemporaryChildren(parent, fromIndex, toIndex, targetStart);
    }

    public void recordFailure(Throwable throwable, int absoluteOffset) {
        this.recorder.fail(throwable, absoluteOffset);
        this.tracing = false;
    }

    public void updateTrackerHints(MapEntryItemType type) {
        this.tempMapItemType = type;
    }

    public void updateTempFieldName(@Nullable String tempFieldName) {
        this.tempFieldName = tempFieldName;
    }

    public String getFieldName(String fieldName) {
        if (this.tempFieldName == null) {
            return fieldName;
        }
        final String name = this.tempFieldName;
        this.tempFieldName = null;
        return name;
    }

    public CodecTraceNode startNewNestedFieldSpan(BeanPropertyMetadata metadata, @Nullable FieldCodec<?> fieldCodec, String fieldType) {
        final String fieldCodecString = fieldCodec == null
                ? null
                : fieldCodec.getClass().getSimpleName();
        return this.startNewNestedFieldSpan(metadata, fieldCodecString, fieldType);
    }

    public CodecTraceNode startNewNestedFieldSpan(BeanPropertyMetadata metadata, @Nullable String fieldCodec, @Nullable String fieldType) {
        final String resolvedFieldName = getFieldName(metadata.name());
        final CodecTraceNode node = this.recorder.enterNode(this.current, CodecTraceNodeKind.NESTED_FIELD, resolvedFieldName)
                .setJavaType(fieldType == null ? metadata.field().getType().getTypeName() : fieldType)
                .setCodecType(fieldCodec)
                .putAttribute("fieldDesc", metadata.xtreamFieldAnnotation().desc());
        this.current = node;
        return node;
    }

    public CodecTraceNode startNewNestedFieldSpan(String name, String desc, String fieldType, @Nullable String fieldCodec) {
        final CodecTraceNode node = this.recorder.enterNode(this.current, CodecTraceNodeKind.NESTED_FIELD, getFieldName(name))
                .setJavaType(fieldType)
                .setCodecType(fieldCodec)
                .putAttribute("fieldDesc", desc);
        this.current = node;
        return node;
    }

    public CodecTraceNode startNewCollectionFieldSpan(BeanPropertyMetadata metadata) {
        final CodecTraceNode node = this.recorder.enterNode(this.current, CodecTraceNodeKind.COLLECTION, getFieldName(metadata.name()))
                .setJavaType(this.getFieldFirstGenericTypeName(metadata.field()))
                .putAttribute("fieldDesc", metadata.xtreamFieldAnnotation().desc());
        this.current = node;
        return node;
    }

    public CodecTraceNode startNewCollectionFieldSpanForSimpleField(String name) {
        final CodecTraceNode node = this.recorder.enterNode(this.current, CodecTraceNodeKind.COLLECTION, name)
                .setJavaType("SimpleField")
                .putAttribute("fieldDesc", "");
        this.current = node;
        return node;
    }

    public CodecTraceNode startNewCollectionItemSpan(CodecTraceNode parent, String fieldName, int sequence) {
        final String itemName = getFieldName(fieldName + "[" + sequence + "]");
        final CodecTraceNode node = this.recorder.enterNode(parent, CodecTraceNodeKind.COLLECTION_ITEM, itemName)
                .setJavaType(parent.getJavaType())
                .putAttribute("itemIndex", sequence);
        this.current = node;
        return node;
    }

    public CodecTraceNode startNewMapFieldSpan(BeanPropertyMetadata metadata, String fieldCodec) {
        final CodecTraceNode node = this.recorder.enterNode(this.current, CodecTraceNodeKind.MAP, metadata.name())
                .setCodecType(fieldCodec)
                .putAttribute("fieldDesc", metadata.xtreamFieldAnnotation().desc());
        this.current = node;
        return node;
    }

    public CodecTraceNode startNewMapFieldSpan(String name, String desc, String fieldCodec) {
        final CodecTraceNode node = this.recorder.enterNode(this.current, CodecTraceNodeKind.MAP, name)
                .setCodecType(fieldCodec)
                .putAttribute("fieldDesc", desc);
        this.current = node;
        return node;
    }

    public CodecTraceNode startNewMapEntrySpan(CodecTraceNode parent, String fieldName, int sequence) {
        final CodecTraceNode node = this.recorder.enterNode(parent, CodecTraceNodeKind.MAP_ENTRY, "[" + sequence + "]")
                .putAttribute("fieldName", fieldName)
                .putAttribute("itemIndex", sequence);
        this.current = node;
        return node;
    }

    public CodecTraceNode startNewMapEntryItemSpan(CodecTraceNode parent, MapEntryItemType type, FieldCodec<?> fieldCodec) {
        final CodecTraceNode node = this.recorder.enterNode(parent, CodecTraceNodeKind.MAP_ENTRY_ITEM, mapEntryItemName(type))
                .setCodecType(fieldCodec.getClass().getSimpleName())
                .putAttribute("mapItemType", type);
        this.current = node;
        return node;
    }

    public void finishCurrentSpan() {
        this.recorder.exitNode(this.current);
        final CodecTraceNode parent = this.recorder.parentOf(this.current);
        this.current = parent == null ? this.recorder.trace().getRoot() : parent;
        resetTempStatus();
    }

    public CodecTraceNode addPrependLengthFieldSpan(CodecTraceNode parent, String fieldName, @Nullable Object value, @Nullable String hexString, String fieldCodec, String fieldDesc) {
        final CodecTraceNode node = this.recorder.addLeaf(CodecTraceNodeKind.LENGTH_FIELD, parent, fieldName, value, hexString, fieldCodec, null, fieldDesc, null, null);
        this.current = parent;
        this.resetTempStatus();
        return node;
    }

    public void addFieldSpan(CodecTraceNode parent, String fieldName, @Nullable Object value, String hexString, FieldCodec<?> fieldCodec, String fieldDesc) {
        this.addFieldSpan(parent, fieldName, value, hexString, fieldCodec.getClass().getSimpleName(), fieldDesc);
    }

    public void addFieldSpan(CodecTraceNode parent, String fieldName, @Nullable Object value, String hexString, FieldCodec<?> fieldCodec, String fieldDesc, int absoluteStart, int absoluteEnd) {
        this.addFieldSpan(parent, fieldName, value, hexString, fieldCodec.getClass().getSimpleName(), fieldDesc, absoluteStart, absoluteEnd);
    }

    public void addFieldSpan(CodecTraceNode parent, String fieldName, @Nullable Object value, String hexString, String fieldCodec, String fieldDesc) {
        this.addFieldSpan(parent, fieldName, value, hexString, fieldCodec, fieldDesc, null, null, CodecTraceNodeKind.FIELD);
    }

    public void addFieldSpan(CodecTraceNode parent, String fieldName, @Nullable Object value, String hexString, String fieldCodec, String fieldDesc, int absoluteStart, int absoluteEnd) {
        this.addFieldSpan(parent, fieldName, value, hexString, fieldCodec, fieldDesc, absoluteStart, absoluteEnd, CodecTraceNodeKind.FIELD);
    }

    private void addFieldSpan(CodecTraceNode parent, String fieldName, @Nullable Object value, String hexString, String fieldCodec, String fieldDesc,
                              @Nullable Integer absoluteStart, @Nullable Integer absoluteEnd, CodecTraceNodeKind defaultKind) {
        final boolean mapEntryItem = parent.getKind() == CodecTraceNodeKind.MAP_ENTRY;
        final CodecTraceNodeKind kind = mapEntryItem ? CodecTraceNodeKind.MAP_ENTRY_ITEM : defaultKind;
        final String name = mapEntryItem ? mapEntryItemName(this.tempMapItemType) : getFieldName(fieldName);
        final CodecTraceNode node = this.recorder.addLeaf(kind, parent, name, value, hexString, fieldCodec, null, fieldDesc, absoluteStart, absoluteEnd);
        if (this.tempMapItemType != null) {
            node.putAttribute("mapItemType", this.tempMapItemType);
        }
        this.current = parent;
        this.resetTempStatus();
    }

    public void addLengthFieldSpan(CodecTraceNode parent, String fieldName, @Nullable Object value, String hexString, FieldCodec<?> fieldCodec, String fieldDesc, int absoluteStart, int absoluteEnd) {
        this.addFieldSpan(parent, fieldName, value, hexString, fieldCodec.getClass().getSimpleName(), fieldDesc, absoluteStart, absoluteEnd, CodecTraceNodeKind.LENGTH_FIELD);
    }

    public void updateSpan(CodecTraceNode node, @Nullable Object value, @Nullable String hexString, int absoluteStart, int absoluteEnd) {
        this.recorder.updateLeaf(node, value, hexString, absoluteStart, absoluteEnd);
    }

    /**
     * 更新容器节点的结束位置，但保留节点创建时记录的起点。
     *
     * @since 0.9.0
     */
    public void updateContainerSpan(CodecTraceNode node, @Nullable Object value, @Nullable String hexString, @Nullable Integer absoluteEnd) {
        this.recorder.updateContainerNode(node, value, hexString, absoluteEnd);
    }

    public CodecTraceNode getCurrentSpan() {
        return this.current;
    }

    public void visit() {
        this.visit((level, node) -> System.out.println(("\t".repeat(level)) + "[==> " + level + "] " + node));
    }

    public void visit(BiConsumer<Integer, CodecTraceNode> consumer) {
        this.recorder.trace().visit(consumer);
    }

    private void resetTempStatus() {
        this.tempFieldName = null;
        this.tempMapItemType = null;
    }

    private String getFieldFirstGenericTypeName(Field field) {
        if (field.getGenericType() instanceof ParameterizedType parameterizedType) {
            if (parameterizedType.getActualTypeArguments().length > 0) {
                return parameterizedType.getActualTypeArguments()[0].getTypeName();
            }
        }
        return "unknown";
    }

    private static String mapEntryItemName(@Nullable MapEntryItemType type) {
        if (type == null) {
            return ".item";
        }
        return "." + type.name().toLowerCase();
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

    public enum MapEntryItemType {
        KEY, VALUE, VALUE_LENGTH
    }

    public interface FlattedSpan {
    }
}
