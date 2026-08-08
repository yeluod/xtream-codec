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

package io.github.hylexus.xtream.codec.core.annotation;

import java.lang.annotation.*;

/**
 * 标注在无符号整数长度字段上，编码时自动计算指定字段范围的编码后字节数并回填。
 * <p>
 * 范围语义：[{@link #from()}, {@link #until()}) 的左闭右开区间。
 * <p>
 * 当 {@link #from()} 为空字符串时，范围从紧跟当前字段的下一个字段开始；
 * <p>
 * 当 {@link #until()} 为空字符串时，范围延伸到编码顺序的最后一个字段。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @Preset.RustStyle.u16
 * @EncodedLength(from = "username", until = "checkSum")
 * private int bodyLength;
 * }</pre>
 * <p>
 * 继承场景：父类可声明 {@code @EncodedLength(until = "checkSum")}，
 * 子类字段自动纳入范围，无需知道子类字段名。
 *
 * @author Codex (AI)
 * @since 0.7.0
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EncodedLength {

    /**
     * 范围起始字段名（包含）。
     * <p>
     * 空字符串表示从紧接当前字段的下一个字段开始。
     */
    String from() default "";

    /**
     * 范围结束字段名（不包含）。
     * <p>
     * 空字符串表示范围延伸到编码顺序的最后一个字段。
     */
    String until() default "";
}
