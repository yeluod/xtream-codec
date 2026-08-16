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

package io.github.hylexus.xtream.codec.core.tracker;

import io.github.hylexus.xtream.codec.common.utils.XtreamBytes;
import io.github.hylexus.xtream.codec.core.EntityCodec;
import io.github.hylexus.xtream.codec.core.annotation.XtreamField;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CodecDebugEntity04Test {

    EntityCodec entityCodec = EntityCodec.DEFAULT;

    @Test
    void test() {
        final CodecDebugEntity04 original = createEntity();

        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            final CodecTracker encodeTracker = new CodecTracker();
            entityCodec.encode(original, buffer, encodeTracker);
            // encodeTracker.visit();
            doCompare(encodeTracker, original);

            final CodecTracker decodeTracker = new CodecTracker();
            final CodecDebugEntity04 decoded = entityCodec.decode(XtreamField.ALL_VERSION, new CodecDebugEntity04().setRuntimeClass(CodecDebugEntity02.class), buffer, decodeTracker);
            // decodeTracker.visit();
            doCompare(decodeTracker, original);
            doCompare(decodeTracker, decoded);
        } finally {
            XtreamBytes.releaseBuf(buffer);
            assertEquals(0, buffer.refCnt());
        }
    }

    private void doCompare(CodecTracker tracker, CodecDebugEntity04 original) {
        final CodecTraceNode rootSpan = tracker.getTrace().getRoot();
        assertEquals(3, rootSpan.getChildren().size());

        final CodecTraceNode headerSpan = rootSpan.getChildren().get(0);
        assertEquals(CodecTraceNodeKind.NESTED_FIELD, headerSpan.getKind());

        final CodecTraceNode bodySpan = rootSpan.getChildren().get(1);
        assertEquals(CodecTraceNodeKind.NESTED_FIELD, bodySpan.getKind());
        final CodecTraceNode checkSumSpan = rootSpan.getChildren().get(2);
        assertEquals(CodecTraceNodeKind.FIELD, checkSumSpan.getKind());

        assertEquals(original.getHeader().getMsgId(), headerSpan.getChildren().get(0).getValue());
        assertEquals(original.getHeader().getMsgBodyProps(), headerSpan.getChildren().get(1).getValue());
        assertEquals(original.getHeader().getTerminalId(), headerSpan.getChildren().get(2).getValue());
        assertEquals(original.getHeader().getMsgSerialNo(), headerSpan.getChildren().get(3).getValue());

        final CodecDebugEntity02 body = (CodecDebugEntity02) original.getBody();
        assertEquals(body.getAlarmFlag(), bodySpan.getChildren().get(0).getValue());
        assertEquals(body.getStatus(), bodySpan.getChildren().get(1).getValue());
        assertEquals(body.getLatitude(), bodySpan.getChildren().get(2).getValue());
        assertEquals(body.getLongitude(), bodySpan.getChildren().get(3).getValue());
        assertEquals(body.getAltitude(), bodySpan.getChildren().get(4).getValue());
        assertEquals(body.getSpeed(), bodySpan.getChildren().get(5).getValue());
        assertEquals(body.getDirection(), bodySpan.getChildren().get(6).getValue());
        assertEquals(body.getTime(), bodySpan.getChildren().get(7).getValue());
        assertEquals(CodecTraceNodeKind.MAP, bodySpan.getChildren().get(8).getKind());
        assertEquals(2, bodySpan.getChildren().get(8).getChildren().size());

        assertEquals(original.getCheckSum(), checkSumSpan.getValue());
    }

    private CodecDebugEntity04 createEntity() {
        final int bodyProps = generateMsgBodyPropsForJt808(28 + 6 + 4, 0, false, 0);
        final CodecDebugEntity04.Header header = new CodecDebugEntity04.Header()
                .setMsgId(0x0200)
                .setMsgSerialNo(123)
                .setMsgBodyProps(bodyProps)
                .setTerminalId("013912344323")
                .setProtocolVersion((byte) 0);
        final Map<Short, Object> extraItems = new HashMap<>();
        extraItems.put((short) 0x01, 22);
        extraItems.put((short) 0x02, 33);

        final LocalDateTime time = LocalDateTime.of(2021, 9, 27, 15, 30, 33);
        return new CodecDebugEntity04()
                .setHeader(header)
                .setBody(new CodecDebugEntity02()
                        .setAlarmFlag(1)
                        .setStatus(1)
                        .setLatitude(31000565L)
                        .setLongitude(121451375L)
                        .setAltitude(777)
                        .setSpeed(67)
                        .setDirection(90)
                        .setTime(time)
                        .setExtraItems(extraItems))
                .setCheckSum((byte) 111);
    }

    @SuppressWarnings("ErroneousBitwiseExpression")
    static int generateMsgBodyPropsForJt808(int msgBodySize, int encryptionType, boolean isSubPackage, int reversedBit15) {
        // [ 0-9 ] 0000,0011,1111,1111(3FF)(消息体长度)
        int props = (msgBodySize & 0x3FF)
                    // [10-12] 0001,1100,0000,0000(1C00)(加密类型)
                    | ((encryptionType << 10) & 0x1C00)
                    // [ 13_ ] 0010,0000,0000,0000(2000)(是否有子包)
                    | (((isSubPackage ? 1 : 0) << 13) & 0x2000)
                    // [14_ ]  0100,0000,0000,0000(4000)(保留位)
                    // 1: V2019 固定为 1
                    // | ((1 << 14) & 0x4000)
                    | ((0 << 14) & 0x4000)
                    // [15_ ]  1000,0000,0000,0000(8000)(保留位)
                    | ((reversedBit15 << 15) & 0x8000);
        return props & 0xFFFF;
    }
}
