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

package io.github.hylexus.xtream.codec.core.impl;

import io.github.hylexus.xtream.codec.base.expression.SpelXtreamExpressionEngine;
import io.github.hylexus.xtream.codec.common.bean.*;
import io.github.hylexus.xtream.codec.common.bean.impl.*;
import io.github.hylexus.xtream.codec.core.*;
import io.github.hylexus.xtream.codec.core.annotation.DerivedField;
import io.github.hylexus.xtream.codec.core.annotation.XtreamEntity;
import io.github.hylexus.xtream.codec.core.annotation.XtreamField;
import io.github.hylexus.xtream.codec.core.annotation.map.XtreamMapField;
import io.github.hylexus.xtream.codec.core.utils.BeanUtils;
import io.github.hylexus.xtream.codec.core.utils.ReflectionUtils;
import io.github.hylexus.xtream.codec.core.utils.XtreamFieldUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.MergedAnnotations;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author hylexus
 * @author opencode (AI)
 */
public class SimpleBeanMetadataRegistry implements BeanMetadataRegistry {
    private static final Logger log = LoggerFactory.getLogger(SimpleBeanMetadataRegistry.class);
    // <class, <version, metadata>>
    protected final ConcurrentMap<Class<?>, ConcurrentMap<Integer, BeanMetadata>> multiVersionCache = new ConcurrentHashMap<>();
    protected final FieldCodecRegistry fieldCodecRegistry;
    protected final XtreamCacheableClassPredicate cacheableClassPredicate;
    protected final XtreamExpressionFactory xtreamExpressionFactory;

    public SimpleBeanMetadataRegistry(FieldCodecRegistry fieldCodecRegistry, XtreamCacheableClassPredicate cacheableClassPredicate) {
        this(fieldCodecRegistry, cacheableClassPredicate, new DefaultXtreamExpressionFactory(new SpelXtreamExpressionEngine()));
    }

    public SimpleBeanMetadataRegistry(FieldCodecRegistry fieldCodecRegistry, XtreamCacheableClassPredicate cacheableClassPredicate, XtreamExpressionFactory xtreamExpressionFactory) {
        this.fieldCodecRegistry = fieldCodecRegistry;
        this.cacheableClassPredicate = cacheableClassPredicate;
        this.xtreamExpressionFactory = xtreamExpressionFactory;
    }

    @Override
    public FieldCodecRegistry getFieldCodecRegistry() {
        return fieldCodecRegistry;
    }

    @Override
    public XtreamExpressionFactory expressionFactory() {
        return this.xtreamExpressionFactory;
    }

    @Override
    public Stream<BeanDescriptor> beanDescriptors() {
        return this.multiVersionCache.values()
                .stream()
                .flatMap(it -> {
                    // ...
                    return it.values().stream().map(BeanDescriptor::of);
                })
                .sorted(Comparator.comparing(BeanDescriptor::rawClass));
    }

    @Override
    public BeanMetadata getBeanMetadata(Class<?> beanClass, int version, Function<PropertyInfo, BeanPropertyMetadata> creator) {
        if (!this.cacheableClassPredicate.test(beanClass)) {
            return this.doGetMetadata(beanClass, version, creator);
        }

        Map<Integer, BeanMetadata> metadataVersionMapping = multiVersionCache.get(beanClass);
        if (metadataVersionMapping != null) {
            final BeanMetadata beanMetadata = metadataVersionMapping.get(version);
            if (beanMetadata != null) {
                return beanMetadata;
            }
        }

        synchronized (SimpleBeanMetadataRegistry.class) {
            if ((metadataVersionMapping = multiVersionCache.get(beanClass)) != null) {
                final BeanMetadata beanMetadata = metadataVersionMapping.get(version);
                if (beanMetadata != null) {
                    return beanMetadata;
                }
            }
            final BeanMetadata beanMetadata = this.doGetMetadata(beanClass, version, creator);
            final Map<Integer, BeanMetadata> versionMappings = multiVersionCache.computeIfAbsent(beanClass, k -> new ConcurrentHashMap<>());
            versionMappings.put(version, beanMetadata);
            return beanMetadata;
        }

    }

    @Override
    public BeanMetadata getBeanMetadata(Class<?> beanClass, int version) {
        return this.getBeanMetadata(beanClass, version, this::createBeanPropertyMetadata);
    }

    public BasicBeanPropertyMetadata createBeanPropertyMetadata(PropertyInfo pi) {
        final BeanUtils.BasicPropertyDescriptor basicPropertyDescriptor = (BeanUtils.BasicPropertyDescriptor) pi.propertyDescriptor();

        final BeanPropertyMetadata.PropertyAccessor propertyAccessor = PropertyAccessorFactory.createPropertyAccessor(pi);
        return new BasicBeanPropertyMetadata(
                this,
                basicPropertyDescriptor.getName(),
                basicPropertyDescriptor.getPropertyType(),
                pi.version(),
                pi.xtreamField(),
                basicPropertyDescriptor.getField(),
                // pd,
                propertyAccessor.getter(),
                propertyAccessor.setter()
        );
    }

    @XtreamEntity(propertyAccessStrategy = PropertyAccessStrategy.AUTO)
    private static class Placeholder {
    }

    public BeanMetadata doGetMetadata(Class<?> beanClass, int version, Function<PropertyInfo, BeanPropertyMetadata> creator) {
        // final BeanInfo beanInfo = BeanUtils.getBeanInfo(beanClass, this.cacheableClassPredicate, field -> AnnotatedElementUtils.findMergedAnnotation(field, XtreamField.class) != null);
        final BeanUtils.XtreamSimpleBeanInfo beanInfo = BeanUtils.getBeanInfo(beanClass, this.cacheableClassPredicate, field -> MergedAnnotations.from(field).isPresent(XtreamField.class) || MergedAnnotations.from(field).isPresent(DerivedField.class));
        final XtreamEntity xtreamEntityAnnotation = ReflectionUtils.findMergedAnnotationAndSynthesize(beanClass, XtreamEntity.class, () -> Placeholder.class.getAnnotation(XtreamEntity.class));
        final PropertyAccessStrategy classStrategy = xtreamEntityAnnotation.propertyAccessStrategy();
        final ArrayList<BeanPropertyMetadata> pdList = new ArrayList<>();
        final boolean isRecordClass = beanInfo.isRecordClass();
        final Map<String, String> reverseSourceMap = new HashMap<>();

        for (final BeanUtils.BasicPropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
            final Field field = pd.getField();

            // 1) 检查 @XtreamField
            final List<XtreamField> xtreamFieldAnnotations;
            if (isRecordClass) {
                final RecordComponent recordComponent = Objects.requireNonNull(pd.getRecordComponent());
                xtreamFieldAnnotations = XtreamFieldUtils.getOrDefault(recordComponent);
            } else {
                xtreamFieldAnnotations = XtreamFieldUtils.getOrEmpty(field);
            }
            final XtreamField xtreamFieldAnnotation = matchVersion(version, xtreamFieldAnnotations);

            // 2) 检查 @DerivedField（支持 @Repeatable + 版本匹配）
            final List<DerivedField> allDerivedFields = XtreamFieldUtils.resolveDerivedFieldAnnotations(field);
            final DerivedField derivedField = allDerivedFields.isEmpty()
                    ? null
                    : XtreamFieldUtils.matchDerivedFieldVersion(version, allDerivedFields);

            if (xtreamFieldAnnotation != null) {
                // @XtreamField 优先：即使同时有 @DerivedField，也按普通字段处理
                if (derivedField != null) {
                    log.warn("Both @DerivedField and @XtreamField present on field [{}] in class [{}]; @XtreamField takes precedence, @DerivedField ignored",
                            pd.getName(), beanClass.getName());
                }
                final BeanPropertyMetadata basicPropertyMetadata = creator.apply(new PropertyInfo(xtreamEntityAnnotation, pd, xtreamFieldAnnotation, version));
                // 用户自定义 FieldCodec
                if (basicPropertyMetadata.fieldCodec() != FieldCodec.NullFieldCodec.INSTANCE) {
                    pdList.add(basicPropertyMetadata);
                } else {
                    if (basicPropertyMetadata.dataType() == BeanPropertyMetadata.FiledDataType.basic) {
                        pdList.add(basicPropertyMetadata);
                    } else if (basicPropertyMetadata.dataType() == BeanPropertyMetadata.FiledDataType.struct) {
                        final BeanMetadata nestedMetadata = doGetMetadata(pd.getPropertyType(), version, creator);
                        final NestedBeanPropertyMetadata metadata = new NestedBeanPropertyMetadata(this, nestedMetadata, basicPropertyMetadata, basicPropertyMetadata.fieldLengthExtractor());
                        pdList.add(metadata);
                    } else if (basicPropertyMetadata.dataType() == BeanPropertyMetadata.FiledDataType.sequence) {
                        final List<Class<?>> genericClass = getGenericClass(basicPropertyMetadata.field());
                        final BeanMetadata valueMetadata = doGetMetadata(genericClass.getFirst(), version, creator);
                        final NestedBeanPropertyMetadata metadata = new NestedBeanPropertyMetadata(this, valueMetadata, basicPropertyMetadata, new FieldLengthExtractor.ConstantFieldLengthExtractor(-2));
                        final SequenceBeanPropertyMetadata seqMetadata = new SequenceBeanPropertyMetadata(this, basicPropertyMetadata, metadata);
                        pdList.add(seqMetadata);
                    } else if (basicPropertyMetadata.dataType() == BeanPropertyMetadata.FiledDataType.map) {
                        pdList.add(new MapBeanPropertyMetadata(basicPropertyMetadata, fieldCodecRegistry, this));
                        throw new IllegalStateException("Deprecated MapBeanPropertyMetadata. Use " + XtreamMapField.class.getSimpleName() + " instead");
                    } else if (basicPropertyMetadata.dataType() == BeanPropertyMetadata.FiledDataType.dynamic) {
                        pdList.add(basicPropertyMetadata);
                    } else {
                        throw new IllegalStateException("Cannot determine dataType for " + basicPropertyMetadata.field());
                    }
                }
            } else if (derivedField != null) {
                // 仅有 @DerivedField，无 @XtreamField → 衍生字段
                final String fieldName = pd.getName();
                if (derivedField.reverseSource()) {
                    final String existing = reverseSourceMap.put(derivedField.source(), fieldName);
                    if (existing != null) {
                        throw new IllegalArgumentException(
                                "Multiple derived fields with reverseSource=true for the same source [" + derivedField.source()
                                + "]: [" + existing + "] and [" + fieldName + "] in class " + beanClass.getName()
                        );
                    }
                }
                final BeanPropertyMetadata.PropertyAccessor accessor = PropertyAccessorFactory.createPropertyAccessor(classStrategy, pd);
                final DerivedBeanPropertyMetadata derivedMetadata = new DerivedBeanPropertyMetadata(
                        this,
                        fieldName,
                        field.getType(),
                        version,
                        derivedField,
                        field,
                        accessor.getter(),
                        accessor.setter()
                );
                pdList.add(derivedMetadata);
            }
        }

        pdList.sort(Comparator.comparing(BeanPropertyMetadata::order));

        // 构建 derivedBySource 和 reverseDerivedBySource 索引表
        final Map<String, List<BeanPropertyMetadata>> derivedBySource = new LinkedHashMap<>();
        final Map<String, BeanPropertyMetadata> reverseDerivedBySource = new LinkedHashMap<>();
        for (final BeanPropertyMetadata pm : pdList) {
            if (!pm.isDerived()) {
                continue;
            }
            final String source = pm.derivedSource();
            if (source == null) {
                continue;
            }
            derivedBySource.computeIfAbsent(source, k -> new ArrayList<>()).add(pm);
            if (pm.reverseSource()) {
                reverseDerivedBySource.put(source, pm);
            }
        }

        return new BeanMetadata(
                beanInfo.getBeanDescriptor().getBeanClass(),
                BeanUtils.getConstructor(beanInfo),
                pdList, derivedBySource, reverseDerivedBySource);
    }

    protected @Nullable XtreamField matchVersion(int targetVersion, List<XtreamField> xtreamFieldAnnotations) {
        return XtreamFieldUtils.matchVersion(targetVersion, xtreamFieldAnnotations);
    }

    private List<Class<?>> getGenericClass(Field field) {
        final Type genericType = field.getGenericType();
        final List<Class<?>> list = new ArrayList<>();
        if (genericType instanceof ParameterizedType parameterizedType) {
            for (Type actualTypeArgument : parameterizedType.getActualTypeArguments()) {
                // ignore WildcardType
                // ?, ? extends Number, or ? super Integer
                if (actualTypeArgument instanceof WildcardType) {
                    continue;
                }
                list.add((Class<?>) actualTypeArgument);
            }
        }
        return list;
    }

}
