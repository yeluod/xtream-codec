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

package io.github.hylexus.xtream.codec.common.bean.impl;

import io.github.hylexus.xtream.codec.common.bean.BeanPropertyMetadata;
import io.github.hylexus.xtream.codec.core.BeanMetadataRegistry;
import io.github.hylexus.xtream.codec.core.annotation.EncodedLength;
import io.github.hylexus.xtream.codec.core.annotation.XtreamField;

import java.lang.reflect.Field;

/**
 * {@link BeanPropertyMetadata} 的编码长度字段实现。
 * <p>
 * 用于 {@link EncodedLength @EncodedLength} 注解标注的字段。
 *
 * @author Codex (AI)
 * @since 0.7.0
 */
public final class EncodedLengthBeanPropertyMetadata extends BasicBeanPropertyMetadata {

    public EncodedLengthBeanPropertyMetadata(
            BeanMetadataRegistry registry,
            String name,
            Class<?> type,
            int version,
            XtreamField xtreamField,
            Field field,
            PropertyGetter getter,
            PropertySetter setter) {

        super(registry, name, type, version, xtreamField, field, getter, setter);
    }

    @Override
    public boolean isEncodedLength() {
        return true;
    }
}
