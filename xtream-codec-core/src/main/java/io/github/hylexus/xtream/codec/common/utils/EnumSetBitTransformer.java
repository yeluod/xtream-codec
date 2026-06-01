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

package io.github.hylexus.xtream.codec.common.utils;

import io.github.hylexus.xtream.codec.core.FieldTransformer;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * @param <E> the enum type that implements {@link BitFlag}
 * @author hylexus
 * @author opencode (AI)
 * @since 0.6.0
 */
public abstract class EnumSetBitTransformer<E extends Enum<E> & BitFlag>
        implements FieldTransformer<Number, Set<E>> {

    private final Class<E> enumType;
    private final E[] enumConstants;

    protected EnumSetBitTransformer(Class<E> enumType) {
        this.enumType = enumType;
        this.enumConstants = enumType.getEnumConstants();
    }

    @Override
    public @Nullable Set<E> read(@Nullable Number source) {
        if (source == null) {
            return null;
        }
        final long val = source.longValue();
        final EnumSet<E> result = EnumSet.noneOf(enumType);
        for (E constant : enumConstants) {
            final int length = constant.bitLength();
            if (length == 1) {
                // 单 bit：bit 被置位即匹配
                if ((val & (1L << constant.bitOffset())) != 0) {
                    result.add(constant);
                }
            } else {
                // 多 bit range：提取 offset 处 length 位，精确匹配 bitValue()
                final long rangeVal = (val >>> constant.bitOffset()) & ((1L << length) - 1);
                if (rangeVal == constant.bitValue()) {
                    result.add(constant);
                }
            }
        }
        return result;
    }

    @Override
    public @Nullable Number write(@Nullable Set<E> derived) {
        if (derived == null) {
            return null;
        }
        long result = 0L;
        for (E constant : derived) {
            final int offset = constant.bitOffset();
            final int length = constant.bitLength();
            if (length == 1) {
                // 单 bit：|= 天然正确（一个位置只有 0 或 1）
                result |= (1L << offset);
            } else {
                // 多 bit range：先清除目标 range 旧位，再写入 bitValue()
                final long mask = ((1L << length) - 1) << offset;
                result = (result & ~mask) | (((long) constant.bitValue()) << offset);
            }
        }
        return result;
    }
}
