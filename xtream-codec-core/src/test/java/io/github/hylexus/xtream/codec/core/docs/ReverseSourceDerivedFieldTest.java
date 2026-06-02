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

package io.github.hylexus.xtream.codec.core.docs;

import io.github.hylexus.xtream.codec.BaseEntityCodecTest;
import io.github.hylexus.xtream.codec.base.annotation.ReferencedByDocs;
import io.github.hylexus.xtream.codec.core.FieldTransformer;
import io.github.hylexus.xtream.codec.core.annotation.DerivedField;
import io.github.hylexus.xtream.codec.core.annotation.PrependLengthFieldType;
import io.github.hylexus.xtream.codec.core.annotation.XtreamField;
import io.github.hylexus.xtream.codec.core.type.Preset;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// 演示 `reverseSource=true` 的编解码回环。
///
/// 编码时，派生字段的值通过 `FieldTransformer#write` 逆向回写至 source 字段；
/// 解码时，source 字段的值通过 `FieldTransformer#read` 正向映射至派生字段。
///
/// @author opencode (AI)
/// @since 0.6.0
@ReferencedByDocs("guide/core/annotation-driven/derived-field.md")
class ReverseSourceDerivedFieldTest extends BaseEntityCodecTest {

    // Transformer：编码时大写→小写，解码时小写→大写
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

    // Entity 类：reverseSource=true 回环
    @Getter
    @Setter
    @Accessors(chain = true)
    public static class RoundTripEntity {
        @Preset.RustStyle.str(prependLengthFieldType = PrependLengthFieldType.u8)
        private String raw;

        @DerivedField(source = "raw", using = UpperCaseTransformer.class, reverseSource = true)
        private String upper;
    }

    // 编解码测试：只设置派生字段，编码时自动回写到 raw
    @Test
    void testRoundTrip() {
        final RoundTripEntity entity = new RoundTripEntity().setUpper("HELLO");
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            // 编码时 upper("HELLO") → write() → raw("hello")
            // 解码后 raw("hello") → read() → upper("HELLO")
            assertEquals("hello", decoded.raw);
            assertEquals("HELLO", decoded.upper);
        }, false);
    }
}
