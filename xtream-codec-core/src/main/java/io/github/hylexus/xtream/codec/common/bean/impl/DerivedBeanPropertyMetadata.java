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

package io.github.hylexus.xtream.codec.common.bean.impl;

import io.github.hylexus.xtream.codec.common.bean.BeanPropertyMetadata;
import io.github.hylexus.xtream.codec.common.bean.FieldConditionEvaluator;
import io.github.hylexus.xtream.codec.common.bean.FieldLengthExtractor;
import io.github.hylexus.xtream.codec.common.bean.IterationTimesExtractor;
import io.github.hylexus.xtream.codec.core.BeanMetadataRegistry;
import io.github.hylexus.xtream.codec.core.ContainerInstanceFactory;
import io.github.hylexus.xtream.codec.core.FieldCodec;
import io.github.hylexus.xtream.codec.core.FieldTransformer;
import io.github.hylexus.xtream.codec.core.annotation.DerivedField;
import io.github.hylexus.xtream.codec.core.annotation.XtreamField;
import io.github.hylexus.xtream.codec.core.utils.BeanUtils;
import io.github.hylexus.xtream.codec.core.utils.XtreamFieldUtils;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * {@link BeanPropertyMetadata} 的派生字段实现。
 * <p>
 * 该实现用于 {@link io.github.hylexus.xtream.codec.core.annotation.DerivedField @DerivedField} 注解标注的字段，
 * 编解码时跳过 ByteBuf 的直接读写，通过 {@link FieldTransformer} 从数据源字段计算得出。
 *
 * @author hylexus
 * @author opencode (AI)
 * @since 0.6.0
 */
public final class DerivedBeanPropertyMetadata implements BeanPropertyMetadata {

    private final BeanMetadataRegistry beanMetadataRegistry;
    private final String name;
    private final Class<?> type;
    private final int version;
    private final Field field;
    private final PropertyGetter propertyGetter;
    private final PropertySetter propertySetter;
    private final boolean reverseSource;
    private final String derivedSource;
    private final FieldTransformer<?, ?> derivedTransformer;
    private final XtreamField xtreamFieldPlaceholder;

    public DerivedBeanPropertyMetadata(
            BeanMetadataRegistry registry,
            String name,
            Class<?> type,
            int version,
            DerivedField annotation,
            Field field,
            PropertyGetter getter,
            PropertySetter setter) {

        this.beanMetadataRegistry = registry;
        this.name = name;
        this.type = type;
        this.version = version;
        this.field = field;
        this.propertyGetter = getter;
        this.propertySetter = setter;
        this.reverseSource = annotation.reverseSource();
        this.derivedSource = annotation.source();
        this.derivedTransformer = BeanUtils.createNewInstance(annotation.using());
        this.xtreamFieldPlaceholder = XtreamFieldUtils.createTransientFieldProxy(field);
    }

    @Override
    public boolean isDerived() {
        return true;
    }

    @Override
    public boolean reverseSource() {
        return this.reverseSource;
    }

    @Override
    public String derivedSource() {
        return this.derivedSource;
    }

    @Override
    public FieldTransformer<?, ?> derivedTransformer() {
        return this.derivedTransformer;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public Class<?> rawClass() {
        return this.type;
    }

    @Override
    public int version() {
        return this.version;
    }

    @Override
    public FiledDataType dataType() {
        return FiledDataType.unknown;
    }

    @Override
    public Field field() {
        return this.field;
    }

    @Override
    public XtreamField xtreamFieldAnnotation() {
        return this.xtreamFieldPlaceholder;
    }

    @Override
    public ContainerInstanceFactory containerInstanceFactory() {
        return ContainerInstanceFactory.PLACEHOLDER;
    }

    @Override
    public PropertyGetter propertyGetter() {
        return this.propertyGetter;
    }

    @Override
    public PropertySetter propertySetter() {
        return this.propertySetter;
    }

    @Override
    public FieldCodec<?> fieldCodec() {
        return FieldCodec.NullFieldCodec.INSTANCE;
    }

    @Override
    public FieldLengthExtractor fieldLengthExtractor() {
        return new FieldLengthExtractor.PlaceholderFieldLengthExtractor("derived field has no field length");
    }

    @Override
    public FieldConditionEvaluator conditionEvaluator() {
        return FieldConditionEvaluator.AlwaysTrueFieldConditionEvaluator.INSTANCE;
    }

    @Override
    public IterationTimesExtractor iterationTimesExtractor() {
        return IterationTimesExtractor.PlaceholderIterationTimesExtractor.DEFAULT;
    }

    @Override
    public int order() {
        return XtreamField.DEFAULT_ORDER;
    }

    @Override
    public <A extends Annotation> Optional<A> findAnnotation(Class<A> annotationClass) {
        final A mergedAnnotation = AnnotatedElementUtils.getMergedAnnotation(this.field(), annotationClass);
        return Optional.ofNullable(mergedAnnotation);
    }

    @Override
    public @Nullable Object decodePropertyValue(FieldCodec.DeserializeContext context, ByteBuf input) {
        throw new UnsupportedOperationException("derived field cannot be decoded directly");
    }

    @Override
    public void encodePropertyValue(FieldCodec.SerializeContext context, ByteBuf output, @Nullable Object value) {
        throw new UnsupportedOperationException("derived field cannot be encoded directly");
    }

    @Override
    public BeanMetadataRegistry beanMetadataRegistry() {
        return this.beanMetadataRegistry;
    }

    @Override
    public boolean isRecordClass() {
        return this.type.isRecord();
    }

    @Override
    public boolean isRecordComponent() {
        return this.field.getDeclaringClass().isRecord();
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", DerivedBeanPropertyMetadata.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("type=" + type)
                .add("version=" + version)
                .add("field=" + field)
                .add("propertyGetter=" + propertyGetter)
                .add("propertySetter=" + propertySetter)
                .add("reverseSource=" + reverseSource)
                .add("derivedSource='" + derivedSource + "'")
                .add("derivedTransformer=" + derivedTransformer)
                .add("xtreamFieldPlaceholder=" + xtreamFieldPlaceholder)
                .toString();
    }

}
