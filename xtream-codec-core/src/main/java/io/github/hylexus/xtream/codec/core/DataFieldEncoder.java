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

package io.github.hylexus.xtream.codec.core;

import io.github.hylexus.xtream.codec.common.exception.NotYetImplementedException;
import io.github.hylexus.xtream.codec.common.utils.BcdOps;
import io.github.hylexus.xtream.codec.common.utils.FormatUtils;
import io.github.hylexus.xtream.codec.common.utils.XtreamBytes;
import io.github.hylexus.xtream.codec.common.utils.XtreamConstants;
import io.github.hylexus.xtream.codec.core.annotation.PrependLengthFieldType;
import io.github.hylexus.xtream.codec.core.impl.DefaultSerializeContext;
import io.github.hylexus.xtream.codec.core.tracker.CodecTraceNodeKind;
import io.github.hylexus.xtream.codec.core.tracker.CodecTracker;
import io.github.hylexus.xtream.codec.core.type.simple.DataField;
import io.netty.buffer.ByteBuf;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@NullMarked
@ApiStatus.Experimental
public class DataFieldEncoder {
    public DataFieldEncoder() {
    }

    public @Nullable DataField decode(FieldCodec.DeserializeContext context, ByteBuf input) {
        throw new NotYetImplementedException("暂不支持");
    }

    public void encode(FieldCodec.SerializeContext context, Iterable<? extends @Nullable DataField> simpleFields, ByteBuf output) {
        for (final DataField dataField : simpleFields) {
            if (dataField == null) {
                continue;
            }
            this.encode(context, dataField, output);
        }
    }

    public void encode(FieldCodec.SerializeContext context, @Nullable DataField dataField, ByteBuf output) {
        if (dataField == null || dataField.value() == null) {
            return;
        }
        final PrependLengthFieldType prependLengthFieldType = dataField.prependLengthFieldType();
        final int prependLengthFieldTypeByteCounts = prependLengthFieldType.getByteCounts();
        if (prependLengthFieldTypeByteCounts <= 0) {
            this.doEncodeField(context, output, dataField);
        } else {
            final int lengthFieldWriterIndex = output.writerIndex();
            // 写入长度字段占位符
            prependLengthFieldType.writeTo(output, 0);
            final int beforeEncode = output.writerIndex();

            this.doEncodeField(context, output, dataField);

            final int afterEncode = output.writerIndex();
            final int byteCounts = afterEncode - beforeEncode;

            output.writerIndex(lengthFieldWriterIndex);
            // 写入长度字段
            prependLengthFieldType.writeTo(output, byteCounts);
            output.writerIndex(afterEncode);
        }
    }

    private void doEncodeField(FieldCodec.SerializeContext context, ByteBuf output, DataField dataField) {
        switch (dataField) {
            case DataField.I8 i8 -> output.writeByte(i8.value());
            case DataField.U8 u8 -> output.writeByte(u8.value());
            case DataField.I16 i16 -> output.writeShort(i16.value());
            case DataField.U16 u16 -> output.writeShort(u16.value());
            case DataField.I32 i32 -> output.writeInt(i32.value());
            case DataField.U32 u32 -> output.writeInt(u32.value().intValue());
            case DataField.I64 i64 -> output.writeLong(i64.value());
            case DataField.F32 f32 -> output.writeFloat(f32.value());
            case DataField.F64 f64 -> output.writeDouble(f64.value());
            case DataField.Bcd8421String bcd8421String -> BcdOps.encodeBcd8421StringIntoByteBuf(bcd8421String.value(), output);
            case DataField.HexString hexString -> XtreamBytes.writeHexString(output, hexString.value());
            case DataField.GbkString gbkString -> encodeString(output, gbkString.value(), XtreamConstants.CHARSET_GBK);
            case DataField.Gb2312String gb2312String -> encodeString(output, gb2312String.value(), XtreamConstants.CHARSET_GB_2312);
            case DataField.Utf8String utf8String -> encodeString(output, utf8String.value(), XtreamConstants.CHARSET_UTF8);
            case DataField.GenericString genericString -> encodeString(output, genericString.value(), Charset.forName(genericString.charset()));
            case DataField.ByteSequence byteSequence -> output.writeBytes(byteSequence.value());
            case DataField.Struct struct -> {
                final List<DataField> value = struct.value();
                final DefaultSerializeContext newContext = new DefaultSerializeContext(context, value);
                this.encode(newContext, value, output);
            }
            case DataField.Sequence sequence -> {
                final List<DataField> value = sequence.value();
                final DefaultSerializeContext newContext = new DefaultSerializeContext(context, value);
                this.encode(newContext, value, output);
            }
            case DataField.Dict<?> simpleMap -> {
                final DefaultSerializeContext newContext = new DefaultSerializeContext(context, simpleMap);
                final Map<? extends DataField.DictKey, DataField> map = simpleMap.value();
                final ByteBuf temp = context.bufferFactory().buffer();
                try {
                    for (Map.Entry<? extends DataField.DictKey, DataField> entry : map.entrySet()) {
                        try {
                            // 1. key
                            final DataField.DictKey key = entry.getKey();
                            this.doEncodeField(newContext, output, key);
                            // 2. value
                            final DataField value = entry.getValue();
                            this.doEncodeField(newContext, temp, value);
                            // 3. valueLength
                            final int valueLength = temp.writerIndex();
                            simpleMap.valueLengthType().writeTo(output, valueLength);
                            output.writeBytes(temp);
                        } finally {
                            temp.clear();
                        }
                    }
                } finally {
                    temp.release();
                }
            }
            case DataField.TlvDataField tlvDataField -> {
                final DefaultSerializeContext newContext = new DefaultSerializeContext(context, tlvDataField);
                // 1. tag
                final DataField.DictKey tag = tlvDataField.tag();
                this.doEncodeField(newContext, output, tag);
                final ByteBuf temp = context.bufferFactory().buffer();

                try {
                    // 2. value
                    final DataField value = tlvDataField.value();
                    this.doEncodeField(newContext, temp, value);

                    // 3. length
                    final int valueLength = temp.writerIndex();
                    tlvDataField.length().writeTo(output, valueLength);
                    output.writeBytes(temp);
                } finally {
                    temp.release();
                }
            }
            case DataField.CustomDataField customSimpleField -> customSimpleField.writeTo(output);
        }
    }

    public void encodeWithTracker(FieldCodec.SerializeContext context, Iterable<? extends @Nullable DataField> simpleFields, ByteBuf output) {
        for (final DataField dataField : simpleFields) {
            if (dataField == null) {
                continue;
            }
            this.encodeWithTracker(context, dataField, output);
        }
    }

    public void encodeWithTracker(FieldCodec.SerializeContext context, @Nullable DataField dataField, ByteBuf output) {
        if (dataField == null || dataField.value() == null) {
            return;
        }
        final PrependLengthFieldType prependLengthFieldType = dataField.prependLengthFieldType();
        final int prependLengthFieldTypeByteCounts = prependLengthFieldType.getByteCounts();
        if (prependLengthFieldTypeByteCounts <= 0) {
            this.doEncodeFieldWithTracker(context, output, dataField);
        } else {
            @SuppressWarnings("Duplicated") final CodecTracker codecTracker = Objects.requireNonNull(context.codecTracker());
            final int lengthFieldWriterIndex = output.writerIndex();
            final CodecTracker.DeferredNode prependLengthField = codecTracker.deferNode(
                    CodecTraceNodeKind.LENGTH_FIELD, "prependLengthField", null,
                    prependLengthFieldType.name(), "前置长度字段", lengthFieldWriterIndex
            );
            // 写入长度字段占位符
            prependLengthFieldType.writeTo(output, 0);
            final int beforeEncode = output.writerIndex();

            this.doEncodeFieldWithTracker(context, output, dataField);

            final int afterEncode = output.writerIndex();
            @SuppressWarnings("Duplicated") final int byteCounts = afterEncode - beforeEncode;

            output.writerIndex(lengthFieldWriterIndex);
            // 写入长度字段
            prependLengthFieldType.writeTo(output, byteCounts);
            final String hexString = FormatUtils.toHexString(output, lengthFieldWriterIndex, output.writerIndex() - lengthFieldWriterIndex);
            prependLengthField.update(byteCounts, hexString, lengthFieldWriterIndex, output.writerIndex());
            output.writerIndex(afterEncode);
        }
    }

    private void doEncodeFieldWithTracker(FieldCodec.SerializeContext context, ByteBuf output, DataField dataField) {
        final CodecTracker codecTracker = Objects.requireNonNull(context.codecTracker());
        final int indexBeforeWrite = output.writerIndex();
        final String name = dataField.name();
        switch (dataField) {
            case DataField.I8 i8 -> output.writeByte(i8.value());
            case DataField.U8 u8 -> output.writeByte(u8.value());
            case DataField.I16 i16 -> output.writeShort(i16.value());
            case DataField.U16 u16 -> output.writeShort(u16.value());
            case DataField.I32 i32 -> output.writeInt(i32.value());
            case DataField.U32 u32 -> output.writeInt(u32.value().intValue());
            case DataField.I64 i64 -> output.writeLong(i64.value());
            case DataField.F32 f32 -> output.writeFloat(f32.value());
            case DataField.F64 f64 -> output.writeDouble(f64.value());
            case DataField.Bcd8421String bcd8421String -> BcdOps.encodeBcd8421StringIntoByteBuf(bcd8421String.value(), output);
            case DataField.HexString hexString -> XtreamBytes.writeHexString(output, hexString.value());
            case DataField.GbkString gbkString -> encodeString(output, gbkString.value(), XtreamConstants.CHARSET_GBK);
            case DataField.Gb2312String gb2312String -> encodeString(output, gb2312String.value(), XtreamConstants.CHARSET_GB_2312);
            case DataField.Utf8String utf8String -> encodeString(output, utf8String.value(), XtreamConstants.CHARSET_UTF8);
            case DataField.GenericString genericString -> encodeString(output, genericString.value(), Charset.forName(genericString.charset()));
            case DataField.ByteSequence byteSequence -> output.writeBytes(byteSequence.value());
            case DataField.Struct struct -> {
                final List<DataField> value = struct.value();
                final DefaultSerializeContext newContext = new DefaultSerializeContext(context, value);
                try (final CodecTracker.TraceScope scope = codecTracker.enterNestedField(name, dataField.type(), this.getClass().getSimpleName(), "", indexBeforeWrite)) {
                    this.encodeWithTracker(newContext, value, output);
                    scope.complete(dataField.value(), output, output.writerIndex());
                }
            }
            case DataField.Sequence sequence -> {
                final List<DataField> value = sequence.value();
                final DefaultSerializeContext newContext = new DefaultSerializeContext(context, value);
                try (final CodecTracker.TraceScope scope = codecTracker.enterCollection(name, dataField.type(), this.getClass().getSimpleName(), "", indexBeforeWrite)) {
                    this.encodeWithTracker(newContext, value, output);
                    scope.complete(dataField.value(), output, output.writerIndex());
                }
            }
            case DataField.Dict<?> simpleMap -> {
                final DefaultSerializeContext newContext = new DefaultSerializeContext(context, simpleMap);
                final Map<? extends DataField.DictKey, DataField> map = simpleMap.value();
                final ByteBuf temp = context.bufferFactory().buffer();
                int sequence = 0;
                try (final CodecTracker.TraceScope mapScope = codecTracker.enterMap(name, dataField.type(), this.getClass().getSimpleName(), "", indexBeforeWrite)) {
                    for (Map.Entry<? extends DataField.DictKey, DataField> entry : map.entrySet()) {
                        final int entryStart = output.writerIndex();
                        try (final CodecTracker.TraceScope entryScope = codecTracker.enterMapEntry(name, sequence++, entryStart)) {

                            // 1. key
                            final DataField.DictKey key = entry.getKey();
                            try (final CodecTracker.NodeOverrideScope ignoredKey = codecTracker.overrideNextMapEntryItem(CodecTracker.MapEntryItemType.KEY)) {
                                this.doEncodeFieldWithTracker(newContext, output, key);
                            }
                            // 2. value
                            final DataField value = entry.getValue();
                            final CodecTracker.TraceCheckpoint valueCheckpoint = codecTracker.checkpoint();
                            try (final CodecTracker.NodeOverrideScope ignoredValue = codecTracker.overrideNextMapEntryItem(CodecTracker.MapEntryItemType.VALUE);
                                    final CodecTracker.TemporaryBufferScope ignoredBuffer = codecTracker.openTemporaryBuffer()) {
                                this.doEncodeFieldWithTracker(newContext, temp, value);
                            }
                            valueCheckpoint.captureNewChildren();
                            // 3. valueLength
                            final int valueLength = temp.writerIndex();
                            try (final CodecTracker.NodeOverrideScope ignoredLength = codecTracker.overrideNextMapEntryItem(CodecTracker.MapEntryItemType.VALUE_LENGTH)) {
                                simpleMap.valueLengthType().writeToWithTracker(output, valueLength, codecTracker, "valueLength");
                            }
                            valueCheckpoint.relocateNewChildren(output.writerIndex());
                            output.writeBytes(temp);
                            entryScope.complete(null, output, output.writerIndex());
                        } finally {
                            temp.clear();
                        }
                    }
                    mapScope.complete(dataField.value(), output, output.writerIndex());
                } finally {
                    temp.release();
                }
            }
            case DataField.TlvDataField tlvDataField -> {
                final DefaultSerializeContext newContext =
                        new DefaultSerializeContext(context, tlvDataField);
                try (final CodecTracker.TraceScope scope = codecTracker.enterNestedField(name, dataField.type(), this.getClass().getSimpleName(), "", indexBeforeWrite)) {
                    // 1. tag
                    final DataField.DictKey tag = tlvDataField.tag();
                    this.doEncodeFieldWithTracker(newContext, output, tag);
                    final ByteBuf temp = context.bufferFactory().buffer();
                    try {
                        // 2. value
                        final DataField value = tlvDataField.value();
                        final CodecTracker.TraceCheckpoint valueCheckpoint = codecTracker.checkpoint();
                        try (final CodecTracker.TemporaryBufferScope ignored = codecTracker.openTemporaryBuffer()) {
                            this.doEncodeFieldWithTracker(newContext, temp, value);
                        }
                        valueCheckpoint.captureNewChildren();

                        // 3. length
                        final int valueLength = temp.writerIndex();
                        tlvDataField.length().writeToWithTracker(output, valueLength, codecTracker, "valueLength");
                        valueCheckpoint.relocateNewChildren(output.writerIndex());
                        output.writeBytes(temp);
                        scope.complete(dataField.value(), output, output.writerIndex());
                    } finally {
                        temp.release();
                    }
                }
            }
            case DataField.CustomDataField customSimpleField -> customSimpleField.writeTo(output);
        }
        if (!(dataField instanceof DataField.Struct)
                && !(dataField instanceof DataField.Dict<?>)
                && !(dataField instanceof DataField.Sequence)
                && !(dataField instanceof DataField.SimpleTlvDataField<?>)) {
            try (final CodecTracker.TraceScope scope = codecTracker.enterField(name, dataField.getClass(), this.getClass(), dataField.getClass().getSimpleName(), indexBeforeWrite)) {
                scope.complete(dataField.value(), output, output.writerIndex());
            }
        }
    }

    private static void encodeString(ByteBuf output, String value, Charset charset) {
        output.writeCharSequence(value, charset);
    }
}
