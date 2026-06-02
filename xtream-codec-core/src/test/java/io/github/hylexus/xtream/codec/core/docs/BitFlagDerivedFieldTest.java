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

import io.github.hylexus.xtream.codec.base.annotation.ReferencedByDocs;
import io.github.hylexus.xtream.codec.BaseEntityCodecTest;
import io.github.hylexus.xtream.codec.common.utils.BitFlag;
import io.github.hylexus.xtream.codec.common.utils.EnumSetBitTransformer;
import io.github.hylexus.xtream.codec.core.annotation.DerivedField;
import io.github.hylexus.xtream.codec.core.annotation.XtreamField;
import io.github.hylexus.xtream.codec.core.type.Preset;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 演示 BitFlag + EnumSetBitTransformer 的位标记衍生用法。
///
/// @author opencode (AI)
/// @since 0.6.0
@ReferencedByDocs("guide/core/annotation-driven/derived-field.md")
class BitFlagDerivedFieldTest extends BaseEntityCodecTest {

    // 位标记枚举
    public enum Permission implements BitFlag {
        READ(0),
        WRITE(1),
        EXECUTE(2);

        private final int offset;

        Permission(int offset) {
            this.offset = offset;
        }

        @Override
        public int bitOffset() {
            return offset;
        }
    }

    // Transformer 子类
    public static class PermissionBitTransformer extends EnumSetBitTransformer<Permission> {
        public PermissionBitTransformer() {
            super(Permission.class);
        }
    }

    // Entity 类：按位标记衍生 Set&lt;Permission&gt;
    @Getter
    @Setter
    @Accessors(chain = true)
    public static class PermissionEntity {
        @Preset.RustStyle.u8
        private int permissionBits;

        // 原始 int 字段中按位提取枚举集合
        @DerivedField(source = "permissionBits", using = PermissionBitTransformer.class)
        private Set<Permission> permissions;
    }

    // 编解码测试
    @Test
    void testBitFlagTransform() {
        final PermissionEntity entity = new PermissionEntity().setPermissionBits(0b101);
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(0b101, decoded.permissionBits);
            assertEquals(2, decoded.permissions.size());
            assertTrue(decoded.permissions.contains(Permission.READ));
            assertTrue(decoded.permissions.contains(Permission.EXECUTE));
        }, false);
    }
}
