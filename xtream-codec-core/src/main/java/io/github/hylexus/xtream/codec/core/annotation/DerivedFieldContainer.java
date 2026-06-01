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
 * 容器注解，用于在同一字段上声明多个 {@link DerivedField @DerivedField}。
 * <p>
 * 用户通常不需要直接使用此注解——Java 8 的 {@code @Repeatable} 机制会
 * 自动将多个 {@code @DerivedField} 包装在此容器中。
 *
 * @author hylexus
 * @author opencode (AI)
 * @see DerivedField
 * @since 0.6.0
 */
@Documented
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface DerivedFieldContainer {
    DerivedField[] value();
}
