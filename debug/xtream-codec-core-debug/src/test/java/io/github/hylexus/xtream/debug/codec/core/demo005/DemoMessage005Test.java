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

import io.github.hylexus.xtream.codec.common.utils.FormatUtils;
import io.github.hylexus.xtream.codec.common.utils.XtreamBytes;
import io.github.hylexus.xtream.codec.core.EntityCodec;
import io.github.hylexus.xtream.debug.codec.core.BaseEntityCodecTest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

class DemoMessage005Test extends BaseEntityCodecTest {
    @Test
    void test() {
        final DemoMessage005 entity = new DemoMessage005();

        // 父类公共字段
        entity.setDelimiter("$$")
                .setCommandFlag(0x01)
                .setReplyFlag(0x01)
                .setIdentifier("0123456789")
                .setEncryptFlag(0x01)
                .setChecksum(111);

        // 子类字段
        entity.setTime(LocalDateTime.now())
                .setSerialNumber(111)
                .setIccid("11111111110000000000")
                .setBmsBatteryCount(new byte[]{1})
                .setBmsBatteries(List.of(new DemoMessage005.BmsBattery("012345678901234567891234")));

        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        EntityCodec.DEFAULT.encode(entity, buffer);
        final String hexString = FormatUtils.toHexString(buffer);
        System.out.println(hexString);
        final ByteBuf buffer2 = XtreamBytes.byteBufFromHexString(ByteBufAllocator.DEFAULT, hexString);
        final DemoMessage005 decode = EntityCodec.DEFAULT.decode(DemoMessage005.class, buffer2);
        System.out.println(decode);
    }
}
