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
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.XtreamServerBuilder;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpNettyServerCustomizer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpXtreamServer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.udp.UdpNettyServerCustomizer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.udp.UdpXtreamServer;
import io.github.hylexus.xtream.codec.server.reactive.spec.resources.TcpXtreamNettyResourceFactory;
import io.github.hylexus.xtream.codec.server.reactive.spec.resources.UdpXtreamNettyResourceFactory;
import io.github.hylexus.xtream.codec.server.reactive.utils.BuiltinConfigurationUtils;
import io.github.hylexus.xtream.codec.ext.jt808.utils.JtProtocolConstant;
import io.github.hylexus.xtream.codec.server.reactive.spec.domain.values.TcpSessionIdleStateCheckerProps;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static io.github.hylexus.xtream.codec.ext.jt808.utils.BuiltinConfigurationUtils.addIdleStateHandler;

/**
 * JT/T 808 协议服务器构建入口。
 *
 * @author hylexus
 * @author Codex (AI)
 * @since 0.7.0
 */
public final class Jt808Servers {
    private static final String INSTRUCTION_TCP_NAME = "JT/T-808-INSTRUCTION";
    private static final String INSTRUCTION_UDP_NAME = "JT/T-808-INSTRUCTION";
    private static final String ATTACHMENT_TCP_NAME = "JT/T-808-ATTACHMENT";
    private static final String ATTACHMENT_UDP_NAME = "JT/T-808-ATTACHMENT";

    private Jt808Servers() {
        throw new UnsupportedOperationException("no instance");
    }

    public static InstructionTcpServerBuilder instructionTcp() {
        return new InstructionTcpServerBuilder();
    }

    public static InstructionUdpServerBuilder instructionUdp() {
        return new InstructionUdpServerBuilder();
    }

    public static AttachmentTcpServerBuilder attachmentTcp() {
        return new AttachmentTcpServerBuilder();
    }

    public static AttachmentUdpServerBuilder attachmentUdp() {
        return new AttachmentUdpServerBuilder();
    }

    private abstract static class AbstractTcpServerBuilder<B extends AbstractTcpServerBuilder<B>> {
        String host = "";
        int port;
        String name;
        @Nullable TcpXtreamNettyResourceFactory resourceFactory;
        @Nullable TcpXtreamNettyHandlerAdapter handlerAdapter;
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

    public static final class InstructionTcpServerBuilder extends AbstractTcpServerBuilder<InstructionTcpServerBuilder> {
        private @Nullable Jt808SessionManager sessionManager;
        private @Nullable TcpSessionIdleStateCheckerProps sessionIdleStateChecker;
        private int maxInstructionFrameLength = JtProtocolConstant.DEFAULT_MAX_INSTRUCTION_FRAME_LENGTH;

        private InstructionTcpServerBuilder() {
            super(INSTRUCTION_TCP_NAME);
        }

        @Override
        public InstructionTcpServerBuilder name(String name) {
            return super.name(name);
        }

        @Override
        public InstructionTcpServerBuilder bind(@Nullable String host, int port) {
            return super.bind(host, port);
        }

        @Override
        public InstructionTcpServerBuilder resourceFactory(TcpXtreamNettyResourceFactory resourceFactory) {
            return super.resourceFactory(resourceFactory);
        }

        @Override
        public InstructionTcpServerBuilder handlerAdapter(TcpXtreamNettyHandlerAdapter handlerAdapter) {
            return super.handlerAdapter(handlerAdapter);
        }

        @Override
        public InstructionTcpServerBuilder customize(TcpNettyServerCustomizer customizer) {
            return super.customize(customizer);
        }

        @Override
        public InstructionTcpServerBuilder customizers(Collection<? extends TcpNettyServerCustomizer> customizers) {
            return super.customizers(customizers);
        }

        public InstructionTcpServerBuilder sessionManager(Jt808SessionManager sessionManager) {
            this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
            return this;
        }

        public InstructionTcpServerBuilder sessionIdleStateChecker(TcpSessionIdleStateCheckerProps sessionIdleStateChecker) {
            this.sessionIdleStateChecker = Objects.requireNonNull(sessionIdleStateChecker, "sessionIdleStateChecker");
            return this;
        }

        public InstructionTcpServerBuilder maxInstructionFrameLength(int maxInstructionFrameLength) {
            this.maxInstructionFrameLength = maxInstructionFrameLength;
            return this;
        }

        public TcpXtreamServer build() {
            final Jt808SessionManager sessionManager = Objects.requireNonNull(this.sessionManager, "sessionManager");
            final TcpSessionIdleStateCheckerProps sessionIdleStateChecker = Objects.requireNonNull(this.sessionIdleStateChecker, "sessionIdleStateChecker");
            final TcpXtreamNettyHandlerAdapter handlerAdapter = Objects.requireNonNull(this.handlerAdapter, "handlerAdapter");
            final TcpXtreamNettyResourceFactory resourceFactory = Objects.requireNonNull(this.resourceFactory, "resourceFactory");
            return XtreamServerBuilder.newTcpServerBuilder()
                    // 默认 host和 port(用户自定义配置可以再次覆盖默认配置)
                    .addServerCustomizer(basicConfigurer())
                    // handler
                    .addServerCustomizer(server -> server.handle(handlerAdapter))
                    // 分包 + 空闲检测
                    .addServerCustomizer(server -> server.doOnConnection(connection -> {
                        // 空闲检测
                        addIdleStateHandler(sessionIdleStateChecker, sessionManager, null, connection);

                        // 分包
                        // stripDelimiter=true
                        // 指令服务器使用 0x7e 分隔符拆包，并在拆包前挂载空闲状态检测处理器。
                        final DelimiterBasedFrameDecoder frameDecoder = new DelimiterBasedFrameDecoder(
                                this.maxInstructionFrameLength,
                                true,
                                Unpooled.copiedBuffer(new byte[]{JtProtocolConstant.PACKAGE_DELIMITER})
                        );
                        connection.addHandlerFirst(JtProtocolConstant.BEAN_NAME_CHANNEL_INBOUND_HANDLER_ADAPTER, frameDecoder);
                    }))
                    // loopResources
                    .addServerCustomizer(server -> server.runOn(resourceFactory.loopResources(), resourceFactory.preferNative()))
                    // 用户自定义配置
                    .addServerCustomizers(this.customizers)
                    .build(this.name);
        }

        @Override
        InstructionTcpServerBuilder self() {
            return this;
        }
    }

    public static final class InstructionUdpServerBuilder extends AbstractUdpServerBuilder<InstructionUdpServerBuilder> {
        private InstructionUdpServerBuilder() {
            super(INSTRUCTION_UDP_NAME);
        }

        @Override
        public InstructionUdpServerBuilder name(String name) {
            return super.name(name);
        }

        @Override
        public InstructionUdpServerBuilder bind(@Nullable String host, int port) {
            return super.bind(host, port);
        }

        @Override
        public InstructionUdpServerBuilder resourceFactory(UdpXtreamNettyResourceFactory resourceFactory) {
            return super.resourceFactory(resourceFactory);
        }

        @Override
        public InstructionUdpServerBuilder handlerAdapter(UdpXtreamNettyHandlerAdapter handlerAdapter) {
            return super.handlerAdapter(handlerAdapter);
        }

        @Override
        public InstructionUdpServerBuilder customize(UdpNettyServerCustomizer customizer) {
            return super.customize(customizer);
        }

        @Override
        public InstructionUdpServerBuilder customizers(Collection<? extends UdpNettyServerCustomizer> customizers) {
            return super.customizers(customizers);
        }

        public UdpXtreamServer build() {
            final UdpXtreamNettyHandlerAdapter handlerAdapter = Objects.requireNonNull(this.handlerAdapter, "handlerAdapter");
            final UdpXtreamNettyResourceFactory resourceFactory = Objects.requireNonNull(this.resourceFactory, "resourceFactory");
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
        InstructionUdpServerBuilder self() {
            return this;
        }
    }

    public static final class AttachmentTcpServerBuilder extends AbstractTcpServerBuilder<AttachmentTcpServerBuilder> {
        private @Nullable Jt808AttachmentSessionManager attachmentSessionManager;
        private @Nullable TcpSessionIdleStateCheckerProps sessionIdleStateChecker;
        private int maxInstructionFrameLength = JtProtocolConstant.DEFAULT_MAX_INSTRUCTION_FRAME_LENGTH;
        private int maxStreamFrameLength = JtProtocolConstant.DEFAULT_MAX_STREAM_FRAME_LENGTH;

        private AttachmentTcpServerBuilder() {
            super(ATTACHMENT_TCP_NAME);
        }

        @Override
        public AttachmentTcpServerBuilder name(String name) {
            return super.name(name);
        }

        @Override
        public AttachmentTcpServerBuilder bind(@Nullable String host, int port) {
            return super.bind(host, port);
        }

        @Override
        public AttachmentTcpServerBuilder resourceFactory(TcpXtreamNettyResourceFactory resourceFactory) {
            return super.resourceFactory(resourceFactory);
        }

        @Override
        public AttachmentTcpServerBuilder handlerAdapter(TcpXtreamNettyHandlerAdapter handlerAdapter) {
            return super.handlerAdapter(handlerAdapter);
        }

        @Override
        public AttachmentTcpServerBuilder customize(TcpNettyServerCustomizer customizer) {
            return super.customize(customizer);
        }

        @Override
        public AttachmentTcpServerBuilder customizers(Collection<? extends TcpNettyServerCustomizer> customizers) {
            return super.customizers(customizers);
        }

        public AttachmentTcpServerBuilder attachmentSessionManager(Jt808AttachmentSessionManager attachmentSessionManager) {
            this.attachmentSessionManager = Objects.requireNonNull(attachmentSessionManager, "attachmentSessionManager");
            return this;
        }

        public AttachmentTcpServerBuilder sessionIdleStateChecker(TcpSessionIdleStateCheckerProps sessionIdleStateChecker) {
            this.sessionIdleStateChecker = Objects.requireNonNull(sessionIdleStateChecker, "sessionIdleStateChecker");
            return this;
        }

        public AttachmentTcpServerBuilder maxInstructionFrameLength(int maxInstructionFrameLength) {
            this.maxInstructionFrameLength = maxInstructionFrameLength;
            return this;
        }

        public AttachmentTcpServerBuilder maxStreamFrameLength(int maxStreamFrameLength) {
            this.maxStreamFrameLength = maxStreamFrameLength;
            return this;
        }

        public TcpXtreamServer build() {
            final Jt808AttachmentSessionManager attachmentSessionManager = Objects.requireNonNull(this.attachmentSessionManager, "attachmentSessionManager");
            final TcpSessionIdleStateCheckerProps sessionIdleStateChecker = Objects.requireNonNull(this.sessionIdleStateChecker, "sessionIdleStateChecker");
            final TcpXtreamNettyHandlerAdapter handlerAdapter = Objects.requireNonNull(this.handlerAdapter, "handlerAdapter");
            final TcpXtreamNettyResourceFactory resourceFactory = Objects.requireNonNull(this.resourceFactory, "resourceFactory");
            return XtreamServerBuilder.newTcpServerBuilder()
                    // 默认 host和 port(用户自定义配置可以再次覆盖默认配置)
                    .addServerCustomizer(basicConfigurer())
                    // handler
                    .addServerCustomizer(server -> server.handle(handlerAdapter))
                    // 分包 + 空闲检测
                    .addServerCustomizer(server -> server.doOnConnection(connection -> {
                        // 空闲检测
                        addIdleStateHandler(sessionIdleStateChecker, null, attachmentSessionManager, connection);
                        // 分包
                        // stripDelimiter=true
                        // 附件服务器需要同时识别指令帧和数据流帧，拆包逻辑由专用解码器处理。
                        final DelimiterAndLengthFieldBasedByteToMessageDecoder frameDecoder = new DelimiterAndLengthFieldBasedByteToMessageDecoder(
                                this.maxInstructionFrameLength,
                                this.maxStreamFrameLength
                        );
                        connection.addHandlerFirst(JtProtocolConstant.BEAN_NAME_CHANNEL_INBOUND_HANDLER_ADAPTER, frameDecoder);
                    }))
                    // loopResources
                    .addServerCustomizer(server -> server.runOn(resourceFactory.loopResources(), resourceFactory.preferNative()))
                    // 用户自定义配置
                    .addServerCustomizers(this.customizers)
                    .build(this.name);
        }

        @Override
        AttachmentTcpServerBuilder self() {
            return this;
        }
    }

    public static final class AttachmentUdpServerBuilder extends AbstractUdpServerBuilder<AttachmentUdpServerBuilder> {
        private AttachmentUdpServerBuilder() {
            super(ATTACHMENT_UDP_NAME);
        }

        @Override
        public AttachmentUdpServerBuilder name(String name) {
            return super.name(name);
        }

        @Override
        public AttachmentUdpServerBuilder bind(@Nullable String host, int port) {
            return super.bind(host, port);
        }

        @Override
        public AttachmentUdpServerBuilder resourceFactory(UdpXtreamNettyResourceFactory resourceFactory) {
            return super.resourceFactory(resourceFactory);
        }

        @Override
        public AttachmentUdpServerBuilder handlerAdapter(UdpXtreamNettyHandlerAdapter handlerAdapter) {
            return super.handlerAdapter(handlerAdapter);
        }

        @Override
        public AttachmentUdpServerBuilder customize(UdpNettyServerCustomizer customizer) {
            return super.customize(customizer);
        }

        @Override
        public AttachmentUdpServerBuilder customizers(Collection<? extends UdpNettyServerCustomizer> customizers) {
            return super.customizers(customizers);
        }

        public UdpXtreamServer build() {
            final UdpXtreamNettyHandlerAdapter handlerAdapter = Objects.requireNonNull(this.handlerAdapter, "handlerAdapter");
            final UdpXtreamNettyResourceFactory resourceFactory = Objects.requireNonNull(this.resourceFactory, "resourceFactory");
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
        AttachmentUdpServerBuilder self() {
            return this;
        }
    }

}
