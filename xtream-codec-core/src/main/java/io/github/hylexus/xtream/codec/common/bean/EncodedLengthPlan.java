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

package io.github.hylexus.xtream.codec.common.bean;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.ApiStatus;

/**
 * {@link io.github.hylexus.xtream.codec.core.annotation.EncodedLength @EncodedLength} 的编码期计划。
 * <p>
 * 元数据注册阶段负责把注解上的字段名解析为稳定的编码顺序下标，编码阶段只按下标判断
 * 何时记录范围起点、何时回填长度字段，避免在热路径中重复做字段名查找。
 *
 * @param lengthFieldIndex 长度字段在实体编码字段列表中的下标
 * @param fromFieldIndex   范围起始字段下标，{@code -1} 表示从长度字段之后立即开始
 * @param untilFieldIndex  范围结束字段下标，{@code -1} 表示持续到实体编码结束
 * @param writer           长度字段的回填写入器
 * @author Codex (AI)
 * @since 0.7.0
 */
@ApiStatus.Internal
public record EncodedLengthPlan(
        int lengthFieldIndex,
        int fromFieldIndex,
        int untilFieldIndex,
        Writer writer
) {

    /**
     * 长度字段的写入计划。
     *
     * @param maxValue 当前长度字段能表达的最大值
     * @param backfill 实际写入 {@link ByteBuf} 的回填函数
     */
    public record Writer(int maxValue, Backfill backfill) {

        /**
         * 将编码后的实际长度回填到指定的 {@code writerIndex}。
         *
         * @param target      目标缓冲区
         * @param writerIndex 长度字段的起始写入位置
         * @param value       实际编码长度
         */
        public void write(ByteBuf target, int writerIndex, int value) {
            if (value > this.maxValue) {
                throw new IllegalArgumentException("Encoded length value exceeds max value: " + value + " > " + this.maxValue);
            }
            this.backfill.write(target, writerIndex, value);
        }
    }

    /**
     * 长度字段的底层回填动作。
     *
     * @author Codex (AI)
     * @since 0.7.0
     */
    @FunctionalInterface
    public interface Backfill {

        /**
         * 在不改变 {@link ByteBuf#writerIndex()} 的情况下写入长度值。
         *
         * @param target      目标缓冲区
         * @param writerIndex 长度字段的起始写入位置
         * @param value       实际编码长度
         */
        void write(ByteBuf target, int writerIndex, int value);
    }

    /**
     * 根据长度字段的字节数创建回填写入器。
     *
     * @param byteCount 长度字段字节数，仅支持 {@code 1}、{@code 2}、{@code 4}
     * @return 回填写入器
     */
    public static Writer writer(int byteCount) {
        return switch (byteCount) {
            case 1 -> new Writer(0xFF, ByteBuf::setByte);
            case 2 -> new Writer(0xFFFF, ByteBuf::setShort);
            case 4 -> new Writer(Integer.MAX_VALUE, ByteBuf::setInt);
            default -> throw new IllegalArgumentException("Unsupported encoded length byte count: " + byteCount);
        };
    }
}
