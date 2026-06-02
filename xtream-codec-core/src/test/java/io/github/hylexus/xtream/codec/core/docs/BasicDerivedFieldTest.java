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
import io.github.hylexus.xtream.codec.core.annotation.XtreamField;
import io.github.hylexus.xtream.codec.core.type.Preset;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// @author opencode (AI)
/// @since 0.6.0
@ReferencedByDocs("guide/core/annotation-driven/derived-field.md")
class BasicDerivedFieldTest extends BaseEntityCodecTest {

    // Transformer 实现：将 long 值映射为可读的状态文本
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

    // Entity 类：保留原始字段，追加衍生字段
    @Getter
    @Setter
    @Accessors(chain = true)
    public static class BasicEntity {
        @Preset.RustStyle.u8
        private long status;

        // status 解码后，自动通过 StatusDisplayTransformer 衍生出 statusDisplay
        @DerivedField(source = "status", using = StatusDisplayTransformer.class)
        private String statusDisplay;
    }

    // 编解码测试
    @Test
    void testBasicTransform() {
        final BasicEntity entity = new BasicEntity().setStatus(1);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            // status 还原为 1，statusDisplay 自动衍生为 "online"
            assertEquals(1L, decoded.status);
            assertEquals("online", decoded.statusDisplay);
        }, false);
    }
}
