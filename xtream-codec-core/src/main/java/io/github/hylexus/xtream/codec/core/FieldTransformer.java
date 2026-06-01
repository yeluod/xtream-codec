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

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * SPI for converting between a raw protocol field and a business-view field.
 * <p>
 * Used with {@link io.github.hylexus.xtream.codec.core.annotation.DerivedField @DerivedField}
 * to provide the conversion logic.
 *
 * @param <S> source type (the raw protocol field type, e.g. {@link Long})
 * @param <T> target type (the derived field type, e.g. {@link java.util.Set Set&lt;Enum&gt;})
 * @author opencode (AI)
 * @since 0.6.0
 */
@ApiStatus.Experimental
public interface FieldTransformer<S, T> {

    /**
     * 从 raw 字段读取值，转换为业务字段值（解码方向）。
     *
     * @param source raw 字段的已解码值（可能为 {@code null}）
     * @return 业务字段值，将设置到 {@code @DerivedField} 字段上
     */
    @Nullable T read(@Nullable S source);

    /**
     * 将业务字段值写回为 raw 字段值（编码方向）。
     * <p>
     * 可选操作。默认实现抛出 {@link UnsupportedOperationException}，
     * 表示该 derived 字段为只读，不参与编码。
     *
     * @param derived 业务字段的当前值（可能为 {@code null}）
     * @return 编码前设置到 raw 字段上的值
     * @throws UnsupportedOperationException 如果不支持逆向转换
     */
    default @Nullable S write(@Nullable T derived) {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + " does not support write-back");
    }

}
