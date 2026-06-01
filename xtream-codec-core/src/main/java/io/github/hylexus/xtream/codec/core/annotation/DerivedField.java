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

import io.github.hylexus.xtream.codec.core.FieldTransformer;
import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.*;

/**
 * Marks a field as derived — its value is not read from {@code ByteBuf} directly,
 * but is <em>derived</em> from another already-decoded field via a {@link FieldTransformer}.
 * <p>
 * Example:
 * <pre>{@code
 * @JtStyle.Dword(desc = "状态")
 * private long status;
 *
 * @DerivedField(source = "status", using = StatusBitTransformer.class, reverseSource = true)
 * private transient Set<StatusBit> statusFlags;
 * }</pre>
 * <p>
 * <b>编解码流水线（自 0.6.0 起为单遍内联）：</b>
 * <ul>
 *   <li><b>解码</b>：遍历 {@code @XtreamField} 时，源字段解码后立即通过
 *       {@code derivedBySource} 索引找到依赖它的派生字段，调用
 *       {@link FieldTransformer#read} 计算派生值并填入实例 / Record 构造器数组。
 *       无需第 2 遍后处理。</li>
 *   <li><b>编码</b>：遍历 {@code @XtreamField} 时，若当前源字段有对应的
 *       {@code reverseSource=true} 派生字段，则通过
 *       {@code reverseDerivedBySource} 索引找到该派生字段，读取其 Getter 值，
 *       调用 {@link FieldTransformer#write} 逆变换后作为编码值写入 ByteBuf。
 *       不会修改实例的源字段。</li>
 * </ul>
 * <p>
 * <b>多版本支持：</b>
 * 自 0.6.0 起支持 {@code @Repeatable}，可在同一字段上为不同版本声明不同的
 * {@code @DerivedField}。匹配规则与 {@link XtreamField} 一致：
 * <ul>
 *   <li>精确匹配目标版本 → 使用该注解</li>
 *   <li>无精确匹配，有 {@link #ALL_VERSION} → 使用默认版本兜底</li>
 *   <li>无精确匹配也无默认版本 → 忽略该字段</li>
 * </ul>
 *
 * @author hylexus
 * @author opencode (AI)
 * @since 0.6.0
 */
@Documented
@Repeatable(DerivedFieldContainer.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@ApiStatus.Experimental
public @interface DerivedField {

    /**
     * 匹配任意版本的常量。与 {@link XtreamField#ALL_VERSION} 值相同。
     */
    int ALL_VERSION = XtreamField.ALL_VERSION;

    /**
     * 该派生字段适用的版本号。默认匹配所有版本。
     * <p>
     * 匹配逻辑：精确匹配目标版本 → 无精确匹配时使用 {@link #ALL_VERSION} 兜底 → 否则忽略。
     *
     * @see XtreamField#version()
     */
    int[] version() default {ALL_VERSION};

    /**
     * The name of the source field in the same entity class.
     * The source field must be a regular (non-derived) field decoded from {@code ByteBuf}.
     */
    String source();

    /**
     * The {@link FieldTransformer} implementation class.
     * <p>
     * The class must have a no-arg constructor so that the framework can instantiate it reflectively.
     */
    Class<? extends FieldTransformer<?, ?>> using();

    /**
     * Whether this derived field is responsible for writing back to the source field during encoding.
     * <p>
     * Only one derived field per source may have {@code reverseSource = true}.
     * If multiple derived fields declare {@code reverseSource = true} for the same source,
     * an exception is thrown at startup.
     * <p>
     * Default is {@code false}. Set to {@code true} if encoding should write the derived
     * value back to the source field.
     */
    boolean reverseSource() default false;

    /**
     * Description of this derived field.
     */
    String desc() default "";
}
