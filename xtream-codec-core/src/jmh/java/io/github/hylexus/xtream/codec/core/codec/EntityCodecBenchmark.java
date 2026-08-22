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

package io.github.hylexus.xtream.codec.core.codec;

import io.github.hylexus.xtream.codec.core.EntityCodec;
import io.github.hylexus.xtream.codec.core.FieldTransformer;
import io.github.hylexus.xtream.codec.core.annotation.DerivedField;
import io.github.hylexus.xtream.codec.core.annotation.PrependLengthFieldType;
import io.github.hylexus.xtream.codec.core.annotation.EncodedLength;
import io.github.hylexus.xtream.codec.core.type.Preset;
import io.github.hylexus.xtream.codec.core.tracker.CodecTracker;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * EntityCodec 编解码基准测试，覆盖各种数据结构：
 * <ul>
 *     <li>平铺 (Flat) — {@link FlatRecord}</li>
 *     <li>范围长度 ({@link EncodedLength}) — {@link RangeRecord}</li>
 *     <li>派生字段 ({@link DerivedField} 只读) — {@link DerivedRecord}</li>
 *     <li>派生字段逆向回写 ({@link DerivedField} reverseSource) — {@link ReverseDerivedPojo}</li>
 *     <li>内嵌结构 ({@link Preset.RustStyle.struct}) — {@link NestedRecord}</li>
 *     <li>混合字段类型 — {@link MixedRecord}</li>
 * </ul>
 *
 * @since 0.7.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
// 3 轮预热，每轮持续 2 秒
@Warmup(iterations = 3, time = 2)
// 5 轮测量，每轮持续 3 秒
@Measurement(iterations = 5, time = 3)
// 启动 1 个全新的、独立的 JVM 子进程来运行这个基准测试
@Fork(1)
@State(Scope.Thread)
@SuppressWarnings({"unused", "NullAway"})
public class EntityCodecBenchmark {

    private static final EntityCodec CODEC = EntityCodec.DEFAULT;
    private static final ByteBufAllocator ALLOC = ByteBufAllocator.DEFAULT;

    // =========================================================================
    // 实体定义
    // =========================================================================

    /**
     * 1. 平铺 Record (baseline)
     */
    public record FlatRecord(
            @Preset.RustStyle.u8 long fieldU8,
            @Preset.RustStyle.u16 int fieldU16,
            @Preset.RustStyle.u32 long fieldU32,
            @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8) String fieldStr,
            @Preset.RustStyle.i64 long fieldI64,
            @Preset.RustStyle.str(length = 8) String fieldFixedStr
    ) {
    }

    /**
     * 2. @EncodedLength：bodyLen 自动回填 [data, checksum) 的序列化字节数
     */
    public record RangeRecord(
            @Preset.RustStyle.u16 int header,
            @Preset.RustStyle.u16 @EncodedLength(from = "data", until = "checksum") int bodyLen,
            @Preset.RustStyle.u16 int fieldA,
            @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8) String data,
            @Preset.RustStyle.u8 long flag,
            @Preset.RustStyle.u16 int checksum
    ) {
    }

    /**
     * 3. @DerivedField 只读派生
     */
    public static class StatusDisplayTransformer implements FieldTransformer<Long, String> {
        @Override
        public String read(Long source) {
            if (source == null) {
                return null;
            }
            return source == 1L ? "online" : "offline";
        }
    }

    public record DerivedRecord(
            @Preset.RustStyle.u8 long status,
            @DerivedField(source = "status", using = StatusDisplayTransformer.class) String display
    ) {
    }

    /**
     * 4. @DerivedField 逆向回写 (reverseSource=true)，需要可变 POJO
     */
    public static class UpperTransformer implements FieldTransformer<String, String> {
        @Override
        public String read(String source) {
            return source != null ? source.toUpperCase() : null;
        }

        @Override
        public String write(String derived) {
            return derived;
        }
    }

    @SuppressWarnings({"LombokGetterMayBeUsed", "LombokSetterMayBeUsed"})
    public static class ReverseDerivedPojo {
        @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8)
        private String raw;
        @DerivedField(source = "raw", using = UpperTransformer.class, reverseSource = true)
        private String upper;

        public void setRaw(String raw) {
            this.raw = raw;
        }

        public String getRaw() {
            return raw;
        }

        public void setUpper(String upper) {
            this.upper = upper;
        }

        public String getUpper() {
            return upper;
        }
    }

    /**
     * 5. 内嵌结构
     */
    public record InnerRecord(
            @Preset.RustStyle.u8 long innerId,
            @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8) String innerName
    ) {
    }

    public record NestedRecord(
            @Preset.RustStyle.u8 long outerId,
            @Preset.RustStyle.struct InnerRecord inner,
            @Preset.RustStyle.u16 int tail
    ) {
    }

    /**
     * 6. 混合字段类型
     */
    public record MixedRecord(
            @Preset.RustStyle.u8 long fieldU8,
            @Preset.RustStyle.i16 int fieldI16,
            @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u16, charset = "GBK") String fieldGbk,
            @Preset.RustStyle.u32 long fieldU32,
            @Preset.RustStyle.f32 float fieldF32,
            @Preset.RustStyle.str(length = 8) String fieldFixedStr
    ) {
    }

    // =========================================================================
    // 状态 & 初始化
    // =========================================================================

    private FlatRecord flatRecord;
    private RangeRecord rangeRecord;
    private DerivedRecord derivedRecord;
    private ReverseDerivedPojo reverseDerivedPojo;
    private NestedRecord nestedRecord;
    private MixedRecord mixedRecord;

    private byte[] flatBytes;
    private byte[] rangeBytes;
    private byte[] derivedBytes;
    private byte[] reverseDerivedBytes;
    private byte[] nestedBytes;
    private byte[] mixedBytes;

    @Setup(Level.Trial)
    public void setup() {
        flatRecord = new FlatRecord(1, 2, 3L, "hello", 4L, "ABCDEFGH");
        rangeRecord = new RangeRecord(100, 0, 200, "data-content", (byte) 1, 0);
        derivedRecord = new DerivedRecord(1, null);
        reverseDerivedPojo = new ReverseDerivedPojo();
        reverseDerivedPojo.setUpper("HELLO");
        nestedRecord = new NestedRecord(10, new InnerRecord(20, "inner"), 30);
        mixedRecord = new MixedRecord(1, -2, "中文GBK", 3L, 1.5f, "ABCDEFGH");

        flatBytes = encodeToBytes(flatRecord);
        rangeBytes = encodeToBytes(rangeRecord);
        derivedBytes = encodeToBytes(derivedRecord);
        reverseDerivedBytes = encodeToBytes(reverseDerivedPojo);
        nestedBytes = encodeToBytes(nestedRecord);
        mixedBytes = encodeToBytes(mixedRecord);
    }

    private static byte[] encodeToBytes(Object entity) {
        ByteBuf buf = ALLOC.buffer();
        try {
            CODEC.encode(entity, buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }

    // =========================================================================
    // 编码 (encode) 基准测试
    // =========================================================================

    @Benchmark
    public void flatRecordEncode(Blackhole bh) {
        ByteBuf buf = ALLOC.buffer();
        try {
            CODEC.encode(flatRecord, buf);
            bh.consume(buf);
        } finally {
            buf.release();
        }
    }

    @Benchmark
    public void flatRecordTrackedEncode(Blackhole bh) {
        ByteBuf buf = ALLOC.buffer();
        try {
            CODEC.encode(flatRecord, buf, new CodecTracker());
            bh.consume(buf);
        } finally {
            buf.release();
        }
    }

    @Benchmark
    public void rangeRecordEncode(Blackhole bh) {
        ByteBuf buf = ALLOC.buffer();
        try {
            CODEC.encode(rangeRecord, buf);
            bh.consume(buf);
        } finally {
            buf.release();
        }
    }

    @Benchmark
    public void derivedRecordEncode(Blackhole bh) {
        ByteBuf buf = ALLOC.buffer();
        try {
            CODEC.encode(derivedRecord, buf);
            bh.consume(buf);
        } finally {
            buf.release();
        }
    }

    @Benchmark
    public void reverseDerivedEncode(Blackhole bh) {
        ByteBuf buf = ALLOC.buffer();
        try {
            CODEC.encode(reverseDerivedPojo, buf);
            bh.consume(buf);
        } finally {
            buf.release();
        }
    }

    @Benchmark
    public void nestedRecordEncode(Blackhole bh) {
        ByteBuf buf = ALLOC.buffer();
        try {
            CODEC.encode(nestedRecord, buf);
            bh.consume(buf);
        } finally {
            buf.release();
        }
    }

    @Benchmark
    public void mixedRecordEncode(Blackhole bh) {
        ByteBuf buf = ALLOC.buffer();
        try {
            CODEC.encode(mixedRecord, buf);
            bh.consume(buf);
        } finally {
            buf.release();
        }
    }

    // =========================================================================
    // 解码 (decode) 基准测试
    // =========================================================================

    @Benchmark
    public void flatRecordDecode(Blackhole bh) {
        ByteBuf buf = Unpooled.wrappedBuffer(flatBytes);
        try {
            FlatRecord decoded = CODEC.decode(FlatRecord.class, buf);
            bh.consume(decoded);
        } finally {
            buf.release();
        }
    }

    @Benchmark
    public void flatRecordTrackedDecode(Blackhole bh) {
        ByteBuf buf = Unpooled.wrappedBuffer(flatBytes);
        try {
            FlatRecord decoded = CODEC.decode(FlatRecord.class, buf, new CodecTracker());
            bh.consume(decoded);
        } finally {
            buf.release();
        }
    }

    @Benchmark
    public void rangeRecordDecode(Blackhole bh) {
        ByteBuf buf = Unpooled.wrappedBuffer(rangeBytes);
        try {
            RangeRecord decoded = CODEC.decode(RangeRecord.class, buf);
            bh.consume(decoded);
        } finally {
            buf.release();
        }
    }

    @Benchmark
    public void derivedRecordDecode(Blackhole bh) {
        ByteBuf buf = Unpooled.wrappedBuffer(derivedBytes);
        try {
            DerivedRecord decoded = CODEC.decode(DerivedRecord.class, buf);
            bh.consume(decoded);
        } finally {
            buf.release();
        }
    }

    @Benchmark
    public void reverseDerivedDecode(Blackhole bh) {
        ByteBuf buf = Unpooled.wrappedBuffer(reverseDerivedBytes);
        try {
            ReverseDerivedPojo decoded = CODEC.decode(ReverseDerivedPojo.class, buf);
            bh.consume(decoded);
        } finally {
            buf.release();
        }
    }

    @Benchmark
    public void nestedRecordDecode(Blackhole bh) {
        ByteBuf buf = Unpooled.wrappedBuffer(nestedBytes);
        try {
            NestedRecord decoded = CODEC.decode(NestedRecord.class, buf);
            bh.consume(decoded);
        } finally {
            buf.release();
        }
    }

    @Benchmark
    public void mixedRecordDecode(Blackhole bh) {
        ByteBuf buf = Unpooled.wrappedBuffer(mixedBytes);
        try {
            MixedRecord decoded = CODEC.decode(MixedRecord.class, buf);
            bh.consume(decoded);
        } finally {
            buf.release();
        }
    }
}
