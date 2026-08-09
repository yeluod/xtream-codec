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

package io.github.hylexus.xtream.codec.server.reactive.spec;

import io.github.hylexus.xtream.codec.core.EntityCodec;
import io.github.hylexus.xtream.codec.server.reactive.spec.domain.values.UdpSessionIdleStateCheckerProps;
import io.github.hylexus.xtream.codec.server.reactive.spec.handler.XtreamHandlerAdapter;
import io.github.hylexus.xtream.codec.server.reactive.spec.handler.XtreamHandlerResult;
import io.github.hylexus.xtream.codec.server.reactive.spec.handler.XtreamHandlerResultHandler;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.DefaultXtreamSessionManager;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.XtreamServerBuilder;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.AbstractXtreamServer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpNettyServerCustomizer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpXtreamServer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.udp.UdpNettyServerCustomizer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.udp.UdpXtreamServer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.ConnectionObserver;
import reactor.netty.tcp.TcpServer;
import reactor.netty.udp.UdpServer;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class XtreamServersTest {

    @Test
    void tcpGenericBuilderShouldPreserveCustomizerOrder() throws Exception {
        final AtomicBoolean sawFrameDecoder = new AtomicBoolean(false);

        final TcpXtreamServer server = XtreamServers.tcp()
                .name("tcp-generic-server")
                .bind("127.0.0.1", 18080)
                .pipeline(pipeline -> pipeline.addFirst(new LengthFieldBasedFrameDecoder(1024, 0, 1, 0, 0)))
                .customize(tcpServer -> tcpServer
                        .host("127.0.0.2")
                        .port(18081)
                        .doOnChannelInit((observer, channel, remoteAddress) ->
                                sawFrameDecoder.set(channel.pipeline().get(LengthFieldBasedFrameDecoder.class) != null)))
                .build();

        assertEquals("tcp-generic-server", readField(server, AbstractXtreamServer.class, "name"));

        final List<TcpNettyServerCustomizer> customizers = readListField(server, TcpXtreamServer.class, "nettyServerCustomizers", TcpNettyServerCustomizer.class);
        assertEquals(4, customizers.size());
        assertInstanceOf(TcpNettyServerCustomizer.Default.class, customizers.getFirst());

        final TcpServer tcpServer = applyTcpCustomizers(customizers);
        final InetSocketAddress bindAddress = (InetSocketAddress) Objects.requireNonNull(tcpServer.configuration().bindAddress()).get();
        assertEquals("127.0.0.2", bindAddress.getHostString());
        assertEquals(18081, bindAddress.getPort());

        tcpServer.configuration().doOnChannelInit()
                .onChannelInit(ConnectionObserver.emptyListener(), new EmbeddedChannel(), bindAddress);
        assertTrue(sawFrameDecoder.get());
    }

    @Test
    void udpGenericBuilderShouldPreserveCustomizerOrder() throws Exception {
        final AtomicBoolean sawFrameDecoder = new AtomicBoolean(false);

        final UdpXtreamServer server = XtreamServers.udp()
                .name("udp-generic-server")
                .bind("127.0.0.1", 19080)
                .pipeline(pipeline -> pipeline.addFirst(new LengthFieldBasedFrameDecoder(1024, 0, 1, 0, 0)))
                .customize(udpServer -> udpServer
                        .host("127.0.0.2")
                        .port(19081)
                        .doOnChannelInit((observer, channel, remoteAddress) ->
                                sawFrameDecoder.set(channel.pipeline().get(LengthFieldBasedFrameDecoder.class) != null)))
                .build();

        assertEquals("udp-generic-server", readField(server, AbstractXtreamServer.class, "name"));

        final List<UdpNettyServerCustomizer> customizers = readListField(server, UdpXtreamServer.class, "nettyServerCustomizers", UdpNettyServerCustomizer.class);
        assertEquals(4, customizers.size());
        assertInstanceOf(UdpNettyServerCustomizer.Default.class, customizers.getFirst());

        final UdpServer udpServer = applyUdpCustomizers(customizers);
        final InetSocketAddress bindAddress = (InetSocketAddress) Objects.requireNonNull(udpServer.configuration().bindAddress()).get();
        assertEquals("127.0.0.2", bindAddress.getHostString());
        assertEquals(19081, bindAddress.getPort());

        udpServer.configuration().doOnChannelInit()
                .onChannelInit(ConnectionObserver.emptyListener(), new EmbeddedChannel(), bindAddress);
        assertTrue(sawFrameDecoder.get());
    }

    @Test
    void dispatcherBuilderShouldEnableBuiltinHandlersAndCustomComponents() throws Exception {
        final DefaultXtreamSessionManager sessionManager = new DefaultXtreamSessionManager(
                true,
                new UdpSessionIdleStateCheckerProps(),
                new XtreamSessionIdGenerator.DefalutXtreamSessionIdGenerator()
        );

        final XtreamNettyHandlerAdapter handlerAdapter = XtreamServers.DispatcherBuilder.tcp()
                .addHandlerMapping(exchange -> Mono.just("handler"))
                .addHandlerAdapter(new XtreamHandlerAdapter() {
                    @Override
                    public boolean supports(Object handler) {
                        return true;
                    }

                    @Override
                    public Mono<XtreamHandlerResult> handle(XtreamExchange exchange, Object handler) {
                        return Mono.empty();
                    }
                })
                .addHandlerResultHandler(new XtreamHandlerResultHandler() {
                    @Override
                    public boolean supports(XtreamHandlerResult result) {
                        return true;
                    }

                    @Override
                    public Mono<Void> handleResult(XtreamExchange exchange, XtreamHandlerResult result) {
                        return Mono.empty();
                    }
                })
                .addFilter((exchange, chain) -> chain.filter(exchange))
                .addExceptionHandler((exchange, ex) -> Mono.empty())
                .sessionManager(sessionManager)
                .enableBuiltinHandlers(EntityCodec.DEFAULT)
                .build();

        final Object sessionManagerField = readField(handlerAdapter, "sessionManager");
        assertSame(sessionManager, sessionManagerField);

        final Object xtreamHandler = readField(handlerAdapter, "xtreamHandler");
        final Object exceptionHandlingDelegate = readField(xtreamHandler, "delegateHandler");
        final Object filteringChain = readField(exceptionHandlingDelegate, "chain");
        final Object dispatcherHandler = readField(filteringChain, "handler");

        final List<?> handlerMappings = (List<?>) readField(dispatcherHandler, "handlerMappings");
        final List<?> handlerAdapters = (List<?>) readField(dispatcherHandler, "handlerAdapters");
        final List<?> resultHandlers = (List<?>) readField(dispatcherHandler, "resultHandlers");
        final List<?> filters = (List<?>) readField(filteringChain, "allFilters");
        final List<?> exceptionHandlers = (List<?>) readField(xtreamHandler, "exceptionHandlers");

        assertEquals(1, handlerMappings.size());
        assertEquals(3, handlerAdapters.size());
        assertEquals(3, resultHandlers.size());
        assertEquals(1, filters.size());
        assertEquals(2, exceptionHandlers.size());
    }

    @Test
    void dispatchShouldOnlyBeConfiguredOnce() {
        final XtreamServers.TcpServerBuilder tcpBuilder = XtreamServers.tcp();
        tcpBuilder.dispatch(dispatcher -> dispatcher
                .addHandlerMapping(exchange -> Mono.just("handler"))
                .enableBuiltinHandlers(EntityCodec.DEFAULT));
        assertThrows(IllegalStateException.class, () -> tcpBuilder.dispatch(dispatcher -> {
        }));

        final XtreamServers.UdpServerBuilder udpBuilder = XtreamServers.udp();
        udpBuilder.dispatch(dispatcher -> dispatcher
                .addHandlerMapping(exchange -> Mono.just("handler"))
                .enableBuiltinHandlers(EntityCodec.DEFAULT));
        assertThrows(IllegalStateException.class, () -> udpBuilder.dispatch(dispatcher -> {
        }));
    }

    @Test
    void lowLevelBuilderUsageShouldRemainValid() {
        final TcpXtreamServer tcpServer = XtreamServerBuilder.newTcpServerBuilder()
                .addServerCustomizer(server -> server.host("127.0.0.1").port(20080))
                .build("legacy-tcp");
        assertInstanceOf(TcpXtreamServer.class, tcpServer);

        final UdpXtreamServer udpServer = XtreamServerBuilder.newUdpServerBuilder()
                .addServerCustomizer(server -> server.host("127.0.0.1").port(20081))
                .build("legacy-udp");
        assertInstanceOf(UdpXtreamServer.class, udpServer);
    }

    private static <T> List<T> readListField(Object target, Class<?> owner, String fieldName, Class<T> elementType) throws ReflectiveOperationException {
        final List<?> values = (List<?>) readField(target, owner, fieldName);
        return values.stream().map(elementType::cast).toList();
    }

    private static Object readField(Object target, Class<?> owner, String fieldName) throws ReflectiveOperationException {
        final Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object readField(Object target, String fieldName) throws ReflectiveOperationException {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                final Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static TcpServer applyTcpCustomizers(List<TcpNettyServerCustomizer> customizers) {
        TcpServer tcpServer = TcpServer.create();
        for (TcpNettyServerCustomizer customizer : customizers) {
            tcpServer = customizer.customize(tcpServer);
        }
        return tcpServer;
    }

    private static UdpServer applyUdpCustomizers(List<UdpNettyServerCustomizer> customizers) {
        UdpServer udpServer = UdpServer.create();
        for (UdpNettyServerCustomizer customizer : customizers) {
            udpServer = customizer.customize(udpServer);
        }
        return udpServer;
    }
}
