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
import io.github.hylexus.xtream.codec.ext.jt1078.utils.Jt1078Constants;
import io.github.hylexus.xtream.codec.server.reactive.spec.TcpXtreamNettyHandlerAdapter;
import io.github.hylexus.xtream.codec.server.reactive.spec.UdpXtreamNettyHandlerAdapter;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.XtreamServerBuilder;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpNettyServerCustomizer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpXtreamServer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.udp.UdpNettyServerCustomizer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.udp.UdpXtreamServer;
import io.github.hylexus.xtream.codec.server.reactive.spec.domain.values.TcpSessionIdleStateCheckerProps;
import io.github.hylexus.xtream.codec.server.reactive.spec.resources.TcpXtreamNettyResourceFactory;
import io.github.hylexus.xtream.codec.server.reactive.spec.resources.UdpXtreamNettyResourceFactory;
import io.github.hylexus.xtream.codec.server.reactive.utils.BuiltinConfigurationUtils;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.jspecify.annotations.Nullable;
import reactor.netty.Connection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * JT/T 1078 协议服务器构建入口。
 *
 * @author hylexus
 * @author Codex (AI)
 * @since 0.7.0
 */
public final class Jt1078Servers {
    private static final String TCP_NAME = "JT/T-1078";
    private static final String UDP_NAME = "JT/T-1078";

    private Jt1078Servers() {
        throw new UnsupportedOperationException("no instance");
    }

    public static TcpServerBuilder tcp() {
        return new TcpServerBuilder();
    }

    public static UdpServerBuilder udp() {
        return new UdpServerBuilder();
    }

    private abstract static class AbstractTcpServerBuilder<B extends AbstractTcpServerBuilder<B>> {
        String host = "";
        int port;
        String name;
        @Nullable TcpXtreamNettyResourceFactory resourceFactory;
        @Nullable TcpXtreamNettyHandlerAdapter handlerAdapter;
        @Nullable Jt1078SessionManager sessionManager;
        @Nullable Jt1078SimConverter simConverter;
        @Nullable TcpSessionIdleStateCheckerProps sessionIdleStateChecker;
        int maxFrameLength = 8192;
        final List<TcpNettyServerCustomizer> customizers = new ArrayList<>();

        AbstractTcpServerBuilder(String name) {
            this.name = name;
        }

        B name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return self();
        }

        B bind(@Nullable String host, int port) {
            this.host = host == null ? "" : host;
            this.port = port;
            return self();
        }

        B resourceFactory(TcpXtreamNettyResourceFactory resourceFactory) {
            this.resourceFactory = Objects.requireNonNull(resourceFactory, "resourceFactory");
            return self();
        }

        B handlerAdapter(TcpXtreamNettyHandlerAdapter handlerAdapter) {
            this.handlerAdapter = Objects.requireNonNull(handlerAdapter, "handlerAdapter");
            return self();
        }

        B sessionManager(Jt1078SessionManager sessionManager) {
            this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
            return self();
        }

        B simConverter(Jt1078SimConverter simConverter) {
            this.simConverter = Objects.requireNonNull(simConverter, "simConverter");
            return self();
        }

        B sessionIdleStateChecker(TcpSessionIdleStateCheckerProps sessionIdleStateChecker) {
            this.sessionIdleStateChecker = Objects.requireNonNull(sessionIdleStateChecker, "sessionIdleStateChecker");
            return self();
        }

        B maxFrameLength(int maxFrameLength) {
            this.maxFrameLength = maxFrameLength;
            return self();
        }

        B customize(TcpNettyServerCustomizer customizer) {
            this.customizers.add(Objects.requireNonNull(customizer, "customizer"));
            return self();
        }

        B customizers(Collection<? extends TcpNettyServerCustomizer> customizers) {
            this.customizers.addAll(customizers);
            return self();
        }

        TcpNettyServerCustomizer basicConfigurer() {
            return BuiltinConfigurationUtils.defaultTcpBasicConfigurer(this.host, this.port);
        }

        abstract B self();
    }

    private abstract static class AbstractUdpServerBuilder<B extends AbstractUdpServerBuilder<B>> {
        String host = "";
        int port;
        String name;
        @Nullable UdpXtreamNettyResourceFactory resourceFactory;
        @Nullable UdpXtreamNettyHandlerAdapter handlerAdapter;
        final List<UdpNettyServerCustomizer> customizers = new ArrayList<>();

        AbstractUdpServerBuilder(String name) {
            this.name = name;
        }

        B name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return self();
        }

        B bind(@Nullable String host, int port) {
            this.host = host == null ? "" : host;
            this.port = port;
            return self();
        }

        B resourceFactory(UdpXtreamNettyResourceFactory resourceFactory) {
            this.resourceFactory = Objects.requireNonNull(resourceFactory, "resourceFactory");
            return self();
        }

        B handlerAdapter(UdpXtreamNettyHandlerAdapter handlerAdapter) {
            this.handlerAdapter = Objects.requireNonNull(handlerAdapter, "handlerAdapter");
            return self();
        }

        B customize(UdpNettyServerCustomizer customizer) {
            this.customizers.add(Objects.requireNonNull(customizer, "customizer"));
            return self();
        }

        B customizers(Collection<? extends UdpNettyServerCustomizer> customizers) {
            this.customizers.addAll(customizers);
            return self();
        }

        UdpNettyServerCustomizer basicConfigurer() {
            return BuiltinConfigurationUtils.defaultUdpBasicConfigurer(this.host, this.port);
        }

        abstract B self();
    }

    public static final class TcpServerBuilder extends AbstractTcpServerBuilder<TcpServerBuilder> {
        private TcpServerBuilder() {
            super(TCP_NAME);
        }

        @Override
        public TcpServerBuilder name(String name) {
            return super.name(name);
        }

        @Override
        public TcpServerBuilder bind(@Nullable String host, int port) {
            return super.bind(host, port);
        }

        @Override
        public TcpServerBuilder resourceFactory(TcpXtreamNettyResourceFactory resourceFactory) {
            return super.resourceFactory(resourceFactory);
        }

        @Override
        public TcpServerBuilder handlerAdapter(TcpXtreamNettyHandlerAdapter handlerAdapter) {
            return super.handlerAdapter(handlerAdapter);
        }

        @Override
        public TcpServerBuilder sessionManager(Jt1078SessionManager sessionManager) {
            return super.sessionManager(sessionManager);
        }

        @Override
        public TcpServerBuilder simConverter(Jt1078SimConverter simConverter) {
            return super.simConverter(simConverter);
        }

        @Override
        public TcpServerBuilder sessionIdleStateChecker(TcpSessionIdleStateCheckerProps sessionIdleStateChecker) {
            return super.sessionIdleStateChecker(sessionIdleStateChecker);
        }

        @Override
        public TcpServerBuilder maxFrameLength(int maxFrameLength) {
            return super.maxFrameLength(maxFrameLength);
        }

        @Override
        public TcpServerBuilder customize(TcpNettyServerCustomizer customizer) {
            return super.customize(customizer);
        }

        @Override
        public TcpServerBuilder customizers(Collection<? extends TcpNettyServerCustomizer> customizers) {
            return super.customizers(customizers);
        }

        public TcpXtreamServer build() {
            final TcpXtreamNettyResourceFactory resourceFactory = Objects.requireNonNull(this.resourceFactory, "resourceFactory");
            final TcpXtreamNettyHandlerAdapter handlerAdapter = Objects.requireNonNull(this.handlerAdapter, "handlerAdapter");
            final Jt1078SessionManager sessionManager = Objects.requireNonNull(this.sessionManager, "sessionManager");
            final Jt1078SimConverter simConverter = Objects.requireNonNull(this.simConverter, "simConverter");
            final TcpSessionIdleStateCheckerProps sessionIdleStateChecker = Objects.requireNonNull(this.sessionIdleStateChecker, "sessionIdleStateChecker");
            return XtreamServerBuilder.newTcpServerBuilder()
                    // 默认 host和 port(用户自定义配置可以再次覆盖默认配置)
                    .addServerCustomizer(basicConfigurer())
                    // handler
                    .addServerCustomizer(server -> server.handle(handlerAdapter))
                    // 分包 + 请求解码 + 空闲检测
                    .addServerCustomizer(server -> server.doOnConnection(connection -> {
                        // 1. 分包(stripDelimiter=true)
                        // 1078 TCP 数据帧以 0x30 0x31 0x63 0x64 作为分隔符，拆包后再解析请求头。
                        final int frameLength = this.maxFrameLength;
                        final DelimiterBasedFrameDecoder frameDecoder = new DelimiterBasedFrameDecoder(
                                frameLength,
                                true,
                                Unpooled.copiedBuffer(new byte[]{0x30, 0x31, 0x63, 0x64})
                        );
                        // 2. 请求解码
                        connection.addHandlerLast(Jt1078Constants.BEAN_NAME_JT1078_CHANNEL_FRAME_DECODER, frameDecoder);
                        connection.addHandlerLast(
                                Jt1078Constants.BEAN_NAME_JT1078_REQUEST_DECODER,
                                new Jt1078ByteToMessageDecoder(simConverter, connection, sessionManager)
                        );
                        // 3. 空闲检测
                        addTcpIdleStateHandler(sessionManager, sessionIdleStateChecker, connection);
                    }))
                    // loopResources
                    .addServerCustomizer(server -> server.runOn(resourceFactory.loopResources(), resourceFactory.preferNative()))
                    // 用户自定义配置
                    .addServerCustomizers(this.customizers)
                    .build(this.name);
        }

        @Override
        TcpServerBuilder self() {
            return this;
        }
    }

    public static final class UdpServerBuilder extends AbstractUdpServerBuilder<UdpServerBuilder> {
        private UdpServerBuilder() {
            super(UDP_NAME);
        }

        @Override
        public UdpServerBuilder name(String name) {
            return super.name(name);
        }

        @Override
        public UdpServerBuilder bind(@Nullable String host, int port) {
            return super.bind(host, port);
        }

        @Override
        public UdpServerBuilder resourceFactory(UdpXtreamNettyResourceFactory resourceFactory) {
            return super.resourceFactory(resourceFactory);
        }

        @Override
        public UdpServerBuilder handlerAdapter(UdpXtreamNettyHandlerAdapter handlerAdapter) {
            return super.handlerAdapter(handlerAdapter);
        }

        @Override
        public UdpServerBuilder customize(UdpNettyServerCustomizer customizer) {
            return super.customize(customizer);
        }

        @Override
        public UdpServerBuilder customizers(Collection<? extends UdpNettyServerCustomizer> customizers) {
            return super.customizers(customizers);
        }

        public UdpXtreamServer build() {
            final UdpXtreamNettyResourceFactory resourceFactory = Objects.requireNonNull(this.resourceFactory, "resourceFactory");
            final UdpXtreamNettyHandlerAdapter handlerAdapter = Objects.requireNonNull(this.handlerAdapter, "handlerAdapter");
            return XtreamServerBuilder.newUdpServerBuilder()
                    // 默认 host和 port(用户自定义配置可以再次覆盖默认配置)
                    .addServerCustomizer(basicConfigurer())
                    // handler
                    .addServerCustomizer(server -> server.handle(handlerAdapter))
                    // loopResources
                    .addServerCustomizer(server -> server.runOn(resourceFactory.loopResources(), resourceFactory.preferNative()))
                    // 用户自定义配置
                    .addServerCustomizers(this.customizers)
                    .build(this.name);
        }

        @Override
        UdpServerBuilder self() {
            return this;
        }
    }

    private static void addTcpIdleStateHandler(Jt1078SessionManager sessionManager, TcpSessionIdleStateCheckerProps props, Connection connection) {
        connection.addHandlerLast(
                "xtreamTcpIdleStateHandler",
                new IdleStateHandler(
                        props.getReaderIdleTime().toMillis(),
                        props.getWriterIdleTime().toMillis(),
                        props.getAllIdleTime().toMillis(),
                        TimeUnit.MILLISECONDS
                )
        );
        connection.addHandlerLast(
                "xtreamTcpIdleStateHandlerCallback",
                new Jt1078TcpHeatBeatHandler(sessionManager)
        );
    }
}
