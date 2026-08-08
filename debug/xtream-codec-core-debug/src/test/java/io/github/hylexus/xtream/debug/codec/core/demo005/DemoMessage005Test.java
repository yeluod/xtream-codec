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
import io.github.hylexus.xtream.codec.common.utils.FormatUtils;
import io.github.hylexus.xtream.codec.common.utils.XtreamBytes;
import io.github.hylexus.xtream.codec.core.EntityCodec;
import io.github.hylexus.xtream.debug.codec.core.BaseEntityCodecTest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ReferencedByDocs("guide/core/annotation-driven/encoded-length.md")
class DemoMessage005Test extends BaseEntityCodecTest {
    private static final int DATA_LENGTH_FIELD_OFFSET = 15;
    private static final int EXPECTED_BODY_LENGTH = 54;

    @Test
    void testEncodedLengthAcrossInheritance() {
        final DemoMessage005 entity = new DemoMessage005();

        // 父类公共字段
        entity.setDelimiter("$$")
                .setCommandFlag(0x01)
                .setReplyFlag(0x01)
                .setIdentifier("0123456789")
                .setEncryptFlag(0x01)
                .setChecksum(111);

        // 子类字段
        entity.setTime(LocalDateTime.of(2026, 8, 8, 12, 30, 45))
                .setSerialNumber(111)
                .setIccid("11111111110000000000")
                .setBmsBatteryCount(new byte[]{1})
                .setBmsBatteries(List.of(new DemoMessage005.BmsBattery("012345678901234567891234")));

        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            EntityCodec.DEFAULT.encode(entity, buffer);
            assertEquals(EXPECTED_BODY_LENGTH, buffer.getUnsignedShort(DATA_LENGTH_FIELD_OFFSET));
            assertEquals(0, entity.getDataLength());

            final String hexString = FormatUtils.toHexString(buffer);
            final ByteBuf buffer2 = XtreamBytes.byteBufFromHexString(ByteBufAllocator.DEFAULT, hexString);
            try {
                final DemoMessage005 decoded = EntityCodec.DEFAULT.decode(DemoMessage005.class, buffer2);
                assertEquals(EXPECTED_BODY_LENGTH, decoded.getDataLength());
                assertEquals(entity.getTime(), decoded.getTime());
                assertEquals(entity.getSerialNumber(), decoded.getSerialNumber());
                assertEquals(entity.getIccid(), decoded.getIccid());
                assertArrayEquals(entity.getBmsBatteryCount(), decoded.getBmsBatteryCount());
                assertEquals(entity.getBmsBatteries(), decoded.getBmsBatteries());
                assertEquals(entity.getChecksum(), decoded.getChecksum());
            } finally {
                buffer2.release();
            }
        } finally {
            buffer.release();
        }
    }
}
