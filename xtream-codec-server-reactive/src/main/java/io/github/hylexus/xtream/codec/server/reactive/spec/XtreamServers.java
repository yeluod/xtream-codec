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
import io.github.hylexus.xtream.codec.server.reactive.spec.handler.XtreamHandlerAdapter;
import io.github.hylexus.xtream.codec.server.reactive.spec.handler.XtreamHandlerMapping;
import io.github.hylexus.xtream.codec.server.reactive.spec.handler.XtreamHandlerResultHandler;
import io.github.hylexus.xtream.codec.server.reactive.spec.handler.XtreamRequestExceptionHandler;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.AbstractXtreamHandlerAdapterBuilder;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpNettyServerCustomizer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpXtreamHandlerAdapterBuilder;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpXtreamServer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.udp.UdpNettyServerCustomizer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.udp.UdpXtreamHandlerAdapterBuilder;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.udp.UdpXtreamServer;
import io.netty.channel.ChannelPipeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 协议无关的服务端入口。
 */
public final class XtreamServers {

    private XtreamServers() {
        throw new UnsupportedOperationException("no instance");
    }

    public static TcpServerBuilder tcp() {
        return new TcpServerBuilder();
    }

    public static UdpServerBuilder udp() {
        return new UdpServerBuilder();
    }

    public static final class DispatcherBuilder {
        private final AbstractXtreamHandlerAdapterBuilder<?> delegate;

        private DispatcherBuilder(AbstractXtreamHandlerAdapterBuilder<?> delegate) {
            this.delegate = delegate;
        }

        public static DispatcherBuilder tcp() {
            return new DispatcherBuilder(new TcpXtreamHandlerAdapterBuilder(io.netty.buffer.ByteBufAllocator.DEFAULT));
        }

        public static DispatcherBuilder udp() {
            return new DispatcherBuilder(new UdpXtreamHandlerAdapterBuilder(io.netty.buffer.ByteBufAllocator.DEFAULT));
        }

        public DispatcherBuilder addHandlerMapping(XtreamHandlerMapping handlerMapping) {
            this.delegate.addHandlerMapping(handlerMapping);
            return this;
        }

        public DispatcherBuilder addHandlerMappings(XtreamHandlerMapping... handlerMappings) {
            this.delegate.addHandlerMappings(handlerMappings);
            return this;
        }

        public DispatcherBuilder addHandlerMappings(Collection<XtreamHandlerMapping> handlerMappings) {
            this.delegate.addHandlerMappings(handlerMappings);
            return this;
        }

        public DispatcherBuilder addHandlerAdapter(XtreamHandlerAdapter handlerAdapter) {
            this.delegate.addHandlerAdapter(handlerAdapter);
            return this;
        }

        public DispatcherBuilder addHandlerAdapters(Collection<XtreamHandlerAdapter> handlerAdapters) {
            this.delegate.addHandlerAdapters(handlerAdapters);
            return this;
        }

        public DispatcherBuilder addHandlerResultHandler(XtreamHandlerResultHandler resultHandler) {
            this.delegate.addHandlerResultHandler(resultHandler);
            return this;
        }

        public DispatcherBuilder addHandlerResultHandlers(Collection<XtreamHandlerResultHandler> resultHandlers) {
            this.delegate.addHandlerResultHandlers(resultHandlers);
            return this;
        }

        public DispatcherBuilder addFilter(XtreamFilter filter) {
            this.delegate.addFilter(filter);
            return this;
        }

        public DispatcherBuilder addFilters(Collection<XtreamFilter> filters) {
            this.delegate.addFilters(filters);
            return this;
        }

        public DispatcherBuilder addExceptionHandler(XtreamRequestExceptionHandler exceptionHandler) {
            this.delegate.addExceptionHandler(exceptionHandler);
            return this;
        }

        public DispatcherBuilder addExceptionHandlers(Collection<XtreamRequestExceptionHandler> exceptionHandlers) {
            this.delegate.addExceptionHandlers(exceptionHandlers);
            return this;
        }

        public DispatcherBuilder sessionManager(XtreamSessionManager<? extends XtreamSession> sessionManager) {
            this.delegate.sessionManager(sessionManager);
            return this;
        }

        public DispatcherBuilder enableBuiltinHandlers(EntityCodec entityCodec) {
            this.delegate.enableBuiltinHandlerAdapters(entityCodec);
            this.delegate.enableBuiltinHandlerResultHandlers(entityCodec);
            return this;
        }

        public XtreamNettyHandlerAdapter build() {
            return this.delegate.build();
        }
    }

    public static final class TcpServerBuilder {
        private final List<TcpNettyServerCustomizer> customizers = new ArrayList<>();
        private String name = "xtream-tcp-server";
        private boolean dispatchConfigured;

        public TcpServerBuilder name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        public TcpServerBuilder bind(String host, int port) {
            final String bindHost = Objects.requireNonNull(host, "host");
            this.customizers.add(server -> server.host(bindHost).port(port));
            return this;
        }

        public TcpServerBuilder pipeline(Consumer<ChannelPipeline> customizer) {
            final Consumer<ChannelPipeline> pipelineCustomizer = Objects.requireNonNull(customizer, "customizer");
            this.customizers.add(server -> {
                // ...
                return server.doOnChannelInit((observer, channel, remoteAddress) -> {
                    // ...
                    pipelineCustomizer.accept(channel.pipeline());
                });
            });
            return this;
        }

        public TcpServerBuilder dispatch(Consumer<DispatcherBuilder> customizer) {
            if (this.dispatchConfigured) {
                throw new IllegalStateException("dispatch(...) can only be called once");
            }
            final DispatcherBuilder dispatcherBuilder = DispatcherBuilder.tcp();
            Objects.requireNonNull(customizer, "customizer").accept(dispatcherBuilder);
            final XtreamNettyHandlerAdapter handlerAdapter = dispatcherBuilder.build();
            this.dispatchConfigured = true;
            this.customizers.add(server -> server.handle(handlerAdapter));
            return this;
        }

        public TcpServerBuilder customize(TcpNettyServerCustomizer customizer) {
            this.customizers.add(Objects.requireNonNull(customizer, "customizer"));
            return this;
        }

        public TcpXtreamServer build() {
            return new TcpXtreamServer(this.name, this.customizers);
        }
    }

    public static final class UdpServerBuilder {
        private final List<UdpNettyServerCustomizer> customizers = new ArrayList<>();
        private String name = "xtream-udp-server";
        private boolean dispatchConfigured;

        public UdpServerBuilder name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        public UdpServerBuilder bind(String host, int port) {
            final String bindHost = Objects.requireNonNull(host, "host");
            this.customizers.add(server -> server.host(bindHost).port(port));
            return this;
        }

        public UdpServerBuilder pipeline(Consumer<ChannelPipeline> customizer) {
            final Consumer<ChannelPipeline> pipelineCustomizer = Objects.requireNonNull(customizer, "customizer");
            this.customizers.add(server -> server.doOnChannelInit((observer, channel, remoteAddress) -> pipelineCustomizer.accept(channel.pipeline())));
            return this;
        }

        public UdpServerBuilder dispatch(Consumer<DispatcherBuilder> customizer) {
            if (this.dispatchConfigured) {
                throw new IllegalStateException("dispatch(...) can ONLY be called ONCE");
            }
            final DispatcherBuilder dispatcherBuilder = DispatcherBuilder.udp();
            Objects.requireNonNull(customizer, "customizer").accept(dispatcherBuilder);
            final XtreamNettyHandlerAdapter handlerAdapter = dispatcherBuilder.build();
            this.dispatchConfigured = true;
            this.customizers.add(server -> server.handle(handlerAdapter));
            return this;
        }

        public UdpServerBuilder customize(UdpNettyServerCustomizer customizer) {
            this.customizers.add(Objects.requireNonNull(customizer, "customizer"));
            return this;
        }

        public UdpXtreamServer build() {
            return new UdpXtreamServer(this.name, this.customizers);
        }
    }
}
