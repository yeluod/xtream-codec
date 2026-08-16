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

package io.github.hylexus.xtream.codec.ext.jt1078.spec;

import io.github.hylexus.xtream.codec.ext.jt1078.codec.Jt1078ByteToMessageDecoder;
import io.github.hylexus.xtream.codec.server.reactive.spec.TcpXtreamNettyHandlerAdapter;
import io.github.hylexus.xtream.codec.server.reactive.spec.UdpXtreamNettyHandlerAdapter;
import io.github.hylexus.xtream.codec.server.reactive.spec.domain.values.TcpSessionIdleStateCheckerProps;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpNettyServerCustomizer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpXtreamServer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.udp.UdpNettyServerCustomizer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.udp.UdpXtreamServer;
import io.github.hylexus.xtream.codec.server.reactive.spec.resources.TcpXtreamNettyResourceFactory;
import io.github.hylexus.xtream.codec.server.reactive.spec.resources.UdpXtreamNettyResourceFactory;
import io.netty.channel.Channel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.resources.LoopResources;
import reactor.netty.tcp.TcpServer;
import reactor.netty.udp.UdpServer;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Consumer;

import static io.github.hylexus.xtream.codec.ext.jt1078.utils.Jt1078Constants.BEAN_NAME_JT1078_CHANNEL_FRAME_DECODER;
import static io.github.hylexus.xtream.codec.ext.jt1078.utils.Jt1078Constants.BEAN_NAME_JT1078_REQUEST_DECODER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Jt1078ServersTest {

    @Test
    void tcpKeepsDefaultOrderAndPipeline() throws Exception {
        final LoopResources loopResources = mock(LoopResources.class);
        final TcpXtreamNettyResourceFactory resourceFactory = tcpResourceFactory(loopResources, true);
        final TcpXtreamNettyHandlerAdapter handlerAdapter = mock(TcpXtreamNettyHandlerAdapter.class);

        final TcpXtreamServer server = Jt1078Servers.tcp()
                .bind("127.0.0.1", 10780)
                .handlerAdapter(handlerAdapter)
                .sessionManager(mock(Jt1078SessionManager.class))
                .simConverter(mock(Jt1078SimConverter.class))
                .sessionIdleStateChecker(new TcpSessionIdleStateCheckerProps())
                .maxFrameLength(4096)
                .resourceFactory(resourceFactory)
                .customize(nettyServer -> nettyServer.port(11780))
                .build();

        final TcpServer tcpServer = mock(TcpServer.class, Answers.RETURNS_SELF);
        applyTcpCustomizers(server, tcpServer);

        final Consumer<Connection> connectionConsumer = verifyTcpOrder(tcpServer, handlerAdapter, loopResources, true, 10780, 11780);
        final Connection connection = tcpConnection();
        connectionConsumer.accept(connection);

        final InOrder connectionOrder = inOrder(connection);
        connectionOrder.verify(connection).addHandlerLast(eq(BEAN_NAME_JT1078_CHANNEL_FRAME_DECODER), isA(DelimiterBasedFrameDecoder.class));
        connectionOrder.verify(connection).addHandlerLast(eq(BEAN_NAME_JT1078_REQUEST_DECODER), isA(Jt1078ByteToMessageDecoder.class));
        connectionOrder.verify(connection).addHandlerLast(eq("xtreamTcpIdleStateHandler"), isA(IdleStateHandler.class));
        connectionOrder.verify(connection).addHandlerLast(eq("xtreamTcpIdleStateHandlerCallback"), isA(Jt1078TcpHeatBeatHandler.class));
    }

    @Test
    void udpKeepsDefaultOrderAndHandlerBinding() throws Exception {
        final LoopResources loopResources = mock(LoopResources.class);
        final UdpXtreamNettyResourceFactory resourceFactory = udpResourceFactory(loopResources, false);
        final UdpXtreamNettyHandlerAdapter handlerAdapter = mock(UdpXtreamNettyHandlerAdapter.class);

        final UdpXtreamServer server = Jt1078Servers.udp()
                .bind("127.0.0.1", 10781)
                .handlerAdapter(handlerAdapter)
                .resourceFactory(resourceFactory)
                .customize(nettyServer -> nettyServer.port(11781))
                .build();

        final UdpServer udpServer = mock(UdpServer.class, Answers.RETURNS_SELF);
        for (final UdpNettyServerCustomizer customizer : udpCustomizers(server)) {
            customizer.customize(udpServer);
        }

        final InOrder order = inOrder(udpServer);
        order.verify(udpServer).host("0.0.0.0");
        order.verify(udpServer).port(3721);
        order.verify(udpServer).host("127.0.0.1");
        order.verify(udpServer).port(10781);
        order.verify(udpServer).handle(handlerAdapter);
        order.verify(udpServer).runOn(loopResources, false);
        order.verify(udpServer).port(11781);
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

    private static Connection tcpConnection() {
        final Connection connection = mock(Connection.class, Answers.RETURNS_SELF);
        final NettyInbound inbound = mock(NettyInbound.class, Answers.RETURNS_SELF);
        final Channel channel = mock(Channel.class);
        when(connection.inbound()).thenReturn(inbound);
        when(connection.channel()).thenReturn(channel);
        // when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), 12345));
        when(inbound.withConnection(any())).thenAnswer(invocation -> {
            final Consumer<Connection> consumer = invocation.getArgument(0);
            consumer.accept(connection);
            return inbound;
        });
        return connection;
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
