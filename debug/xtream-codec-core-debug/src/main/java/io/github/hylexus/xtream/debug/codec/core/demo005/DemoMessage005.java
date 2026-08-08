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
import io.github.hylexus.xtream.codec.core.annotation.PrependLengthFieldType;
import io.github.hylexus.xtream.codec.core.type.Preset;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/**
 * 继承 {@link BaseMessage} 的消息体示例。
 *
 * @author hylexus
 * @author Codex (AI)
 */
@ReferencedByDocs("guide/core/annotation-driven/encoded-length.md")
@SuppressWarnings("LombokGetterMayBeUsed")
public class DemoMessage005 extends BaseMessage {

    // 数据采集时间 BYTE[6]
    @Preset.JtStyle.BcdDateTime
    private LocalDateTime time;
    // 登入流水号
    @Preset.RustStyle.u16
    private int serialNumber;
    // 集成电路卡识别码(ICCID)
    @Preset.RustStyle.str(length = 20)
    private String iccid;

    // 电池管理系统对应动力蓄电池包个数
    @Preset.RustStyle.byte_array(prependLengthFieldType = PrependLengthFieldType.u8)
    private byte[] bmsBatteryCount;

    @Preset.RustStyle.list(lengthExpression = "getBmsBatteriesEncodedLength()")
    private List<BmsBattery> bmsBatteries;

    public record BmsBattery(@Preset.RustStyle.str(length = 24) String id) {
    }

    public int getBmsBatteriesEncodedLength() {
        int sum = 0;
        for (final byte c : this.bmsBatteryCount) {
            sum += c;
        }
        return sum * 24;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public DemoMessage005 setTime(LocalDateTime time) {
        this.time = time;
        return this;
    }

    public int getSerialNumber() {
        return serialNumber;
    }

    public DemoMessage005 setSerialNumber(int serialNumber) {
        this.serialNumber = serialNumber;
        return this;
    }

    public String getIccid() {
        return iccid;
    }

    public DemoMessage005 setIccid(String iccid) {
        this.iccid = iccid;
        return this;
    }

    public byte[] getBmsBatteryCount() {
        return bmsBatteryCount;
    }

    public DemoMessage005 setBmsBatteryCount(byte[] bmsBatteryCount) {
        this.bmsBatteryCount = bmsBatteryCount;
        return this;
    }

    public List<BmsBattery> getBmsBatteries() {
        return bmsBatteries;
    }

    public DemoMessage005 setBmsBatteries(List<BmsBattery> bmsBatteries) {
        this.bmsBatteries = bmsBatteries;
        return this;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", DemoMessage005.class.getSimpleName() + "[", "]")
                .add("time=" + time)
                .add("serialNumber=" + serialNumber)
                .add("iccid='" + iccid + "'")
                .add("bmsBatteryCount=" + Arrays.toString(bmsBatteryCount))
                .add("bmsBatteries=" + bmsBatteries)
                .toString();
    }
}
