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

package io.github.hylexus.xtream.codec.core.utils;

import io.github.hylexus.xtream.codec.common.utils.BitFlag;
import io.github.hylexus.xtream.codec.common.utils.EnumSetBitTransformer;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author hylexus
 * @author opencode (AI)
 */
class EnumSetBitTransformerTest {

    enum TestBitRange implements BitFlag {
        // 单 bit 常量（bit 被置位即匹配，bitValue 仅用于多 bit range）
        BIT_0(0, 1, 1),
        BIT_1(1, 1, 1),
        BIT_2(2, 1, 1),

        // 多 bit range（offset=8, length=2），不同 bitValue
        R8_00(8, 2, 0b00),
        R8_01(8, 2, 0b01),
        R8_10(8, 2, 0b10),
        R8_11(8, 2, 0b11),

        // 多 bit range（offset=12, length=3），不同 bitValue
        R12_000(12, 3, 0b000),
        R12_101(12, 3, 0b101);

        private final int offset;
        private final int length;
        private final int bitValue;

        TestBitRange(int offset, int length, int bitValue) {
            this.offset = offset;
            this.length = length;
            this.bitValue = bitValue;
        }

        @Override
        public int bitOffset() {
            return offset;
        }

        @Override
        public int bitLength() {
            return length;
        }

        @Override
        public int bitValue() {
            return bitValue;
        }
    }

    private final EnumSetBitTransformer<TestBitRange> transformer = new EnumSetBitTransformer<>(TestBitRange.class) {
    };

    // ========== write() ==========

    @Test
    void singleBitWrite() {
        // BIT_0(offset=0) + BIT_2(offset=2) → 0b101
        final Set<TestBitRange> set = EnumSet.of(TestBitRange.BIT_0, TestBitRange.BIT_2);
        assertEquals(0b101L, transformer.write(set).longValue());
    }

    @Test
    void multiBitCleanWrite() {
        // R8_10(offset=8, length=2, bitValue=0b10) → bits 8-9 = 0b10
        final Set<TestBitRange> set = EnumSet.of(TestBitRange.R8_10);
        assertEquals(0b10L << 8, transformer.write(set).longValue());
    }

    @Test
    void multiBitOverwriteSameRange() {
        // 先设 R8_11 (bits 8-9 = 0b11)，再换成 R8_01 (bits 8-9 = 0b01)
        // 修复前 |= 会残留旧值 0b11，修复后应清除旧位得到 0b01
        final Set<TestBitRange> set = EnumSet.of(TestBitRange.R8_11);
        set.remove(TestBitRange.R8_11);
        set.add(TestBitRange.R8_01);
        assertEquals(0b01L << 8, transformer.write(set).longValue());
    }

    @Test
    void multiBitOverwriteWithOtherBitsPreserved() {
        // BIT_2(offset=2) + R8_11(bits 8-9 = 0b11) → 换 R8_01 → BIT_2 不受影响
        final Set<TestBitRange> set = EnumSet.of(TestBitRange.BIT_2, TestBitRange.R8_11);
        set.remove(TestBitRange.R8_11);
        set.add(TestBitRange.R8_01);
        final long expected = (1L << 2) | (0b01L << 8);
        assertEquals(expected, transformer.write(set).longValue());
    }

    @Test
    void mixedSingleAndMultiBitWrite() {
        // BIT_0 + BIT_1 + R8_11(0b11 at bits 8-9)
        final Set<TestBitRange> set = EnumSet.of(
                TestBitRange.BIT_0, TestBitRange.BIT_1, TestBitRange.R8_11
        );
        final long expected = (1L << 0) | (1L << 1) | (0b11L << 8);
        assertEquals(expected, transformer.write(set).longValue());
    }

    @Test
    void multipleNonOverlappingRanges() {
        // R8_10(bits 8-9 = 0b10) + R12_101(bits 12-14 = 0b101)
        final Set<TestBitRange> set = EnumSet.of(
                TestBitRange.R8_10, TestBitRange.R12_101
        );
        final long expected = (0b10L << 8) | (0b101L << 12);
        assertEquals(expected, transformer.write(set).longValue());
    }

    @Test
    void writeNullReturnsNull() {
        assertNull(transformer.write(null));
    }

    @Test
    void writeEmptySetReturnsZero() {
        assertEquals(0L, transformer.write(EnumSet.noneOf(TestBitRange.class)).longValue());
    }

    // ========== read() ==========

    @Test
    void readForwardSingleBit() {
        // BIT_0 + BIT_2；未触及的 range 零位也会匹配 bitValue=0 的常量
        final long val = 0b101L;
        assertEquals(
                Set.of(TestBitRange.BIT_0, TestBitRange.BIT_2,
                        TestBitRange.R8_00, TestBitRange.R12_000),
                transformer.read(val)
        );
    }

    @Test
    void readForwardMultiBitRange() {
        // bits 8-9 = 0b10 → R8_10；R12_000 因 bits 12-14=0 匹配
        final long val = 0b10L << 8;
        assertEquals(
                Set.of(TestBitRange.R8_10, TestBitRange.R12_000),
                transformer.read(val)
        );
    }

    @Test
    void readForwardMixed() {
        // BIT_0 + BIT_2 + R8_10 + R12_000(bits 12-14=0)
        final long val = (1L << 0) | (1L << 2) | (0b10L << 8);
        assertEquals(
                Set.of(TestBitRange.BIT_0, TestBitRange.BIT_2,
                        TestBitRange.R8_10, TestBitRange.R12_000),
                transformer.read(val)
        );
    }

    @Test
    void readForwardMultiBitRange011ResolvesToR8_01() {
        // bits 8-9 = 0b01 → R8_01；R12_000 因 bits 12-14=0 匹配
        final long val = 0b01L << 8;
        assertEquals(
                Set.of(TestBitRange.R8_01, TestBitRange.R12_000),
                transformer.read(val)
        );
    }

    @Test
    void readForwardNullReturnsNull() {
        assertNull(transformer.read(null));
    }
}
