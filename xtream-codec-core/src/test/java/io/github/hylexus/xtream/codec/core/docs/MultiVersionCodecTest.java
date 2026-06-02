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
import io.github.hylexus.xtream.codec.common.utils.XtreamConstants;
import io.github.hylexus.xtream.codec.core.annotation.PrependLengthFieldType;
import io.github.hylexus.xtream.codec.core.annotation.XtreamField;
import io.github.hylexus.xtream.codec.core.type.Preset;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// 演示 `@XtreamField#version` 的多版本编解码。
///
/// V1 与 V2 共用同一 Entity 结构，但部分字段在不同版本下有不同编解码行为。
///
/// @author opencode (AI)
/// @since 0.6.0
@ReferencedByDocs("guide/core/annotation-driven/multi-version.md")
class MultiVersionCodecTest extends BaseEntityCodecTest {

    public interface Versions {
        int V1 = 1;
        int V2 = 2;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class VersionedEntity {
        // 两版本共有：u32，行为一致
        @Preset.RustStyle.u32(desc = "用户ID")
        private Long id;

        // V1: GBK 编码；V2: UTF-8 编码
        @Preset.RustStyle.str(
                prependLengthFieldType = PrependLengthFieldType.u8,
                version = {Versions.V1},
                desc = "用户名(V1 GBK)",
                charset = XtreamConstants.CHARSET_NAME_GBK
        )
        @Preset.RustStyle.str(
                prependLengthFieldType = PrependLengthFieldType.u8,
                version = {Versions.V2},
                desc = "用户名(V2 UTF-8)",
                charset = XtreamConstants.CHARSET_NAME_UTF8
        )
        private String name;

        // V1: u8；V2: u16；其余版本默认 u32
        @Preset.RustStyle.u8(desc = "年龄(V1 u8)", version = {Versions.V1})
        @Preset.RustStyle.u16(desc = "年龄(V2 u16)", version = {Versions.V2})
        @Preset.RustStyle.u32(desc = "年龄(默认 u32)")
        private long age;

        // 仅 V2 有
        @Preset.RustStyle.str(
                prependLengthFieldType = PrependLengthFieldType.u8,
                desc = "邮箱(仅V2)",
                version = {Versions.V2}
        )
        private String email;
    }

    @Test
    void testV1() {
        final VersionedEntity entity = new VersionedEntity()
                .setId(100L)
                .setName("张三")
                .setAge(25);
        doCodecTest(Versions.V1, entity, (source, hex, decoded) -> {
            assertEquals(Long.valueOf(100L), decoded.id);
            assertEquals("张三", decoded.name);
            assertEquals(25, decoded.age);
            // V1 无 email 字段，应为 null
            assertNull(decoded.email);
        }, false);
    }

    @Test
    void testV2() {
        final VersionedEntity entity = new VersionedEntity()
                .setId(100L)
                .setName("张三")
                .setAge(25)
                .setEmail("zhangsan@example.com");
        doCodecTest(Versions.V2, entity, (source, hex, decoded) -> {
            assertEquals(Long.valueOf(100L), decoded.id);
            assertEquals("张三", decoded.name);
            assertEquals(25, decoded.age);
            assertEquals("zhangsan@example.com", decoded.email);
        }, false);
    }

    // 验证 version=ALL_VERSION 时，没有指定 ALL_VERSION 的字段回退到默认注解
    @Test
    void testAllVersionFallback() {
        final VersionedEntity entity = new VersionedEntity()
                .setId(100L)
                .setName("张三")
                .setAge(25);
        // ALL_VERSION 不精确匹配 V1 或 V2
        //   - name 只有 V1/V2 注解 → 无兜底 → 被跳过
        //   - age 有 ALL_VERSION 默认 u32 注解 → 正常编解码
        //   - id 默认 ALL_VERSION → 正常编解码
        doCodecTest(XtreamField.ALL_VERSION, entity, (source, hex, decoded) -> {
            assertEquals(Long.valueOf(100L), decoded.id);
            assertNull(decoded.name);
            assertNull(decoded.email);
            assertEquals(25, decoded.age);
        }, false);
    }
}
