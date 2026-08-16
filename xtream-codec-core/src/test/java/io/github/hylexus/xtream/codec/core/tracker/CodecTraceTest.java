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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hylexus.xtream.codec.common.utils.XtreamBytes;
import io.github.hylexus.xtream.codec.core.EntityCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CodecTraceTest {
    private final EntityCodec entityCodec = EntityCodec.DEFAULT;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepPublicTrackerApiCompatible() {
        final CodecDebugEntity01 original = createSimpleEntity();
        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            final CodecTracker encodeTracker = new CodecTracker();
            this.entityCodec.encode(original, buffer, encodeTracker);

            final CodecTracker decodeTracker = new CodecTracker();
            final CodecDebugEntity01 decoded = this.entityCodec.decode(CodecDebugEntity01.class, buffer, decodeTracker);

            assertEquals(original.getMultimediaType(), decoded.getMultimediaType());
            assertEquals(CodecTraceDirection.ENCODE, encodeTracker.getTrace().getDirection());
            assertEquals(CodecTraceDirection.DECODE, decodeTracker.getTrace().getDirection());
            assertEquals(5, encodeTracker.getTrace().getRoot().getChildren().size());
        } finally {
            XtreamBytes.releaseBuf(buffer);
        }
    }

    @Test
    void shouldRouteNullTrackerToProductionPath() {
        final CodecDebugEntity01 original = createSimpleEntity();
        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            this.entityCodec.encode(original, buffer, null);
            final CodecDebugEntity01 decoded = this.entityCodec.decode(CodecDebugEntity01.class, buffer, null);

            assertEquals(original.getChannelId(), decoded.getChannelId());
            assertEquals(original.getEndTime(), decoded.getEndTime());
        } finally {
            XtreamBytes.releaseBuf(buffer);
        }
    }

    @Test
    void shouldRecordSimpleFieldByteRanges() {
        final CodecDebugEntity01 original = createSimpleEntity();
        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            final CodecTracker tracker = new CodecTracker();
            this.entityCodec.encode(original, buffer, tracker);

            final CodecTrace trace = tracker.getTrace();
            final List<CodecTraceNode> children = trace.getRoot().getChildren();
            assertEquals(buffer.writerIndex(), trace.getRoot().getByteEnd());
            assertEquals(0, children.get(0).getByteStart());
            assertEquals(1, children.get(0).getByteEnd());
            assertEquals(1, children.get(1).getByteStart());
            assertEquals(2, children.get(1).getByteEnd());
            assertEquals(2, children.get(2).getByteStart());
            assertEquals(3, children.get(2).getByteEnd());
            assertEquals(3, children.get(3).getByteStart());
            assertEquals(9, children.get(3).getByteEnd());
            assertEquals(9, children.get(4).getByteStart());
            assertEquals(15, children.get(4).getByteEnd());
        } finally {
            XtreamBytes.releaseBuf(buffer);
        }
    }

    @Test
    void shouldRecordNestedCollectionAndMapHierarchy() {
        final CodecDebugEntity04 original = createNestedEntity();
        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            final CodecTracker tracker = new CodecTracker();
            this.entityCodec.encode(original, buffer, tracker);

            final CodecTraceNode root = tracker.getTrace().getRoot();
            assertEquals(3, root.getChildren().size());
            assertEquals(CodecTraceNodeKind.NESTED_FIELD, root.getChildren().get(0).getKind());
            assertEquals(CodecTraceNodeKind.NESTED_FIELD, root.getChildren().get(1).getKind());
            assertEquals(CodecTraceNodeKind.FIELD, root.getChildren().get(2).getKind());

            final CodecTraceNode body = root.getChildren().get(1);
            final Optional<CodecTraceNode> map = body.getChildren().stream()
                    .filter(node -> node.getKind() == CodecTraceNodeKind.MAP)
                    .findFirst();
            assertTrue(map.isPresent());
            assertEquals(2, map.orElseThrow().getChildren().size());
            assertEquals(0, map.orElseThrow().getChildren().get(0).getAttributes().get("itemIndex"));
        } finally {
            XtreamBytes.releaseBuf(buffer);
        }
    }

    @Test
    void shouldKeepEncodedMapEntryChildrenAlignedWithPayload() {
        final CodecDebugEntity04 original = createNestedEntity();
        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            final CodecTracker tracker = new CodecTracker();
            this.entityCodec.encode(original, buffer, tracker);

            assertEquals(buffer.writerIndex(), tracker.getTrace().getRoot().getByteEnd());
            assertChildRangesInsideParent(tracker.getTrace().getRoot());

            final CodecTraceNode body = tracker.getTrace().getRoot().getChildren().get(1);
            final CodecTraceNode map = body.getChildren().stream()
                    .filter(node -> node.getKind() == CodecTraceNodeKind.MAP)
                    .findFirst()
                    .orElseThrow();
            for (final CodecTraceNode entry : map.getChildren()) {
                assertContiguousRanges(entry);
            }
        } finally {
            XtreamBytes.releaseBuf(buffer);
        }
    }

    @Test
    void shouldKeepNestedMapNodeRangesInsideParentRange() {
        final CodecDebugEntity04 original = createNestedEntity();
        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            this.entityCodec.encode(original, buffer);
            final int encodedLength = buffer.writerIndex();

            final CodecTracker tracker = new CodecTracker();
            final CodecDebugEntity04 target = new CodecDebugEntity04().setRuntimeClass(CodecDebugEntity02.class);
            this.entityCodec.decode(target, buffer, tracker);

            assertEquals(encodedLength, tracker.getTrace().getRoot().getByteEnd());
            assertChildRangesInsideParent(tracker.getTrace().getRoot());

            final CodecTraceNode body = tracker.getTrace().getRoot().getChildren().get(1);
            final CodecTraceNode map = body.getChildren().stream()
                    .filter(node -> node.getKind() == CodecTraceNodeKind.MAP)
                    .findFirst()
                    .orElseThrow();
            final CodecTraceNode firstEntry = map.getChildren().getFirst();
            assertEquals(3, firstEntry.getChildren().size());
            assertContiguousRanges(firstEntry);
        } finally {
            XtreamBytes.releaseBuf(buffer);
        }
    }

    @Test
    void shouldVisitTraceInDepthFirstOrder() {
        final CodecDebugEntity01 original = createSimpleEntity();
        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            final CodecTracker tracker = new CodecTracker();
            this.entityCodec.encode(original, buffer, tracker);

            final List<String> visited = new ArrayList<>();
            tracker.getTrace().visit((level, node) -> visited.add(level + ":" + node.getName()));

            assertEquals("0:root", visited.getFirst());
            assertEquals("1:multimediaType", visited.get(1));
            assertEquals("1:channelId", visited.get(2));
            assertEquals(6, visited.size());
        } finally {
            XtreamBytes.releaseBuf(buffer);
        }
    }

    @Test
    void shouldCreateJsonFriendlyTraceView() throws Exception {
        final CodecDebugEntity01 original = createSimpleEntity();
        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            final CodecTracker tracker = new CodecTracker();
            this.entityCodec.encode(original, buffer, tracker);

            final CodecTraceView view = tracker.toTraceView();
            final String json = this.objectMapper.writeValueAsString(view);

            assertNotNull(view.payloadHex());
            assertTrue(view.nodeIdsByByteOffset().containsKey(0));
            assertTrue(view.nodeIdsByByteOffset().get(0).contains(view.root().children().getFirst().id()));
            assertTrue(json.contains("payloadHex"));
            assertTrue(json.contains("byteStart"));
        } finally {
            XtreamBytes.releaseBuf(buffer);
        }
    }

    @Test
    void shouldKeepPartialTraceWhenDecodeFails() {
        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            buffer.writeByte(2);
            final CodecTracker tracker = new CodecTracker();

            assertThrows(RuntimeException.class, () -> this.entityCodec.decode(CodecDebugEntity01.class, buffer, tracker));

            assertEquals(1, tracker.getTrace().getRoot().getChildren().size());
            assertFalse(tracker.getTrace().getDiagnostics().isEmpty());
            assertEquals(CodecTraceStatus.ERROR, tracker.getTrace().getRoot().getStatus());
        } finally {
            XtreamBytes.releaseBuf(buffer);
        }
    }

    private static CodecDebugEntity01 createSimpleEntity() {
        final LocalDateTime startTime = LocalDateTime.of(2021, 4, 12, 14, 30, 3);
        final LocalDateTime endTime = LocalDateTime.of(2021, 4, 12, 14, 30, 4);
        return new CodecDebugEntity01()
                .setMultimediaType((short) 2)
                .setChannelId((short) 3)
                .setEventItemCode((short) 0)
                .setStartTime(startTime)
                .setEndTime(endTime);
    }

    private static CodecDebugEntity04 createNestedEntity() {
        final int bodyProps = CodecDebugEntity04Test.generateMsgBodyPropsForJt808(28 + 6 + 4, 0, false, 0);
        final CodecDebugEntity04.Header header = new CodecDebugEntity04.Header()
                .setMsgId(0x0200)
                .setMsgSerialNo(123)
                .setMsgBodyProps(bodyProps)
                .setTerminalId("013912344323")
                .setProtocolVersion((byte) 0);
        final Map<Short, Object> extraItems = new LinkedHashMap<>();
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

    private static void assertChildRangesInsideParent(CodecTraceNode parent) {
        final Integer parentStart = parent.getByteStart();
        final Integer parentEnd = parent.getByteEnd();
        for (final CodecTraceNode child : parent.getChildren()) {
            if (parentStart != null && parentEnd != null && child.getByteStart() != null && child.getByteEnd() != null) {
                assertTrue(child.getByteStart() >= parentStart, () -> child + " starts before parent " + parent);
                assertTrue(child.getByteEnd() <= parentEnd, () -> child + " ends after parent " + parent);
            }
            assertChildRangesInsideParent(child);
        }
    }

    private static void assertContiguousRanges(CodecTraceNode entry) {
        final CodecTraceNode key = findMapEntryItem(entry, CodecTracker.MapEntryItemType.KEY);
        final CodecTraceNode valueLength = findMapEntryItem(entry, CodecTracker.MapEntryItemType.VALUE_LENGTH);
        final CodecTraceNode value = findMapEntryItem(entry, CodecTracker.MapEntryItemType.VALUE);
        assertEquals(entry.getByteStart(), key.getByteStart());
        assertEquals(key.getByteEnd(), valueLength.getByteStart());
        assertEquals(valueLength.getByteEnd(), value.getByteStart());
        assertEquals(entry.getByteEnd(), value.getByteEnd());
    }

    private static CodecTraceNode findMapEntryItem(CodecTraceNode entry, CodecTracker.MapEntryItemType type) {
        return entry.getChildren().stream()
                .filter(node -> type.equals(node.getAttributes().get("mapItemType")))
                .findFirst()
                .orElseThrow();
    }
}
