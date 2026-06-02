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
import io.github.hylexus.xtream.codec.core.type.Preset;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// 演示同一 Entity 在不同版本下的 `@DerivedField` 行为差异。
///
/// 应用场景：协议升级后衍生逻辑发生变化，但 Entity 结构不变。
/// 不同版本的 `FieldTransformer` 可返回不同的衍生结果。
///
/// @author opencode (AI)
/// @since 0.6.0
@ReferencedByDocs("guide/core/annotation-driven/derived-field.md")
class MultiVersionDerivedFieldTest extends BaseEntityCodecTest {

    // V1 Transformer：低版本用中文映射
    public static class V1StatusTransformer implements FieldTransformer<Long, String> {
        @Override
        public String read(Long source) {
            if (source == null) {
                return null;
            }
            if (source == 0L) {
                return "离线";
            }
            if (source == 1L) {
                return "在线";
            }
            return "未知";
        }
    }

    // V2 Transformer：高版本用英文映射
    public static class V2StatusTransformer implements FieldTransformer<Long, String> {
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
            return "unknown";
        }
    }

    // Entity 类（同一结构，不同版本用不同 Transformer）
    public interface Versions {
        int V1 = 2013;
        int V2 = 2019;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class VersionedEntity {
        @Preset.RustStyle.u8
        private long status;

        @DerivedField(source = "status", using = V1StatusTransformer.class, version = {Versions.V1})
        @DerivedField(source = "status", using = V2StatusTransformer.class, version = {Versions.V2})
        private String statusDisplay;
    }

    // V1 版本测试
    @Test
    void testV1Version() {
        final VersionedEntity entity = new VersionedEntity().setStatus(1);
        doCodecTest(Versions.V1, entity, (source, hex, decoded) -> {
            assertEquals("在线", decoded.statusDisplay);
        }, false);
    }

    // V2 版本测试
    @Test
    void testV2Version() {
        final VersionedEntity entity = new VersionedEntity().setStatus(1);
        doCodecTest(Versions.V2, entity, (source, hex, decoded) -> {
            assertEquals("online", decoded.statusDisplay);
        }, false);
    }
}
