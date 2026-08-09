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

package io.github.hylexus.xtream.codec.ext.jt808.boot.configuration.instruction;

import io.github.hylexus.xtream.codec.ext.jt808.boot.properties.XtreamJt808ServerProperties;
import io.github.hylexus.xtream.codec.ext.jt808.spec.Jt808SessionManager;
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

class BuiltinJt808InstructionServerTcpConfigurationTest {

    @Test
    @SuppressWarnings("unchecked")
    void tcpServerBeanKeepsNameAndCustomizerChain() throws Exception {
        final XtreamJt808ServerProperties properties = new XtreamJt808ServerProperties();
        properties.getInstructionServer().getTcpServer().setHost("127.0.0.1");
        properties.getInstructionServer().getTcpServer().setPort(18080);

        final ObjectProvider<TcpNettyServerCustomizer> customizers = mock(ObjectProvider.class);
        when(customizers.stream()).thenReturn(Stream.of(server -> server.port(19080)));

        final TcpXtreamServer server = new BuiltinJt808InstructionServerTcpConfiguration().tcpXtreamServer(
                mock(TcpXtreamNettyHandlerAdapter.class),
                mock(TcpXtreamNettyResourceFactory.class),
                customizers,
                mock(Jt808SessionManager.class),
                properties
        );

        assertEquals("JT/T-808-INSTRUCTION", serverName(server));
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
