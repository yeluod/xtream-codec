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

package io.github.hylexus.xtream.codec.ext.jt1078.boot.configuration;

import io.github.hylexus.xtream.codec.ext.jt1078.boot.properties.XtreamJt1078ServerProperties;
import io.github.hylexus.xtream.codec.ext.jt1078.spec.Jt1078SessionManager;
import io.github.hylexus.xtream.codec.ext.jt1078.spec.Jt1078SimConverter;
import io.github.hylexus.xtream.codec.server.reactive.spec.TcpXtreamNettyHandlerAdapter;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.AbstractXtreamServer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpNettyServerCustomizer;
import io.github.hylexus.xtream.codec.server.reactive.spec.impl.tcp.TcpXtreamServer;
import io.github.hylexus.xtream.codec.server.reactive.spec.resources.TcpXtreamNettyResourceFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuiltinJt1078ServerTcpConfigurationTest {

    @Test
    @SuppressWarnings("unchecked")
    void tcpServerBeanKeepsNameAndCustomizerChain() throws Exception {
        final XtreamJt1078ServerProperties properties = new XtreamJt1078ServerProperties();
        properties.getTcpServer().setHost("127.0.0.1");
        properties.getTcpServer().setPort(10780);

        final ObjectProvider<TcpNettyServerCustomizer> customizers = mock(ObjectProvider.class);
        when(customizers.stream()).thenReturn(Stream.of(server -> server.port(11780)));

        final TcpXtreamServer server = new BuiltinJt1078ServerTcpConfiguration().tcpXtreamServer(
                mock(TcpXtreamNettyHandlerAdapter.class),
                mock(TcpXtreamNettyResourceFactory.class),
                customizers,
                mock(Jt1078SessionManager.class),
                properties,
                mock(Jt1078SimConverter.class)
        );

        assertEquals("JT/T-1078", serverName(server));
        assertEquals(6, tcpCustomizers(server).size());
    }

    private static String serverName(TcpXtreamServer server) throws Exception {
        final Field field = AbstractXtreamServer.class.getDeclaredField("name");
        field.setAccessible(true);
        return (String) field.get(server);
    }

    @SuppressWarnings("unchecked")
    private static List<TcpNettyServerCustomizer> tcpCustomizers(TcpXtreamServer server) throws Exception {
        final Field field = TcpXtreamServer.class.getDeclaredField("nettyServerCustomizers");
        field.setAccessible(true);
        return (List<TcpNettyServerCustomizer>) field.get(server);
    }
}
