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

import io.github.hylexus.xtream.codec.core.annotation.XtreamField;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class ReflectionUtils {
    private ReflectionUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * 将反射方法格式化为便于日志阅读的形式。
     *
     * @param method 待格式化的方法
     * @return 格式化后的方法描述
     * @since 0.7.0
     */
    public static String formatMethod(Method method) {
        Objects.requireNonNull(method, "method");

        final StringBuilder result = new StringBuilder();
        final String modifiers = Modifier.toString(method.getModifiers());
        if (!modifiers.isEmpty()) {
            result.append(modifiers).append(' ');
        }

        result.append(formatType(method.getGenericReturnType()))
                .append(' ')
                .append(method.getDeclaringClass().getSimpleName())
                .append('#')
                .append(method.getName())
                .append('(');

        final Type[] parameterTypes = method.getGenericParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                result.append(',');
            }
            result.append(formatType(parameterTypes[i]));
        }
        return result.append(')').toString();
    }

    private static String formatType(Type type) {
        switch (type) {
            case Class<?> cls -> {
                if (cls.isArray()) {
                    return formatType(Objects.requireNonNull(cls.getComponentType())) + "[]";
                }
                final String simpleName = cls.getSimpleName();
                return simpleName.isEmpty() ? cls.getName() : simpleName;
            }
            case ParameterizedType parameterizedType -> {
                final StringBuilder result = new StringBuilder(formatType(parameterizedType.getRawType())).append('<');
                final Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                for (int i = 0; i < actualTypeArguments.length; i++) {
                    if (i > 0) {
                        result.append(',');
                    }
                    result.append(formatType(actualTypeArguments[i]));
                }
                return result.append('>').toString();
            }
            case GenericArrayType genericArrayType -> {
                return formatType(genericArrayType.getGenericComponentType()) + "[]";
            }
            case WildcardType wildcardType -> {
                final Type[] lowerBounds = wildcardType.getLowerBounds();
                if (lowerBounds.length > 0) {
                    return "? super " + formatType(lowerBounds[0]);
                }

                final Type[] upperBounds = wildcardType.getUpperBounds();
                if (upperBounds.length == 0 || upperBounds[0].equals(Object.class)) {
                    return "?";
                }
                return "? extends " + formatType(upperBounds[0]);
            }
            case TypeVariable<?> typeVariable -> {
                return typeVariable.getName();
            }
            default -> {
            }
        }
        return type.getTypeName();
    }

    public static List<XtreamField> findXtreamFieldAnnotations(Field field) {
        final MergedAnnotations mergedAnnotations = MergedAnnotations.from(field);
        final List<XtreamField> result = new ArrayList<>();
        for (final MergedAnnotation<Annotation> mergedAnnotation : mergedAnnotations) {
            final Class<Annotation> type = mergedAnnotation.getType();
            if (type.equals(XtreamField.class)) {
                final Annotation annotation = mergedAnnotation.synthesize();
                final XtreamField xtreamFieldAnnotation = (XtreamField) annotation;
                result.add(xtreamFieldAnnotation);
            }
        }
        return result;
    }

    public static <T extends Annotation> T findMergedAnnotationAndSynthesize(AnnotatedElement annotatedElement, Class<T> annotationClass, Supplier<T> callback) {
        final MergedAnnotations mergedAnnotations = MergedAnnotations.from(annotatedElement);
        final MergedAnnotation<T> mergedAnnotation = mergedAnnotations.get(annotationClass);
        if (mergedAnnotation.isPresent()) {
            return mergedAnnotation.synthesize();
        }

        return callback.get();
    }

    /**
     * 在指定类及其父类中查找带有指定注解的构造函数，
     * 并返回在目标类（clazz）中“参数类型相同”的构造函数。
     *
     * @return 对应的子类构造函数，若无匹配则返回 null
     */
    public static @Nullable Constructor<?> findCorrespondingConstructor(
            Class<?> clazz,
            Class<? extends Annotation> annotationType) {

        Class<?> targetClass = clazz;

        // 从当前类向上查找，找到第一个带有注解的构造函数
        while (targetClass != null && !targetClass.equals(Object.class)) {
            for (Constructor<?> constructor : targetClass.getDeclaredConstructors()) {
                if (constructor.isAnnotationPresent(annotationType)) {
                    // 找到了带注解的构造函数
                    // 查找子类中参数类型相同的构造函数
                    final Class<?>[] paramTypes = constructor.getParameterTypes();
                    return findConstructorInClass(clazz, paramTypes);
                }
            }
            targetClass = targetClass.getSuperclass();
        }

        return null;
    }

    /**
     * 在指定类中查找参数类型完全匹配的构造函数
     */
    private static @Nullable Constructor<?> findConstructorInClass(Class<?> clazz, Class<?>[] paramTypes) {
        try {
            return clazz.getDeclaredConstructor(paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

}
