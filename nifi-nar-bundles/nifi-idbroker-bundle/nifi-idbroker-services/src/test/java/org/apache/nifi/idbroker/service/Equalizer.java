/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.idbroker.service;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;

// TODO Move to a generic util bundle
public class Equalizer<T> {
    private final T item;
    private final List<Function<T, Object>> propertyProvider;

    public Equalizer(T item, List<Function<T, Object>> propertyProvider) {
        this.item = item;
        this.propertyProvider = propertyProvider;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null
            || getClass() != o.getClass()
            || !Arrays.equals(o.getClass().getGenericInterfaces(), o.getClass().getGenericInterfaces())
        ) {
            return false;
        }

        final Equalizer<T> that = (Equalizer<T>) o;
        final EqualsBuilder equalsBuilder = new EqualsBuilder();

        propertyProvider.forEach(propertySupplier -> equalsBuilder.append(propertySupplier.apply(item), propertySupplier.apply(that.item)));

        return equalsBuilder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder hashCodeBuilder = new HashCodeBuilder(17, 37);

        propertyProvider.forEach(propertySupplier -> hashCodeBuilder.append(propertySupplier.apply(item)));

        return hashCodeBuilder.toHashCode();
    }

    @Override
    public String toString() {
        final StringJoiner stringJoiner = new StringJoiner(",\n\t", "{\n\t", "\n}");

        propertyProvider.forEach(propertySupplier -> stringJoiner.add(Optional.ofNullable(propertySupplier.apply(item)).orElse("N/A").toString()));

        return stringJoiner.toString();
    }
}
