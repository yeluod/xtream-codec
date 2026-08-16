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

package io.github.hylexus.xtream.codec.core.impl.codec;

import io.github.hylexus.xtream.codec.common.bean.BeanPropertyMetadata;
import io.github.hylexus.xtream.codec.common.utils.FormatUtils;
import io.github.hylexus.xtream.codec.common.utils.XtreamTypes;
import io.github.hylexus.xtream.codec.core.BeanMetadataRegistry;
import io.github.hylexus.xtream.codec.core.FieldCodec;
import io.github.hylexus.xtream.codec.core.tracker.CodecTraceNode;
import io.github.hylexus.xtream.codec.core.tracker.CodecTracker;
import io.github.hylexus.xtream.codec.core.utils.BeanUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @param <K>    {@link Map.Entry#getKey()}
 * @param <VLFC> ValueLengthFieldCodec
 * @author hylexus
 */
@SuppressWarnings({"unchecked", "rawtypes", "checkstyle:ClassTypeParameterName"})
public abstract class AbstractMapFieldCodec<
        K,
        VLFC extends IntegralFieldCodec
        > implements FieldCodec<Object> {

    protected final BeanMetadataRegistry registry;
    protected final Map<K, FieldCodec<?>> keyFieldCodecInstances;

    @FieldCodecCreator
    public AbstractMapFieldCodec(BeanMetadataRegistry registry) {
        this.registry = registry;
        this.keyFieldCodecInstances = new LinkedHashMap<>();
        this.initValueCodec(registry);
    }

    /**
     * @see #registerValueFieldCodec(Object, FieldCodec)
     * @see #registerValueFieldCodec(Object, Class)
     */
    protected abstract void initValueCodec(BeanMetadataRegistry registry);

    public void registerValueFieldCodec(K key, FieldCodec<?> fieldCodec) {
        this.keyFieldCodecInstances.put(key, fieldCodec);
    }

    public void registerValueFieldCodec(K key, Class<?> cls) {
        final FieldCodec<?> newInstance;
        if (FieldCodec.class.isAssignableFrom(cls)) {
            if (!CustomFieldCodec.class.isAssignableFrom(cls)) {
                throw new IllegalArgumentException("cls must be a subclass of CustomFieldCodec");
            }
            newInstance = BeanUtils.createFieldCodecInstance(cls, registry);
        } else {
            if (XtreamTypes.isBasicType(cls)) {
                // 不支持基础数据类型，只支持实体类
                throw new IllegalArgumentException("cls must be a entity class");
            }
            newInstance = new EntityFieldCodec(cls);
        }
        this.keyFieldCodecInstances.put(key, newInstance);
    }

    protected abstract FieldCodec getKeyFieldCodec();

    protected abstract VLFC getValueLengthFieldCodec();

    protected FieldCodec getValueFieldCodec(K key) {
        final FieldCodec<?> fieldCodec = this.keyFieldCodecInstances.get(key);
        if (fieldCodec == null) {
            if (key instanceof Number number) {
                throw new UnsupportedOperationException("Unsupported key: " + key + "(0x" + FormatUtils.toHexString(number.longValue(), 2) + ")");
            } else {
                throw new UnsupportedOperationException("Unsupported key: " + key);
            }
        }
        return fieldCodec;
    }

    @Override
    public Object deserialize(BeanPropertyMetadata propertyMetadata, DeserializeContext context, ByteBuf input, int length) {
        final ByteBuf slice = length < 0
                ? input // all remaining
                : input.readSlice(length);
        final Map<Object, @Nullable Object> result = new LinkedHashMap<>();
        while (slice.isReadable()) {
            final Object key = Objects.requireNonNull(this.getKeyFieldCodec().deserialize(propertyMetadata, context, slice, length));
            final int valueLength = Objects.requireNonNull(this.getValueLengthFieldCodec().deserialize(propertyMetadata, context, slice, length)).intValue();
            final FieldCodec<?> valueFieldCodec = this.getValueFieldCodec((K) key);
            final Object value = valueFieldCodec.deserialize(propertyMetadata, context, slice, valueLength);
            result.put(key, value);
        }
        return result;
    }

    @Override
    public Object deserializeWithTracker(BeanPropertyMetadata propertyMetadata, DeserializeContext context, ByteBuf input, int length) {
        final int inputReaderIndexBeforeSlice = input.readerIndex();
        final ByteBuf slice = length < 0
                ? input // all remaining
                : input.readSlice(length);
        final CodecTracker codecTracker = Objects.requireNonNull(context.codecTracker());
        if (length >= 0) {
            codecTracker.pushCoordinateBase(inputReaderIndexBeforeSlice);
        }
        final Map<Object, @Nullable Object> result = new LinkedHashMap<>();
        final CodecTraceNode mapFieldSpan = codecTracker.startNewMapFieldSpan(propertyMetadata, this.getClass().getSimpleName());
        int sequence = 0;
        try {
            while (slice.isReadable()) {
                final int indexBeforeRead = slice.readerIndex();
                final CodecTraceNode mapEntrySpan = codecTracker.startNewMapEntrySpan(mapFieldSpan, propertyMetadata.name(), sequence++);

                codecTracker.updateTrackerHints(CodecTracker.MapEntryItemType.KEY);
                final Object key = Objects.requireNonNull(this.getKeyFieldCodec().deserializeWithTracker(propertyMetadata, context, slice, length));

                codecTracker.updateTrackerHints(CodecTracker.MapEntryItemType.VALUE_LENGTH);
                final int valueLength = Objects.requireNonNull(this.getValueLengthFieldCodec().deserializeWithTracker(propertyMetadata, context, slice, length)).intValue();

                final FieldCodec<?> valueFieldCodec = this.getValueFieldCodec((K) key);
                codecTracker.updateTrackerHints(CodecTracker.MapEntryItemType.VALUE);
                final Object value = valueFieldCodec.deserializeWithTracker(propertyMetadata, context, slice, valueLength);

                codecTracker.updateContainerSpan(mapEntrySpan, null, FormatUtils.toHexString(slice, indexBeforeRead, slice.readerIndex() - indexBeforeRead), slice.readerIndex());
                codecTracker.finishCurrentSpan();
                result.put(key, value);
            }
            codecTracker.updateContainerSpan(mapFieldSpan, null, FormatUtils.toHexString(slice, 0, slice.readerIndex()), slice.readerIndex());
            codecTracker.finishCurrentSpan();
        } finally {
            if (length >= 0) {
                codecTracker.popCoordinateBase();
            }
        }
        return result;
    }

    @Override
    public void serialize(BeanPropertyMetadata propertyMetadata, SerializeContext context, ByteBuf output, @Nullable Object value) {
        if (value == null) {
            return;
        }
        final Map<Object, Object> map = (Map<Object, Object>) value;
        final ByteBuf temp = ByteBufAllocator.DEFAULT.buffer();
        try {
            for (final Map.Entry<Object, Object> entry : map.entrySet()) {
                final Object mapKey = entry.getKey();
                final Object mapValue = entry.getValue();

                final FieldCodec keyFieldCodec = this.getKeyFieldCodec();
                keyFieldCodec.serialize(propertyMetadata, context, output, mapKey);

                final FieldCodec valueFieldCodec = this.getValueFieldCodec((K) mapKey);
                valueFieldCodec.serialize(propertyMetadata, context, temp, mapValue);

                final int valueLength = temp.writerIndex();
                this.getValueLengthFieldCodec().serialize(propertyMetadata, context, output, valueLength);
                output.writeBytes(temp);
                temp.clear();
            }
        } finally {
            temp.release();
        }
    }

    @Override
    public void serializeWithTracker(BeanPropertyMetadata propertyMetadata, SerializeContext context, ByteBuf output, @Nullable Object value) {
        if (value == null) {
            return;
        }
        final Map<Object, Object> map = (Map<Object, Object>) value;
        final ByteBuf temp = ByteBufAllocator.DEFAULT.buffer();
        final CodecTracker codecTracker = Objects.requireNonNull(context.codecTracker());
        final CodecTraceNode mapFieldSpan = codecTracker.startNewMapFieldSpan(propertyMetadata, this.getClass().getSimpleName());
        final int parenIndexBeforeWrite = output.writerIndex();
        final CodecTraceNode parent = codecTracker.getCurrentSpan();
        int sequence = 0;
        try {
            for (final Map.Entry<Object, Object> entry : map.entrySet()) {
                final Object mapKey = entry.getKey();
                final Object mapValue = entry.getValue();

                final CodecTraceNode mapEntrySpan = codecTracker.startNewMapEntrySpan(parent, propertyMetadata.name(), sequence++);
                final int writerIndex = output.writerIndex();
                codecTracker.updateTrackerHints(CodecTracker.MapEntryItemType.KEY);
                this.getKeyFieldCodec().serializeWithTracker(propertyMetadata, context, output, mapKey);

                final FieldCodec valueFieldCodec = this.getValueFieldCodec((K) mapKey);
                codecTracker.updateTrackerHints(CodecTracker.MapEntryItemType.VALUE);
                final int valueChildStart = mapEntrySpan.getChildren().size();
                try (final CodecTracker.TemporaryBufferScope ignored = codecTracker.openTemporaryBuffer()) {
                    valueFieldCodec.serializeWithTracker(propertyMetadata, context, temp, mapValue);
                }
                final int valueChildEnd = mapEntrySpan.getChildren().size();

                final int valueLength = temp.writerIndex();
                codecTracker.updateTrackerHints(CodecTracker.MapEntryItemType.VALUE_LENGTH);
                this.getValueLengthFieldCodec().serializeWithTracker(propertyMetadata, context, output, valueLength);
                codecTracker.relocateTemporaryChildren(mapEntrySpan, valueChildStart, valueChildEnd, output.writerIndex());
                output.writeBytes(temp);
                codecTracker.updateSpan(mapEntrySpan, null, FormatUtils.toHexString(output, writerIndex, output.writerIndex() - writerIndex), writerIndex, output.writerIndex());
                temp.clear();
                codecTracker.finishCurrentSpan();
            }
            codecTracker.updateSpan(mapFieldSpan, null, FormatUtils.toHexString(output, parenIndexBeforeWrite, output.writerIndex() - parenIndexBeforeWrite), parenIndexBeforeWrite, output.writerIndex());
            codecTracker.finishCurrentSpan();
        } finally {
            temp.release();
        }
    }
}
