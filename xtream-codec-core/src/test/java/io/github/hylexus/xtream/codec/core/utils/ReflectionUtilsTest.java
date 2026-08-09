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

package io.github.hylexus.xtream.codec.core.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReflectionUtilsTest {

    static class MyDemoHandler {

        public Mono<RegisterAckResponse> handleDeviceRegister(
                XtreamExchange exchange,
                DeviceRegisterRequest request) {
            return null;
        }

        public Map<String, ? extends DeviceRegisterRequest[]> handleGeneric(
                DeviceRegisterRequest[] requests,
                List<? super XtreamExchange> exchanges) {
            return null;
        }
    }

    static class Mono<T> {
    }

    static class RegisterAckResponse {
    }

    static class XtreamExchange {
    }

    static class DeviceRegisterRequest {
    }

    @Test
    void shouldFormatMethodWithSimpleNamesAndGenericReturnType() throws Exception {
        final Method method = MyDemoHandler.class.getMethod(
                "handleDeviceRegister",
                XtreamExchange.class,
                DeviceRegisterRequest.class
        );

        assertEquals(
                "public Mono<RegisterAckResponse> MyDemoHandler#handleDeviceRegister(XtreamExchange,DeviceRegisterRequest)",
                ReflectionUtils.formatMethod(method)
        );
    }

    @Test
    void shouldFormatGenericArraysAndWildcards() throws Exception {
        final Method method = MyDemoHandler.class.getMethod(
                "handleGeneric",
                DeviceRegisterRequest[].class,
                List.class
        );

        assertEquals(
                "public Map<String,? extends DeviceRegisterRequest[]> MyDemoHandler#handleGeneric(DeviceRegisterRequest[],List<? super XtreamExchange>)",
                ReflectionUtils.formatMethod(method)
        );
    }
}
