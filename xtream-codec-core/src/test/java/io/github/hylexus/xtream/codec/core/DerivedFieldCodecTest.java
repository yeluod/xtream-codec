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

import io.github.hylexus.xtream.codec.BaseEntityCodecTest;
import io.github.hylexus.xtream.codec.common.utils.BitFlag;
import io.github.hylexus.xtream.codec.common.utils.EnumSetBitTransformer;
import io.github.hylexus.xtream.codec.core.annotation.DerivedField;
import io.github.hylexus.xtream.codec.core.annotation.PrependLengthFieldType;
import io.github.hylexus.xtream.codec.core.annotation.XtreamField;
import io.github.hylexus.xtream.codec.core.type.Preset;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hylexus
 * @author opencode (AI)
 */
public class DerivedFieldCodecTest extends BaseEntityCodecTest {

    // ========== 第1组: 基础 S→T 转换(无逆向) ==========
    public static class StatusDisplayTransformer implements FieldTransformer<Long, String> {
        @Override
        public String read(Long source) {
            if (source == null) {
                return null;
            }
            if (source == 0L) {
                return "offline";
            }
            if (source == 1L) {
                return "online";
            }
            return "unknown:" + source;
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ToString
    public static class BasicEntity {
        @Preset.RustStyle.u8
        private long status;

        @DerivedField(source = "status", using = StatusDisplayTransformer.class)
        private String statusDisplay;
    }

    @Test
    void testBasicTransform() {
        final BasicEntity entity = new BasicEntity().setStatus(1);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals("online", decoded.statusDisplay);
            assertEquals(source.status, decoded.status);
        }, false);
    }

    @Test
    void testBasicTransformWithTracker() {
        final BasicEntity entity = new BasicEntity().setStatus(0);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals("offline", decoded.statusDisplay);
            assertEquals(source.status, decoded.status);
        }, true);
    }

    // ========== 第2组: BitFlag/EnumSet 转换 ==========
    public enum AlarmBit implements BitFlag {
        EMERGENCY(0),
        LOW_BATTERY(1),
        OVER_TEMP(2);

        private final int offset;

        AlarmBit(int offset) {
            this.offset = offset;
        }

        @Override
        public int bitOffset() {
            return offset;
        }
    }

    public static class AlarmBitTransformer extends EnumSetBitTransformer<AlarmBit> {
        public AlarmBitTransformer() {
            super(AlarmBit.class);
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ToString
    public static class BitFlagEntity {
        @Preset.RustStyle.u16
        private int alarm;

        @DerivedField(source = "alarm", using = AlarmBitTransformer.class)
        private Set<AlarmBit> alarmFlags;
    }

    @Test
    void testBitFlagTransform() {
        // alarm=0x03 → 第0位和第1位置位 → EMERGENCY + LOW_BATTERY
        final BitFlagEntity entity = new BitFlagEntity().setAlarm(0x03);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertNotNull(decoded.alarmFlags);
            assertEquals(2, decoded.alarmFlags.size());
            assertTrue(decoded.alarmFlags.contains(AlarmBit.EMERGENCY));
            assertTrue(decoded.alarmFlags.contains(AlarmBit.LOW_BATTERY));
            assertFalse(decoded.alarmFlags.contains(AlarmBit.OVER_TEMP));
        }, false);
    }

    @Test
    void testBitFlagTransformEmpty() {
        final BitFlagEntity entity = new BitFlagEntity().setAlarm(0);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertNotNull(decoded.alarmFlags);
            assertTrue(decoded.alarmFlags.isEmpty());
        }, false);
    }

    // ========== 第3组: 多个派生字段指向同一数据源 ==========
    public static class StatusCodeTransformer implements FieldTransformer<Long, String> {
        @Override
        public String read(Long source) {
            if (source == null) {
                return null;
            }
            if (source == 0L) {
                return "OK";
            }
            if (source == 1L) {
                return "WARN";
            }
            return "ERR";
        }
    }

    public static class StatusDescriptionTransformer implements FieldTransformer<Long, String> {
        @Override
        public String read(Long source) {
            if (source == null) {
                return null;
            }
            if (source == 0L) {
                return "All systems normal";
            }
            if (source == 1L) {
                return "Degraded performance";
            }
            return "Critical failure";
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ToString
    public static class MultiDerivedEntity {
        @Preset.RustStyle.u8
        private long code;

        @DerivedField(source = "code", using = StatusCodeTransformer.class, reverseSource = false)
        private String codeLabel;

        @DerivedField(source = "code", using = StatusDescriptionTransformer.class, reverseSource = false)
        private String codeDesc;
    }

    @Test
    void testMultipleDerivedFromSameSource() {
        final MultiDerivedEntity entity = new MultiDerivedEntity().setCode(1);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals("WARN", decoded.codeLabel);
            assertEquals("Degraded performance", decoded.codeDesc);
        }, false);
    }

    // ========== 第4组: reverseSource=true 编解码回环 ==========
    public static class UpperCaseTransformer implements FieldTransformer<String, String> {
        @Override
        public String read(String source) {
            return source == null ? null : source.toUpperCase();
        }

        @Override
        public String write(String derived) {
            return derived == null ? null : derived.toLowerCase();
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ToString
    public static class RoundTripEntity {
        @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8)
        private String raw;

        @DerivedField(source = "raw", using = UpperCaseTransformer.class, reverseSource = true)
        private String upper;
    }

    @Test
    void testRoundTrip() {
        // 只设置派生字段；编码时 reverseDerivedPass 应将值回写到 raw
        final RoundTripEntity entity = new RoundTripEntity().setUpper("HELLO");
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            // raw 在编码时由 reverseDerivedPass 设置（"HELLO".toLowerCase）
            // 解码后 upper 由 transform 重新计算得出
            assertEquals("hello", decoded.raw);
            assertEquals("HELLO", decoded.upper);
        }, false);
    }

    // ========== 第5组: reverseSource 冲突校验 ==========
    @Getter
    @Setter
    public static class ConflictEntity {
        @Preset.RustStyle.u8
        private long src;

        @DerivedField(source = "src", using = StatusDisplayTransformer.class, reverseSource = true)
        private String derived1;

        @DerivedField(source = "src", using = StatusDisplayTransformer.class, reverseSource = true)
        private String derived2;
    }

    @Test
    void testReverseSourceConflictThrows() {
        final ConflictEntity entity = new ConflictEntity();
        assertThrows(IllegalArgumentException.class, () ->
                entityCodec.encode(XtreamField.ALL_VERSION, entity, allocator.buffer())
        );
    }

    // ========== 第6组: @DerivedField 与 @XtreamField 共存 ==========
    public static class SimpleEncoder implements FieldTransformer<Long, Long> {
        @Override
        public Long read(Long source) {
            return source == null ? null : source + 100;
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ToString
    public static class CoexistenceEntity {
        @Preset.RustStyle.u8
        private long raw;

        // 两个注解同时存在时: @XtreamField 优先
        @Preset.RustStyle.u8
        @DerivedField(source = "raw", using = SimpleEncoder.class)
        private long derived;
    }

    @Test
    void testCoexistenceXtreamFieldTakesPrecedence() {
        final CoexistenceEntity entity = new CoexistenceEntity().setRaw(42).setDerived(99);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            // raw 为普通字段: 42
            assertEquals(42, decoded.raw);
            // derived 上有 @XtreamField(u8)，按普通字段编解码，@DerivedField 被忽略
            // 值 99 应能完整回环
            assertEquals(99, decoded.derived);
        }, false);
    }

    // ========== 第7组: 数据源字段为 null 时的处理 ==========
    public static class NullSourceTransformer implements FieldTransformer<String, String> {
        @Override
        public String read(String source) {
            return source == null ? "DEFAULT" : source + "_transformed";
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ToString
    public static class ConditionalEntity {
        @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8, condition = "false")
        private String conditional;

        @DerivedField(source = "conditional", using = NullSourceTransformer.class, reverseSource = false)
        private String derived;
    }

    @Test
    void testNullSourceHandling() {
        // conditional 字段不会被解码(condition = "false")，值为 null
        // applyDerivedPass 应在数据源为 null 时跳过派生字段
        final ConditionalEntity entity = new ConditionalEntity();
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertNull(decoded.conditional);
            assertNull(decoded.derived);
        }, false);
    }

    // ========== 第8组: 内嵌普通类中的派生字段 ==========

    @Getter
    @Setter
    @Accessors(chain = true)
    @ToString
    public static class OuterWithInnerClassEntity {
        @Preset.RustStyle.u8
        private int outerStatus;

        @Preset.RustStyle.struct
        private InnerClassEntity inner;

        public OuterWithInnerClassEntity() {
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ToString
    public static class InnerClassEntity {
        @Preset.RustStyle.u8
        private long level;

        @DerivedField(source = "level", using = StatusDisplayTransformer.class)
        private String levelDisplay;

        public InnerClassEntity() {
        }
    }

    @Test
    void testNestedInnerClassDerivedField() {
        final InnerClassEntity inner = new InnerClassEntity().setLevel(2);
        final OuterWithInnerClassEntity entity = new OuterWithInnerClassEntity()
                .setOuterStatus(1)
                .setInner(inner);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.outerStatus, decoded.outerStatus);
            assertEquals(2L, decoded.inner.level);
            assertEquals("unknown:2", decoded.inner.levelDisplay);
        }, false);
    }

    @Test
    void testNestedInnerClassDerivedFieldWithTracker() {
        final InnerClassEntity inner = new InnerClassEntity().setLevel(0);
        final OuterWithInnerClassEntity entity = new OuterWithInnerClassEntity()
                .setOuterStatus(1)
                .setInner(inner);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.outerStatus, decoded.outerStatus);
            assertEquals(0L, decoded.inner.level);
            assertEquals("offline", decoded.inner.levelDisplay);
        }, true);
    }

    // ========== 第9组: 内嵌 Record 中的派生字段 ==========

    public record OuterWithNestedRecordEntity(
            @Preset.RustStyle.u8 int outerStatus,
            @Preset.RustStyle.struct NestedRecordEntity inner
    ) {
    }

    public record NestedRecordEntity(
            @Preset.RustStyle.u8 long level,
            @DerivedField(source = "level", using = StatusDisplayTransformer.class) String levelDisplay
    ) {
    }

    @Test
    void testNestedRecordDerivedField() {
        final OuterWithNestedRecordEntity entity = new OuterWithNestedRecordEntity(1, new NestedRecordEntity(2, null));
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.outerStatus(), decoded.outerStatus());
            assertEquals(2L, decoded.inner().level());
            assertEquals("unknown:2", decoded.inner().levelDisplay());
        }, false);
    }

    @Test
    void testNestedRecordDerivedFieldWithTracker() {
        final OuterWithNestedRecordEntity entity = new OuterWithNestedRecordEntity(0, new NestedRecordEntity(1, null));
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.outerStatus(), decoded.outerStatus());
            assertEquals(1L, decoded.inner().level());
            assertEquals("online", decoded.inner().levelDisplay());
        }, true);
    }

    // ========== 第10组: 内嵌普通类中的 reverseSource 回环 ==========

    @Getter
    @Setter
    @Accessors(chain = true)
    @ToString
    public static class InnerWithReverseEntity {
        @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8)
        private String raw;

        @DerivedField(source = "raw", using = UpperCaseTransformer.class, reverseSource = true)
        private String upper;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ToString
    public static class OuterWithInnerReverseEntity {
        @Preset.RustStyle.u8
        private int outerStatus;

        @Preset.RustStyle.struct
        private InnerWithReverseEntity inner;
    }

    @Test
    void testNestedInnerClassReverseSource() {
        final InnerWithReverseEntity inner = new InnerWithReverseEntity().setUpper("HELLO");
        final OuterWithInnerReverseEntity entity = new OuterWithInnerReverseEntity()
                .setOuterStatus(1)
                .setInner(inner);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.outerStatus, decoded.outerStatus);
            assertEquals("hello", decoded.inner.raw);
            assertEquals("HELLO", decoded.inner.upper);
        }, false);
    }

    @Test
    void testNestedInnerClassReverseSourceWithTracker() {
        final InnerWithReverseEntity inner = new InnerWithReverseEntity().setUpper("HELLO");
        final OuterWithInnerReverseEntity entity = new OuterWithInnerReverseEntity()
                .setOuterStatus(2)
                .setInner(inner);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.outerStatus, decoded.outerStatus);
            assertEquals("hello", decoded.inner.raw);
            assertEquals("HELLO", decoded.inner.upper);
        }, true);
    }

    @Test
    void testNestedInnerClassReverseSourceSourceValueIgnored() {
        // source 和派生字段都有值 → reverseSource 应优先使用派生字段
        final InnerWithReverseEntity inner = new InnerWithReverseEntity()
                .setRaw("原始值")
                .setUpper("HELLO");
        final OuterWithInnerReverseEntity entity = new OuterWithInnerReverseEntity()
                .setOuterStatus(1)
                .setInner(inner);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals("hello", decoded.inner.raw);
            assertEquals("HELLO", decoded.inner.upper);
        }, false);
    }

    @Test
    void testNestedInnerClassReverseSourceSourceValueIgnoredWithTracker() {
        final InnerWithReverseEntity inner = new InnerWithReverseEntity()
                .setRaw("原始值")
                .setUpper("HELLO");
        final OuterWithInnerReverseEntity entity = new OuterWithInnerReverseEntity()
                .setOuterStatus(1)
                .setInner(inner);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals("hello", decoded.inner.raw);
            assertEquals("HELLO", decoded.inner.upper);
        }, true);
    }

    // ========== 第11组: 内嵌 Record 中的 reverseSource 回环 ==========

    public record NestedRecordReverseEntity(
            @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8) String raw,
            @DerivedField(source = "raw", using = UpperCaseTransformer.class, reverseSource = true) String upper
    ) {
    }

    public record OuterWithNestedRecordReverseEntity(
            @Preset.RustStyle.u8 int outerStatus,
            @Preset.RustStyle.struct NestedRecordReverseEntity inner
    ) {
    }

    @Test
    void testNestedRecordReverseSource() {
        final OuterWithNestedRecordReverseEntity entity = new OuterWithNestedRecordReverseEntity(
                1, new NestedRecordReverseEntity(null, "HELLO")
        );
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.outerStatus(), decoded.outerStatus());
            assertEquals("hello", decoded.inner().raw());
            assertEquals("HELLO", decoded.inner().upper());
        }, false);
    }

    @Test
    void testNestedRecordReverseSourceWithTracker() {
        final OuterWithNestedRecordReverseEntity entity = new OuterWithNestedRecordReverseEntity(
                2, new NestedRecordReverseEntity(null, "WORLD")
        );
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.outerStatus(), decoded.outerStatus());
            assertEquals("world", decoded.inner().raw());
            assertEquals("WORLD", decoded.inner().upper());
        }, true);
    }

    // ========== 第12组: 三层嵌套（Outer → Middle → Inner） ==========

    @Getter
    @Setter
    @Accessors(chain = true)
    @ToString
    public static class ThreeLevelInnerEntity {
        @Preset.RustStyle.u8
        private long innerLevel;

        @DerivedField(source = "innerLevel", using = StatusDisplayTransformer.class)
        private String innerDisplay;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ToString
    public static class ThreeLevelMiddleEntity {
        @Preset.RustStyle.u8
        private long middleLevel;

        @DerivedField(source = "middleLevel", using = StatusDisplayTransformer.class)
        private String middleDisplay;

        @Preset.RustStyle.struct
        private ThreeLevelInnerEntity inner;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ToString
    public static class ThreeLevelOuterEntity {
        @Preset.RustStyle.u8
        private int outerLevel;

        @Preset.RustStyle.struct
        private ThreeLevelMiddleEntity middle;
    }

    @Test
    void testThreeLevelNestedDerivedField() {
        final ThreeLevelInnerEntity inner = new ThreeLevelInnerEntity().setInnerLevel(3);
        final ThreeLevelMiddleEntity middle = new ThreeLevelMiddleEntity()
                .setMiddleLevel(2)
                .setInner(inner);
        final ThreeLevelOuterEntity entity = new ThreeLevelOuterEntity()
                .setOuterLevel(1)
                .setMiddle(middle);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(1, decoded.outerLevel);
            assertEquals(2L, decoded.middle.middleLevel);
            assertEquals("unknown:2", decoded.middle.middleDisplay);
            assertEquals(3L, decoded.middle.inner.innerLevel);
            assertEquals("unknown:3", decoded.middle.inner.innerDisplay);
        }, false);
    }

    @Test
    void testThreeLevelNestedDerivedFieldWithTracker() {
        final ThreeLevelInnerEntity inner = new ThreeLevelInnerEntity().setInnerLevel(7);
        final ThreeLevelMiddleEntity middle = new ThreeLevelMiddleEntity()
                .setMiddleLevel(5)
                .setInner(inner);
        final ThreeLevelOuterEntity entity = new ThreeLevelOuterEntity()
                .setOuterLevel(1)
                .setMiddle(middle);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(1, decoded.outerLevel);
            assertEquals(5L, decoded.middle.middleLevel);
            assertEquals("unknown:5", decoded.middle.middleDisplay);
            assertEquals(7L, decoded.middle.inner.innerLevel);
            assertEquals("unknown:7", decoded.middle.inner.innerDisplay);
        }, true);
    }
}
