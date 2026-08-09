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

package io.github.hylexus.xtream.codec.ext.jt808.spec;

import io.github.hylexus.xtream.codec.ext.jt808.codec.DelimiterAndLengthFieldBasedByteToMessageDecoder;
import io.github.hylexus.xtream.codec.server.reactive.spec.TcpXtreamNettyHandlerAdapter;
import io.github.hylexus.xtream.codec.server.reactive.spec.UdpXtreamNettyHandlerAdapter;
import io.github.hylexus.xtream.codec.server.reactive.spec.domain.values.TcpSessionIdleStateCheckerProps;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpNettyServerCustomizer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpXtreamServer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.udp.UdpNettyServerCustomizer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.udp.UdpXtreamServer;
import io.github.hylexus.xtream.codec.server.reactive.spec.resources.TcpXtreamNettyResourceFactory;
import io.github.hylexus.xtream.codec.server.reactive.spec.resources.UdpXtreamNettyResourceFactory;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import reactor.netty.Connection;
import reactor.netty.resources.LoopResources;
import reactor.netty.tcp.TcpServer;
import reactor.netty.udp.UdpServer;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

import static io.github.hylexus.xtream.codec.ext.jt808.utils.JtProtocolConstant.BEAN_NAME_CHANNEL_INBOUND_HANDLER_ADAPTER;
import static io.github.hylexus.xtream.codec.ext.jt808.utils.JtProtocolConstant.BEAN_NAME_CHANNEL_INBOUND_IDLE_STATE_HANDLER;
import static io.github.hylexus.xtream.codec.ext.jt808.utils.JtProtocolConstant.BEAN_NAME_CHANNEL_INBOUND_IDLE_STATE_HANDLER_CALLBACK;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Jt808ServersTest {

    @Test
    void instructionTcpKeepsDefaultOrderAndPipeline() throws Exception {
        final LoopResources loopResources = mock(LoopResources.class);
        final TcpXtreamNettyResourceFactory resourceFactory = tcpResourceFactory(loopResources, true);
        final TcpXtreamNettyHandlerAdapter handlerAdapter = mock(TcpXtreamNettyHandlerAdapter.class);

        final TcpXtreamServer server = Jt808Servers.instructionTcp()
                .bind("127.0.0.1", 18080)
                .handlerAdapter(handlerAdapter)
                .sessionManager(mock(Jt808SessionManager.class))
                .sessionIdleStateChecker(new TcpSessionIdleStateCheckerProps())
                .maxInstructionFrameLength(1024)
                .resourceFactory(resourceFactory)
                .customize(nettyServer -> nettyServer.port(19080))
                .build();

        final TcpServer tcpServer = mock(TcpServer.class, Answers.RETURNS_SELF);
        applyTcpCustomizers(server, tcpServer);

        final Consumer<Connection> connectionConsumer = verifyTcpOrder(tcpServer, handlerAdapter, loopResources, true, 18080, 19080);
        final Connection connection = mock(Connection.class, Answers.RETURNS_SELF);
        connectionConsumer.accept(connection);

        final InOrder connectionOrder = inOrder(connection);
        connectionOrder.verify(connection).addHandlerLast(eq(BEAN_NAME_CHANNEL_INBOUND_IDLE_STATE_HANDLER), isA(IdleStateHandler.class));
        connectionOrder.verify(connection).addHandlerLast(eq(BEAN_NAME_CHANNEL_INBOUND_IDLE_STATE_HANDLER_CALLBACK), isA(XtreamTcpHeatBeatHandler.class));
        connectionOrder.verify(connection).addHandlerFirst(eq(BEAN_NAME_CHANNEL_INBOUND_HANDLER_ADAPTER), isA(DelimiterBasedFrameDecoder.class));
    }

    @Test
    void attachmentTcpKeepsDefaultOrderAndPipeline() throws Exception {
        final LoopResources loopResources = mock(LoopResources.class);
        final TcpXtreamNettyResourceFactory resourceFactory = tcpResourceFactory(loopResources, false);
        final TcpXtreamNettyHandlerAdapter handlerAdapter = mock(TcpXtreamNettyHandlerAdapter.class);

        final TcpXtreamServer server = Jt808Servers.attachmentTcp()
                .bind("127.0.0.1", 18081)
                .handlerAdapter(handlerAdapter)
                .attachmentSessionManager(mock(Jt808AttachmentSessionManager.class))
                .sessionIdleStateChecker(new TcpSessionIdleStateCheckerProps())
                .maxInstructionFrameLength(1024)
                .maxStreamFrameLength(2048)
                .resourceFactory(resourceFactory)
                .customize(nettyServer -> nettyServer.port(19081))
                .build();

        final TcpServer tcpServer = mock(TcpServer.class, Answers.RETURNS_SELF);
        applyTcpCustomizers(server, tcpServer);

        final Consumer<Connection> connectionConsumer = verifyTcpOrder(tcpServer, handlerAdapter, loopResources, false, 18081, 19081);
        final Connection connection = mock(Connection.class, Answers.RETURNS_SELF);
        connectionConsumer.accept(connection);

        final InOrder connectionOrder = inOrder(connection);
        connectionOrder.verify(connection).addHandlerLast(eq(BEAN_NAME_CHANNEL_INBOUND_IDLE_STATE_HANDLER), isA(IdleStateHandler.class));
        connectionOrder.verify(connection).addHandlerLast(eq(BEAN_NAME_CHANNEL_INBOUND_IDLE_STATE_HANDLER_CALLBACK), isA(XtreamTcpHeatBeatHandler.class));
        connectionOrder.verify(connection).addHandlerFirst(
                eq(BEAN_NAME_CHANNEL_INBOUND_HANDLER_ADAPTER),
                isA(DelimiterAndLengthFieldBasedByteToMessageDecoder.class)
        );
    }

    @Test
    void udpBuildersKeepDefaultOrderAndHandlerBinding() throws Exception {
        final LoopResources instructionLoopResources = mock(LoopResources.class);
        final UdpXtreamNettyResourceFactory instructionResourceFactory = udpResourceFactory(instructionLoopResources, true);
        final UdpXtreamNettyHandlerAdapter instructionHandlerAdapter = mock(UdpXtreamNettyHandlerAdapter.class);
        final UdpXtreamServer instructionServer = Jt808Servers.instructionUdp()
                .bind("127.0.0.1", 28080)
                .handlerAdapter(instructionHandlerAdapter)
                .resourceFactory(instructionResourceFactory)
                .customize(nettyServer -> nettyServer.port(29080))
                .build();

        verifyUdpOrder(instructionServer, instructionHandlerAdapter, instructionLoopResources, true, 28080, 29080);

        final LoopResources attachmentLoopResources = mock(LoopResources.class);
        final UdpXtreamNettyResourceFactory attachmentResourceFactory = udpResourceFactory(attachmentLoopResources, false);
        final UdpXtreamNettyHandlerAdapter attachmentHandlerAdapter = mock(UdpXtreamNettyHandlerAdapter.class);
        final UdpXtreamServer attachmentServer = Jt808Servers.attachmentUdp()
                .bind("127.0.0.1", 28081)
                .handlerAdapter(attachmentHandlerAdapter)
                .resourceFactory(attachmentResourceFactory)
                .customize(nettyServer -> nettyServer.port(29081))
                .build();

        verifyUdpOrder(attachmentServer, attachmentHandlerAdapter, attachmentLoopResources, false, 28081, 29081);
    }

    private static TcpXtreamNettyResourceFactory tcpResourceFactory(LoopResources loopResources, boolean preferNative) {
        final TcpXtreamNettyResourceFactory resourceFactory = mock(TcpXtreamNettyResourceFactory.class);
        when(resourceFactory.loopResources()).thenReturn(loopResources);
        when(resourceFactory.preferNative()).thenReturn(preferNative);
        return resourceFactory;
    }

    private static UdpXtreamNettyResourceFactory udpResourceFactory(LoopResources loopResources, boolean preferNative) {
        final UdpXtreamNettyResourceFactory resourceFactory = mock(UdpXtreamNettyResourceFactory.class);
        when(resourceFactory.loopResources()).thenReturn(loopResources);
        when(resourceFactory.preferNative()).thenReturn(preferNative);
        return resourceFactory;
    }

    private static void applyTcpCustomizers(TcpXtreamServer server, TcpServer tcpServer) throws Exception {
        for (final TcpNettyServerCustomizer customizer : tcpCustomizers(server)) {
            customizer.customize(tcpServer);
        }
    }

    private static void verifyUdpOrder(
            UdpXtreamServer server, UdpXtreamNettyHandlerAdapter handlerAdapter, LoopResources loopResources,
            boolean preferNative, int bindPort, int userPort) throws Exception {
        final UdpServer udpServer = mock(UdpServer.class, Answers.RETURNS_SELF);
        for (final UdpNettyServerCustomizer customizer : udpCustomizers(server)) {
            customizer.customize(udpServer);
        }

        final InOrder order = inOrder(udpServer);
        order.verify(udpServer).host("0.0.0.0");
        order.verify(udpServer).port(3721);
        order.verify(udpServer).host("127.0.0.1");
        order.verify(udpServer).port(bindPort);
        order.verify(udpServer).handle(handlerAdapter);
        order.verify(udpServer).runOn(loopResources, preferNative);
        order.verify(udpServer).port(userPort);
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Connection> verifyTcpOrder(
            TcpServer tcpServer, TcpXtreamNettyHandlerAdapter handlerAdapter, LoopResources loopResources,
            boolean preferNative, int bindPort, int userPort) {
        final ArgumentCaptor<Consumer<Connection>> captor = ArgumentCaptor.forClass(Consumer.class);
        final InOrder order = inOrder(tcpServer);
        order.verify(tcpServer).host("0.0.0.0");
        order.verify(tcpServer).port(3927);
        order.verify(tcpServer).host("127.0.0.1");
        order.verify(tcpServer).port(bindPort);
        order.verify(tcpServer).handle(handlerAdapter);
        order.verify(tcpServer).doOnConnection(captor.capture());
        order.verify(tcpServer).runOn(loopResources, preferNative);
        order.verify(tcpServer).port(userPort);
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static List<TcpNettyServerCustomizer> tcpCustomizers(TcpXtreamServer server) throws Exception {
        final Field field = TcpXtreamServer.class.getDeclaredField("nettyServerCustomizers");
        field.setAccessible(true);
        return (List<TcpNettyServerCustomizer>) field.get(server);
    }

    @SuppressWarnings("unchecked")
    private static List<UdpNettyServerCustomizer> udpCustomizers(UdpXtreamServer server) throws Exception {
        final Field field = UdpXtreamServer.class.getDeclaredField("nettyServerCustomizers");
        field.setAccessible(true);
        return (List<UdpNettyServerCustomizer>) field.get(server);
    }
}
