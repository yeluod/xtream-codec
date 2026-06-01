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

package io.github.hylexus.xtream.codec.common.utils;

/**
 * 位标志接口，配合 {@link EnumSetBitTransformer} 使用，
 * 将 raw 数值中的 bit range 映射为枚举常量集。
 *
 * <p>用法示例：
 * <pre>{@code
 * enum AlarmFlag implements BitFlag {
 *     EMERGENCY(0),           // bitLength=1，bit 0 置位即匹配
 *     OVERSPEED(1),           // bitLength=1，bit 1 置位即匹配
 *     CARGO_LOADED(8, 2, 0b11); // bitLength=2，bits 8~9 = 0b11 时匹配
 *
 *     // ... 实现 bitOffset() / bitLength() / bitValue()
 * }
 * }</pre>
 *
 * @author hylexus
 * @author opencode (AI)
 * @since 0.6.0
 */
public interface BitFlag {

    /**
     * @return 该枚举常量在 raw 数值中占用的起始 bit 位置（从 0 开始计数）
     */
    int bitOffset();

    /**
     * @return 该枚举常量在 raw 数值中占用的 bit 位数。
     * {@code bitLength() == 1} 为单 bit 标志（bit 置位即匹配）；
     * {@code bitLength() > 1} 为多 bit range（精确匹配 {@link #bitValue()}）。
     * 默认返回 {@code 1}。
     */
    default int bitLength() {
        return 1;
    }

    /**
     * @return {@code bitLength() == 1} 时固定返回 {@code 1}（bit 被置位表示该标志出现），
     * 不支持反向语义；
     * {@code bitLength() > 1} 时表示该 range 对应的排他性取值。
     * @since 0.6.0
     */
    default int bitValue() {
        return 1;
    }

}
