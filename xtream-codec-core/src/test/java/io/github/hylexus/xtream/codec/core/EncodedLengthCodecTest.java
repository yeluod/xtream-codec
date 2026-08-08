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
import io.github.hylexus.xtream.codec.core.annotation.DerivedField;
import io.github.hylexus.xtream.codec.core.annotation.EncodedLength;
import io.github.hylexus.xtream.codec.core.annotation.PrependLengthFieldType;
import io.github.hylexus.xtream.codec.core.annotation.XtreamField;
import io.github.hylexus.xtream.codec.core.type.Preset;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncodedLengthCodecTest extends BaseEntityCodecTest {

    // ========== 平面单类 roundtrip（显式 from/until） ==========

    @Getter
    @Setter
    @ToString
    @Accessors(chain = true)
    public static class FlatEntity {
        @Preset.RustStyle.u16
        private int version;

        @Preset.RustStyle.u16
        private int messageType;

        @Preset.RustStyle.u16
        @EncodedLength(from = "username", until = "checkSum")
        private int bodyLength;

        @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8)
        private String username;

        @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8)
        private String password;

        @Preset.JtStyle.BcdDateTime
        private LocalDateTime birthDay;

        @Preset.RustStyle.u16
        private int checkSum;
    }

    @Test
    void testFlatEntityRoundTrip() {
        final FlatEntity entity = new FlatEntity()
                .setVersion(1)
                .setMessageType(128)
                .setBodyLength(0)
                .setUsername("hello")
                .setPassword("world")
                .setBirthDay(LocalDateTime.of(2025, 1, 15, 10, 30, 0))
                .setCheckSum(0);

        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.version, decoded.version);
            assertEquals(source.messageType, decoded.messageType);
            assertEquals(source.username, decoded.username);
            assertEquals(source.password, decoded.password);
            assertEquals(source.birthDay, decoded.birthDay);
            assertEquals(source.checkSum, decoded.checkSum);
            // bodyLength 应为 username + password + birthDay 的编码字节数
            // username: 1(u8 len) + 5("hello") = 6
            // password: 1(u8 len) + 5("world") = 6
            // birthDay: 6 bytes BCD DateTime
            // total: 6 + 6 + 6 = 18
            assertEquals(18, decoded.bodyLength);
        }, false);
    }

    @Test
    void testFlatEntityRoundTripWithTracker() {
        final FlatEntity entity = new FlatEntity()
                .setVersion(1)
                .setMessageType(128)
                .setBodyLength(0)
                .setUsername("hello")
                .setPassword("world")
                .setBirthDay(LocalDateTime.of(2025, 1, 15, 10, 30, 0))
                .setCheckSum(0);

        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.version, decoded.version);
            assertEquals(source.messageType, decoded.messageType);
            assertEquals(source.username, decoded.username);
            assertEquals(source.password, decoded.password);
            assertEquals(source.birthDay, decoded.birthDay);
            assertEquals(source.checkSum, decoded.checkSum);
            assertEquals(18, decoded.bodyLength);
        }, true);
    }

    // ========== 继承场景（父类 @EncodedLength、子类 body 字段） ==========

    @Getter
    @Setter
    public abstract static class AbstractMessage {
        @Preset.RustStyle.u16(order = -999)
        private int version;

        @Preset.RustStyle.u16(order = -888)
        private int messageType;

        @Preset.RustStyle.u16(order = -777)
        @EncodedLength(until = "checkSum")
        private int bodyLength;

        @Preset.RustStyle.u16(order = 99999)
        private int checkSum;
    }

    @Getter
    @Setter
    public static class ConcreteMessage extends AbstractMessage {
        @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8)
        private String name;

        @Preset.RustStyle.u16
        private int age;
    }

    @Test
    void testInheritanceRoundTrip() {
        final ConcreteMessage entity = new ConcreteMessage();
        entity.setVersion(2);
        entity.setMessageType(256);
        entity.setBodyLength(0);
        entity.setName("Alice");
        entity.setAge(30);
        entity.setCheckSum(999);

        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.getVersion(), decoded.getVersion());
            assertEquals(source.getMessageType(), decoded.getMessageType());
            assertEquals(source.getName(), decoded.getName());
            assertEquals(source.getAge(), decoded.getAge());
            assertEquals(source.getCheckSum(), decoded.getCheckSum());
            // name: 1(u8 len) + 5("Alice") = 6
            // age: 2 bytes (u16)
            // total: 6 + 2 = 8
            assertEquals(8, decoded.getBodyLength());
        }, false);
    }

    // ========== from 空值（范围从当前字段的下一个开始） ==========

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class FromEmptyEntity {
        @Preset.RustStyle.u16
        @EncodedLength(until = "endField")
        private int bodyLen;

        @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8)
        private String data;

        @Preset.RustStyle.u16
        private int endField;
    }

    @Test
    void testFromEmpty() {
        final FromEmptyEntity entity = new FromEmptyEntity()
                .setBodyLen(0)
                .setData("test")
                .setEndField(0);

        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.data, decoded.data);
            assertEquals(source.endField, decoded.endField);
            // data: 1(u8 len) + 4("test") = 5
            assertEquals(5, decoded.bodyLen);
        }, false);
    }

    // ========== until 空值（范围到最后一个字段） ==========

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class UntilEmptyEntity {
        @Preset.RustStyle.u16
        @EncodedLength(from = "fieldA")
        private int bodyLen;

        @Preset.RustStyle.u16
        private int fieldA;

        @Preset.RustStyle.u16
        private int fieldB;
    }

    @Test
    void testUntilEmpty() {
        final UntilEmptyEntity entity = new UntilEmptyEntity()
                .setBodyLen(0)
                .setFieldA(100)
                .setFieldB(200);

        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.fieldA, decoded.fieldA);
            assertEquals(source.fieldB, decoded.fieldB);
            // fieldA: 2, fieldB: 2, total: 4
            assertEquals(4, decoded.bodyLen);
        }, false);
    }

    // ========== from 和 until 皆空（全部 body 字段） ==========

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class BothEmptyEntity {
        @Preset.RustStyle.u16
        @EncodedLength
        private int bodyLen;

        @Preset.RustStyle.u8
        private int valA;

        @Preset.RustStyle.u16
        private int valB;
    }

    @Test
    void testBothEmpty() {
        final BothEmptyEntity entity = new BothEmptyEntity()
                .setBodyLen(0)
                .setValA(10)
                .setValB(20);

        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.valA, decoded.valA);
            assertEquals(source.valB, decoded.valB);
            // valA: 1, valB: 2, total: 3
            assertEquals(3, decoded.bodyLen);
        }, false);
    }

    // ========== 元数据校验失败测试 ==========

    @Getter
    @Setter
    public static class InvalidFromEntity {
        @Preset.RustStyle.u16
        @EncodedLength(from = "nonExistent")
        private int bodyLen;

        @Preset.RustStyle.u8
        private int fieldA;
    }

    @Test
    void testInvalidFromFieldThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> entityCodec.encode(new InvalidFromEntity(), allocator.buffer()));
    }

    @Getter
    @Setter
    public static class InvalidUntilEntity {
        @Preset.RustStyle.u16
        @EncodedLength(until = "nonExistent")
        private int bodyLen;

        @Preset.RustStyle.u8
        private int fieldA;
    }

    @Test
    void testInvalidUntilFieldThrows() {
        assertThrows(IllegalArgumentException.class, () -> entityCodec.encode(new InvalidUntilEntity(), allocator.buffer()));
    }

    @Getter
    @Setter
    public static class FromEqualsUntilEntity {
        @Preset.RustStyle.u16
        @EncodedLength(from = "foo", until = "foo")
        private int bodyLen;

        @Preset.RustStyle.u8
        private int foo;
    }

    @Test
    void testFromEqualsUntilThrows() {
        assertThrows(IllegalArgumentException.class, () -> entityCodec.encode(new FromEqualsUntilEntity(), allocator.buffer()));
    }

    @Getter
    @Setter
    public static class FromAfterUntilEntity {
        @Preset.RustStyle.u16
        @EncodedLength(from = "fieldB", until = "fieldA")
        private int bodyLen;

        @Preset.RustStyle.u8(order = 1)
        private int fieldA;

        @Preset.RustStyle.u8(order = 2)
        private int fieldB;
    }

    @Test
    void testFromAfterUntilThrows() {
        assertThrows(IllegalArgumentException.class, () -> entityCodec.encode(new FromAfterUntilEntity(), allocator.buffer()));
    }

    // ========== 多个 @EncodedLength 被拒绝 ==========

    @Getter
    @Setter
    public static class MultipleEncodedLengthEntity {
        @Preset.RustStyle.u16
        @EncodedLength(until = "innerLen")
        private int outerLen;

        @Preset.RustStyle.u16
        @EncodedLength(until = "innerEnd")
        private int innerLen;

        @Preset.RustStyle.u8
        private int innerEnd;

        @Preset.RustStyle.u8
        private int outerEnd;
    }

    @Test
    void testMultipleEncodedLengthThrows() {
        assertThrows(IllegalArgumentException.class, () -> entityCodec.encode(new MultipleEncodedLengthEntity(), allocator.buffer()));
    }

    // ========== u8 和 u32 类型的 EncodedLength ==========

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class U8RangeEntity {
        @Preset.RustStyle.u8
        @EncodedLength(until = "endField")
        private int bodyLen;

        @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8)
        private String data;

        @Preset.RustStyle.u16
        private int endField;
    }

    @Test
    void testU8EncodedLength() {
        final U8RangeEntity entity = new U8RangeEntity()
                .setBodyLen(0)
                .setData("ab")
                .setEndField(0);

        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(3, decoded.bodyLen);
        }, false);
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class U32RangeEntity {
        @Preset.RustStyle.u32
        @EncodedLength(until = "endField")
        private long bodyLen;

        @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8)
        private String data;

        @Preset.RustStyle.u16
        private int endField;
    }

    @Test
    void testU32EncodedLength() {
        final U32RangeEntity entity = new U32RangeEntity()
                .setBodyLen(0)
                .setData("xyz")
                .setEndField(0);

        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(4, decoded.bodyLen);
        }, false);
    }

    // ========== 条件字段或 null 字段按实际写出字节计算 ==========

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class SkippedFieldEntity {
        @Preset.RustStyle.u16
        @EncodedLength(until = "endField")
        private int bodyLen;

        @Preset.RustStyle.u8
        private int fieldA;

        @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8, condition = "false")
        private String skippedNull;

        @Preset.RustStyle.u16(condition = "false")
        private int skippedConditional;

        @Preset.RustStyle.u16
        private int fieldB;

        @Preset.RustStyle.u16
        private int endField;
    }

    @Test
    void testSkippedOrNullFieldsUseActualEncodedBytes() {
        final SkippedFieldEntity entity = new SkippedFieldEntity()
                .setBodyLen(0)
                .setFieldA(1)
                .setSkippedNull(null)
                .setSkippedConditional(2)
                .setFieldB(3)
                .setEndField(4);

        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(source.fieldA, decoded.fieldA);
            assertEquals(source.fieldB, decoded.fieldB);
            assertEquals(source.endField, decoded.endField);
            // fieldA: 1, fieldB: 2, skippedNull/skippedConditional: 0
            assertEquals(3, decoded.bodyLen);
        }, false);
    }

    // ========== 不支持的长度字段格式 ==========

    @Getter
    @Setter
    public static class SignedLengthEntity {
        @Preset.RustStyle.i16
        @EncodedLength
        private int bodyLen;

        @Preset.RustStyle.u8
        private int fieldA;
    }

    @Getter
    @Setter
    public static class LittleEndianLengthEntity {
        @Preset.RustStyle.u16_le
        @EncodedLength
        private int bodyLen;

        @Preset.RustStyle.u8
        private int fieldA;
    }

    @Test
    void testUnsupportedLengthFieldFormatThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> entityCodec.encode(new SignedLengthEntity(), allocator.buffer()));
        assertThrows(IllegalArgumentException.class,
                () -> entityCodec.encode(new LittleEndianLengthEntity(), allocator.buffer()));
    }

    // ========== @DerivedField 与 @EncodedLength 冲突 ==========

    public static class NullTransformer implements FieldTransformer<Integer, String> {
        @Override
        public String read(Integer source) {
            return null;
        }
    }

    @Getter
    @Setter
    public static class DerivedWithEncodedLengthEntity {
        @Preset.RustStyle.u8
        private int sourceField;

        @DerivedField(source = "sourceField", using = NullTransformer.class)
        @EncodedLength
        private String conflictField;
    }

    @Test
    void testDerivedFieldWithEncodedLengthThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> entityCodec.encode(new DerivedWithEncodedLengthEntity(), allocator.buffer()));
    }
}
