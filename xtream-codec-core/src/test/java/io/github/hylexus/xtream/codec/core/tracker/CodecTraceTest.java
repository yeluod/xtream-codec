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
import io.github.hylexus.xtream.codec.common.utils.FormatUtils;
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
    void shouldNotCreateTraceWhenProductionPathIsUsed() {
        final CodecDebugEntity01 original = createSimpleEntity();
        final CodecTracker unusedTracker = new CodecTracker();
        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            this.entityCodec.encode(original, buffer, null);
            this.entityCodec.decode(CodecDebugEntity01.class, buffer, null);

            assertEquals(CodecTraceDirection.UNKNOWN, unusedTracker.getTrace().getDirection());
            assertTrue(unusedTracker.getTrace().getRoot().getChildren().isEmpty());
        } finally {
            XtreamBytes.releaseBuf(buffer);
        }
    }

    @Test
    void shouldKeepTrackedAndProductionPayloadsIdentical() {
        final CodecDebugEntity01 original = createSimpleEntity();
        final ByteBuf production = ByteBufAllocator.DEFAULT.buffer();
        final ByteBuf tracked = ByteBufAllocator.DEFAULT.buffer();
        try {
            this.entityCodec.encode(original, production, null);
            this.entityCodec.encode(original, tracked, new CodecTracker());

            assertEquals(FormatUtils.toHexString(production), FormatUtils.toHexString(tracked));
        } finally {
            XtreamBytes.releaseBuf(production);
            XtreamBytes.releaseBuf(tracked);
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
            assertEquals("U8FieldCodec", children.getFirst().getProcessorType());
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
            assertEquals("LocationExtraItemFieldCodec", map.orElseThrow().getProcessorType());
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
            assertTrue(json.contains("processorType"));
            assertFalse(json.contains("codecType"));
        } finally {
            XtreamBytes.releaseBuf(buffer);
        }
    }

    @Test
    void shouldDeriveViewHexFromRootPayloadWhenNodeHexIsMissing() {
        final CodecTracker tracker = new CodecTracker();
        tracker.beginEncode(0, "Example");
        try (final CodecTracker.TraceScope scope = tracker.enterScope(
                CodecTraceNodeKind.FIELD, "value", "byte", "U8FieldCodec", "值", 1)) {
            scope.completeWithHex(2, null, 2);
        }
        tracker.finishTrace("0102", 2);

        assertEquals("02", tracker.toTraceView().root().children().getFirst().hex());
    }

    @Test
    void shouldTranslateNestedSliceCoordinatesToRootPayload() {
        final CodecTracker tracker = new CodecTracker();
        tracker.beginDecode(0, "Example");
        try (CodecTracker.TraceScope parent = tracker.enterScope(CodecTraceNodeKind.NESTED_FIELD, "body", "Body", "BodyCodec", "消息体", 4)) {
            try (CodecTracker.CoordinateScope ignored = tracker.openCoordinateBase(4)) {
                try (CodecTracker.TraceScope child = tracker.enterScope(CodecTraceNodeKind.FIELD, "value", "byte", "U8FieldCodec", "值", 2)) {
                    child.complete(1, 3);
                }
                try (CodecTracker.CoordinateScope ignored1 = tracker.openCoordinateBase(3)) {
                    try (CodecTracker.TraceScope nested = tracker.enterScope(CodecTraceNodeKind.FIELD, "nested", "byte", "U8FieldCodec", "嵌套", 1)) {
                        nested.complete(2, 2);
                    }
                }
            }
            parent.complete("body", 9);
        }

        final CodecTraceNode body = tracker.getTrace().getRoot().getChildren().getFirst();
        assertEquals(4, body.getByteStart());
        assertEquals(9, body.getByteEnd());
        assertEquals(6, body.getChildren().get(0).getByteStart());
        assertEquals(7, body.getChildren().get(0).getByteEnd());
        assertEquals(8, body.getChildren().get(1).getByteStart());
        assertEquals(9, body.getChildren().get(1).getByteEnd());
    }

    @Test
    void shouldAttachTemporarySubtreeToOutputCoordinates() {
        final CodecTracker tracker = new CodecTracker();
        tracker.beginEncode(0, "Example");
        try (final CodecTracker.TraceScope parent = tracker.enterScope(CodecTraceNodeKind.MAP_ENTRY, "[0]", null, null, null, 0)) {
            final CodecTracker.TraceCheckpoint checkpoint = tracker.checkpoint();
            try (final CodecTracker.TemporaryBufferScope ignored = tracker.openTemporaryBuffer()) {
                try (final CodecTracker.TraceScope child = tracker.enterScope(CodecTraceNodeKind.FIELD, "value", "byte", "U8FieldCodec", "值", 0)) {
                    child.complete(1, 2);
                }
            }
            checkpoint.captureNewChildren();
            try (final CodecTracker.TraceScope length = tracker.enterScope(CodecTraceNodeKind.FIELD, "valueLength", "byte", "U8FieldCodec", "长度", 0)) {
                length.complete(2, 1);
            }
            checkpoint.relocateNewChildren(5);
            parent.complete(null, 7);
        }

        final List<CodecTraceNode> children = tracker.getTrace().getRoot().getChildren().getFirst().getChildren();
        final CodecTraceNode child = children.getFirst();
        assertEquals(5, child.getByteStart());
        assertEquals(7, child.getByteEnd());
        assertEquals(0, children.get(1).getByteStart());
        assertEquals(1, children.get(1).getByteEnd());
    }

    @Test
    void shouldKeepPartialTraceWhenDecodeFails() {
        final ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            buffer.writeByte(2);
            final CodecTracker tracker = new CodecTracker();

            assertThrows(RuntimeException.class, () -> this.entityCodec.decode(CodecDebugEntity01.class, buffer, tracker));

            final CodecTrace trace = tracker.getTrace();
            assertEquals(2, trace.getRoot().getChildren().size());
            assertEquals(CodecTraceStatus.ERROR, trace.getRoot().getStatus());
            assertEquals(CodecTraceStatus.ERROR, trace.getRoot().getChildren().get(1).getStatus());
            assertEquals(1, trace.getRoot().getChildren().get(1).getDiagnostics().size());
            assertEquals(1, trace.getDiagnostics().size());
        } finally {
            XtreamBytes.releaseBuf(buffer);
        }
    }

    @Test
    void shouldRecordScopedNodesWithAutomaticParenting() {
        final CodecTracker tracker = new CodecTracker();
        tracker.beginEncode(0, "Example");

        try (final CodecTracker.TraceScope parent = tracker.enterScope(CodecTraceNodeKind.NESTED_FIELD, "body", "Body", "BodyCodec", "消息体", 0)) {
            try (final CodecTracker.TraceScope child = tracker.enterScope(CodecTraceNodeKind.FIELD, "value", "int", "U8FieldCodec", "值", 0)) {
                child.complete(1, 1);
            }
            parent.complete("body", 1);
        }
        tracker.finishTrace("01", 2);

        final CodecTraceNode parent = tracker.getTrace().getRoot().getChildren().getFirst();
        assertEquals("body", parent.getName());
        assertEquals(0, parent.getByteStart());
        assertEquals(1, parent.getByteEnd());
        assertEquals(parent.getId(), parent.getChildren().getFirst().getParentId());
        assertEquals(CodecTraceStatus.SUCCESS, parent.getStatus());
    }

    @Test
    void shouldLimitNodeOverrideToItsExplicitScope() {
        final CodecTracker tracker = new CodecTracker();
        tracker.beginEncode(0, "Example");

        try (final CodecTracker.NodeOverrideScope ignored = tracker.overrideNextNodeName("overridden")) {
            try (final CodecTracker.TraceScope first = tracker.enterScope(CodecTraceNodeKind.FIELD, "first", "byte", "U8FieldCodec", null, 0)) {
                first.complete(1, 1);
            }
            try (final CodecTracker.TraceScope second = tracker.enterScope(CodecTraceNodeKind.FIELD, "second", "byte", "U8FieldCodec", null, 1)) {
                second.complete(2, 2);
            }
        }
        try (final CodecTracker.NodeOverrideScope ignored = tracker.overrideNextNodeName("unused")) {
            // 未创建节点时直接关闭，不能污染后续节点。
        }
        try (final CodecTracker.TraceScope third = tracker.enterScope(
                CodecTraceNodeKind.FIELD, "third", "byte", "U8FieldCodec", null, 2)) {
            third.complete(3, 3);
        }

        final List<CodecTraceNode> children = tracker.getTrace().getRoot().getChildren();
        assertEquals("overridden", children.get(0).getName());
        assertEquals("second", children.get(1).getName());
        assertEquals("third", children.get(2).getName());
    }

    @Test
    void shouldRecoverScopeStackAfterFailure() {
        final CodecTracker tracker = new CodecTracker();
        tracker.beginDecode(0, "Example");

        final CodecTracker.TraceScope failed = tracker.enterScope(CodecTraceNodeKind.FIELD, "failed", "int", "U8FieldCodec", "失败字段", 0);
        failed.fail(new IllegalArgumentException("invalid value"), 1);

        try (final CodecTracker.TraceScope next = tracker.enterScope(
                CodecTraceNodeKind.FIELD, "next", "int", "U8FieldCodec", "下一个字段", 1)) {
            next.complete(2, 2);
        }

        assertEquals(2, tracker.getTrace().getRoot().getChildren().size());
        assertEquals(CodecTraceStatus.ERROR, tracker.getTrace().getRoot().getChildren().get(0).getStatus());
        assertEquals(CodecTraceStatus.SUCCESS, tracker.getTrace().getRoot().getChildren().get(1).getStatus());
        assertEquals(1, tracker.getTrace().getDiagnostics().size());
    }

    @Test
    void shouldAssociateOperationFailureWithDeepestIncompleteScope() {
        final CodecTracker tracker = new CodecTracker();
        final IllegalArgumentException failure = new IllegalArgumentException("invalid value");
        tracker.beginDecode(0, "Example");

        try {
            try (final CodecTracker.TraceScope parent = tracker.enterScope(
                    CodecTraceNodeKind.NESTED_FIELD, "body", "Body", "BodyCodec", "消息体", 0)) {
                try (final CodecTracker.TraceScope child = tracker.enterScope(
                        CodecTraceNodeKind.FIELD, "value", "int", "U8FieldCodec", "值", 1)) {
                    throw failure;
                }
            }
        } catch (IllegalArgumentException e) {
            tracker.recordFailure(e, 2);
        }

        final CodecTrace trace = tracker.getTrace();
        final CodecTraceNode parent = trace.getRoot().getChildren().getFirst();
        final CodecTraceNode child = parent.getChildren().getFirst();
        assertEquals(CodecTraceStatus.ERROR, trace.getRoot().getStatus());
        assertEquals(CodecTraceStatus.ERROR, parent.getStatus());
        assertEquals(CodecTraceStatus.ERROR, child.getStatus());
        assertTrue(parent.getDiagnostics().isEmpty());
        assertEquals(1, child.getDiagnostics().size());
        assertEquals(1, trace.getDiagnostics().size());
        assertEquals(child.getId(), trace.getDiagnostics().getFirst().nodeId());
    }

    @Test
    void shouldNotDuplicateExplicitScopeFailureAtOperationBoundary() {
        final CodecTracker tracker = new CodecTracker();
        final IllegalArgumentException failure = new IllegalArgumentException("invalid value");
        tracker.beginDecode(0, "Example");

        try (final CodecTracker.TraceScope scope = tracker.enterScope(
                CodecTraceNodeKind.FIELD, "value", "int", "U8FieldCodec", "值", 0)) {
            scope.fail(failure, 1);
        }
        tracker.recordFailure(failure, 1);

        final CodecTrace trace = tracker.getTrace();
        assertEquals(CodecTraceStatus.ERROR, trace.getRoot().getStatus());
        assertEquals(1, trace.getRoot().getChildren().getFirst().getDiagnostics().size());
        assertEquals(1, trace.getDiagnostics().size());
    }

    @Test
    void shouldRejectOutOfOrderScopeClose() {
        final CodecTracker tracker = new CodecTracker();
        tracker.beginEncode(0, "Example");
        final CodecTracker.TraceScope outer = tracker.enterScope(
                CodecTraceNodeKind.NESTED_FIELD, "outer", "Outer", "OuterCodec", "外层", 0);
        final CodecTracker.TraceScope inner = tracker.enterScope(
                CodecTraceNodeKind.FIELD, "inner", "int", "U8FieldCodec", "内层", 0);

        assertThrows(IllegalStateException.class, outer::close);
        inner.complete(1, 1);
        outer.complete("outer", 1);
    }

    @Test
    void shouldDiagnoseUnclosedScopesAtTraceCompletion() {
        final CodecTracker tracker = new CodecTracker();
        tracker.beginEncode(0, "Example");
        tracker.enterScope(CodecTraceNodeKind.FIELD, "unfinished", "byte", "U8FieldCodec", "未完成", 0);

        tracker.finishTrace("01", 1);

        assertEquals(CodecTraceStatus.ERROR, tracker.getTrace().getRoot().getStatus());
        assertFalse(tracker.getTrace().getDiagnostics().isEmpty());
        assertTrue(tracker.getTrace().getRoot().getChildren().getFirst().getStatus() == CodecTraceStatus.ERROR);
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
