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

package io.github.hylexus.xtream.debug.codec.core.demo005;

import io.github.hylexus.xtream.codec.base.annotation.ReferencedByDocs;
import io.github.hylexus.xtream.codec.core.annotation.EncodedLength;
import io.github.hylexus.xtream.codec.core.type.Preset;

import java.util.StringJoiner;

/**
 * 将公共消息头和校验字段定义在父类中，消息体字段交给具体消息子类声明。
 *
 * @author hylexus
 * @author Codex (AI)
 */
@ReferencedByDocs("guide/core/annotation-driven/encoded-length.md")
@SuppressWarnings("LombokGetterMayBeUsed")
public class BaseMessage {
    @Preset.RustStyle.str(order = -600, length = 2)
    protected String delimiter;

    @Preset.RustStyle.u8(order = -500)
    protected int commandFlag;

    @Preset.RustStyle.u8(order = -400)
    protected int replyFlag;

    @Preset.RustStyle.str(order = -300, length = 10)
    protected String identifier;

    @Preset.RustStyle.u8(order = -200)
    protected int encryptFlag;

    // 自动统计后续子类消息体的编码字节数，不包含 checksum
    @Preset.RustStyle.u16(order = -100)
    @EncodedLength(until = "checksum")
    protected int dataLength;

    // region body
    // 由子类定义
    // endregion

    @Preset.RustStyle.u8(order = 99999)
    protected int checksum;

    public String getDelimiter() {
        return delimiter;
    }

    public BaseMessage setDelimiter(String delimiter) {
        this.delimiter = delimiter;
        return this;
    }

    public int getCommandFlag() {
        return commandFlag;
    }

    public BaseMessage setCommandFlag(int commandFlag) {
        this.commandFlag = commandFlag;
        return this;
    }

    public int getReplyFlag() {
        return replyFlag;
    }

    public BaseMessage setReplyFlag(int replyFlag) {
        this.replyFlag = replyFlag;
        return this;
    }

    public String getIdentifier() {
        return identifier;
    }

    public BaseMessage setIdentifier(String identifier) {
        this.identifier = identifier;
        return this;
    }

    public int getEncryptFlag() {
        return encryptFlag;
    }

    public BaseMessage setEncryptFlag(int encryptFlag) {
        this.encryptFlag = encryptFlag;
        return this;
    }

    public int getDataLength() {
        return dataLength;
    }

    public BaseMessage setDataLength(int dataLength) {
        this.dataLength = dataLength;
        return this;
    }

    public int getChecksum() {
        return checksum;
    }

    public BaseMessage setChecksum(int checksum) {
        this.checksum = checksum;
        return this;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", BaseMessage.class.getSimpleName() + "[", "]")
                .add("delimiter='" + delimiter + "'")
                .add("commandFlag=" + commandFlag)
                .add("replyFlag=" + replyFlag)
                .add("identifier='" + identifier + "'")
                .add("encryptFlag=" + encryptFlag)
                .add("dataLength=" + dataLength)
                .add("checksum=" + checksum)
                .toString();
    }
}
