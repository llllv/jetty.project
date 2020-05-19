//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.websocket.javax.common.decoders;

import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;

import org.eclipse.jetty.websocket.util.InvalidSignatureException;
import org.eclipse.jetty.websocket.util.InvalidWebSocketException;
import org.eclipse.jetty.websocket.util.ReflectUtils;

public class AvailableDecoders implements Iterable<RegisteredDecoder>
{
    private final List<RegisteredDecoder> registeredDecoders = new LinkedList<>();
    private final EndpointConfig config;

    public AvailableDecoders(EndpointConfig config)
    {
        this.config = Objects.requireNonNull(config);

        // TEXT based [via Class reference]
        registerPrimitive(BooleanDecoder.class, Decoder.Text.class, Boolean.class);
        registerPrimitive(ByteDecoder.class, Decoder.Text.class, Byte.class);
        registerPrimitive(CharacterDecoder.class, Decoder.Text.class, Character.class);
        registerPrimitive(DoubleDecoder.class, Decoder.Text.class, Double.class);
        registerPrimitive(FloatDecoder.class, Decoder.Text.class, Float.class);
        registerPrimitive(ShortDecoder.class, Decoder.Text.class, Short.class);
        registerPrimitive(IntegerDecoder.class, Decoder.Text.class, Integer.class);
        registerPrimitive(LongDecoder.class, Decoder.Text.class, Long.class);
        registerPrimitive(StringDecoder.class, Decoder.Text.class, String.class);

        // TEXT based [via Primitive reference]
        registerPrimitive(BooleanDecoder.class, Decoder.Text.class, Boolean.TYPE);
        registerPrimitive(ByteDecoder.class, Decoder.Text.class, Byte.TYPE);
        registerPrimitive(CharacterDecoder.class, Decoder.Text.class, Character.TYPE);
        registerPrimitive(DoubleDecoder.class, Decoder.Text.class, Double.TYPE);
        registerPrimitive(FloatDecoder.class, Decoder.Text.class, Float.TYPE);
        registerPrimitive(ShortDecoder.class, Decoder.Text.class, Short.TYPE);
        registerPrimitive(IntegerDecoder.class, Decoder.Text.class, Integer.TYPE);
        registerPrimitive(LongDecoder.class, Decoder.Text.class, Long.TYPE);

        // BINARY based
        registerPrimitive(ByteBufferDecoder.class, Decoder.Binary.class, ByteBuffer.class);
        registerPrimitive(ByteArrayDecoder.class, Decoder.Binary.class, byte[].class);

        // STREAMING based
        registerPrimitive(ReaderDecoder.class, Decoder.TextStream.class, Reader.class);
        registerPrimitive(InputStreamDecoder.class, Decoder.BinaryStream.class, InputStream.class);

        // Config Based
        registerAll(config.getDecoders());
    }

    private void registerPrimitive(Class<? extends Decoder> decoderClass, Class<? extends Decoder> interfaceType, Class<?> type)
    {
        registeredDecoders.add(new RegisteredDecoder(decoderClass, interfaceType, type, config, true));
    }

    public void register(Class<? extends Decoder> decoder)
    {
        if (!ReflectUtils.isDefaultConstructable(decoder))
        {
            throw new InvalidSignatureException("Decoder must have public, no-args constructor: " + decoder.getName());
        }

        boolean foundDecoder = false;

        if (Decoder.Binary.class.isAssignableFrom(decoder))
        {
            add(decoder, Decoder.Binary.class);
            foundDecoder = true;
        }

        if (Decoder.BinaryStream.class.isAssignableFrom(decoder))
        {
            add(decoder, Decoder.BinaryStream.class);
            foundDecoder = true;
        }

        if (Decoder.Text.class.isAssignableFrom(decoder))
        {
            add(decoder, Decoder.Text.class);
            foundDecoder = true;
        }

        if (Decoder.TextStream.class.isAssignableFrom(decoder))
        {
            add(decoder, Decoder.TextStream.class);
            foundDecoder = true;
        }

        if (!foundDecoder)
        {
            throw new InvalidSignatureException(
                "Not a valid Decoder class: " + decoder.getName() + " implements no " + Decoder.class.getName() + " interfaces");
        }
    }

    public void registerAll(List<Class<? extends Decoder>> decoders)
    {
        if (decoders == null)
            return;
        decoders.forEach(this::register);
    }

    private void add(Class<? extends Decoder> decoder, Class<? extends Decoder> interfaceClass)
    {
        Class<?> objectType = ReflectUtils.findGenericClassFor(decoder, interfaceClass);
        if (objectType == null)
        {
            String err = "Unknown Decoder Object type declared for interface " +
                interfaceClass.getName() + " on class " + decoder;
            throw new InvalidWebSocketException(err);
        }

        boolean alreadyRegistered = registeredDecoders.stream().anyMatch(registered ->
            registered.decoder.equals(decoder) && registered.interfaceType.equals(interfaceClass));

        // If decoder is already registered for this interfaceType, don't bother adding it again.
        if (!alreadyRegistered)
            registeredDecoders.add(0, new RegisteredDecoder(decoder, interfaceClass, objectType, config));
    }

    public RegisteredDecoder getFirstRegisteredDecoder(Class<?> type)
    {
        return registeredDecoders.stream()
            .filter(registered -> registered.isType(type))
            .findFirst()
            .orElse(null);
    }

    public List<RegisteredDecoder> getRegisteredDecoders(Class<?> returnType)
    {
        return registeredDecoders.stream()
            .filter(registered -> registered.isType(returnType))
            .collect(Collectors.toList());
    }

    public List<RegisteredDecoder> getRegisteredDecoders(Class<? extends Decoder> interfaceType, Class<?> returnType)
    {
        return registeredDecoders.stream()
            .filter(registered -> registered.interfaceType.equals(interfaceType) && registered.isType(returnType))
            .collect(Collectors.toList());
    }

    public List<RegisteredDecoder> getTextDecoders(Class<?> returnType)
    {
        return getRegisteredDecoders(Decoder.Text.class, returnType);
    }

    public List<RegisteredDecoder> getBinaryDecoders(Class<?> returnType)
    {
        return getRegisteredDecoders(Decoder.Binary.class, returnType);
    }

    public List<RegisteredDecoder> getTextStreamDecoders(Class<?> returnType)
    {
        return getRegisteredDecoders(Decoder.TextStream.class, returnType);
    }

    public List<RegisteredDecoder> getBinaryStreamDecoders(Class<?> returnType)
    {
        return getRegisteredDecoders(Decoder.BinaryStream.class, returnType);
    }

    @Override
    public Iterator<RegisteredDecoder> iterator()
    {
        return registeredDecoders.iterator();
    }

    public Stream<RegisteredDecoder> stream()
    {
        return registeredDecoders.stream();
    }
}
