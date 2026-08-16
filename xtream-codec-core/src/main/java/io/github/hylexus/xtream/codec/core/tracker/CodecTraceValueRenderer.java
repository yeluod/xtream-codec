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

import io.github.hylexus.xtream.codec.common.utils.FormatUtils;
import io.github.hylexus.xtream.codec.core.type.wrapper.*;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * 编解码跟踪值渲染工具。
 *
 * @author hylexus
 * @author Codex (AI)
 * @since 0.9.0
 */
public final class CodecTraceValueRenderer {
    private static final int MAX_SUMMARY_LENGTH = 120;

    private CodecTraceValueRenderer() {
    }

    public static @Nullable Object toJsonValue(@Nullable Object value) {
        return switch (value) {
            case null -> null;
            case byte[] bytes -> toUnsignedBytes(bytes);
            case DataWrapper<?> wrapper -> renderDataWrapper(wrapper);
            case CharSequence ignored -> value;
            case Number ignored -> value;
            case Boolean ignored -> value;
            case Enum<?> ignored -> value;
            default -> String.valueOf(value);
        };
    }

    public static @Nullable String toSummary(@Nullable Object value) {
        final Object jsonValue = toJsonValue(value);
        if (jsonValue == null) {
            return null;
        }
        final String summary = switch (jsonValue) {
            case byte[] bytes -> FormatUtils.toHexString(bytes);
            default -> String.valueOf(jsonValue);
        };
        if (summary.length() <= MAX_SUMMARY_LENGTH) {
            return summary;
        }
        return summary.substring(0, MAX_SUMMARY_LENGTH) + "...";
    }

    private static @Nullable Object renderDataWrapper(DataWrapper<?> wrapper) {
        switch (wrapper) {
            case U32Wrapper u32Wrapper -> {
                return u32Wrapper.asU32();
            }
            case U16Wrapper u16Wrapper -> {
                return u16Wrapper.asU16();
            }
            case U8Wrapper u8Wrapper -> {
                return u8Wrapper.asU8();
            }
            case StringWrapperGbk stringWrapper -> {
                return stringWrapper.asString();
            }
            case StringWrapperUtf8 stringWrapper -> {
                return stringWrapper.asString();
            }
            case StringWrapperBcd stringWrapper -> {
                return stringWrapper.asString();
            }
            case I32Wrapper i32Wrapper -> {
                return i32Wrapper.asI32();
            }
            case I16Wrapper i16Wrapper -> {
                return i16Wrapper.asI16();
            }
            case I8Wrapper i8Wrapper -> {
                return i8Wrapper.asI8();
            }
            case BytesDataWrapper bytesDataWrapper -> {
                final byte[] bytes = bytesDataWrapper.asBytes();
                return bytes == null ? null : toUnsignedBytes(bytes);
            }
            default -> {
            }
        }
        return wrapper;
    }

    private static List<Integer> toUnsignedBytes(byte[] bytes) {
        final List<Integer> values = new ArrayList<>(bytes.length);
        for (final byte value : bytes) {
            values.add(value & 0xFF);
        }
        return values;
    }
}
