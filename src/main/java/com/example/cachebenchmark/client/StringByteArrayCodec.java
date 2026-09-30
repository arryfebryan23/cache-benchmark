package com.example.cachebenchmark.client;

import io.lettuce.core.codec.RedisCodec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Lettuce codec for String keys and raw byte[] values.
 *
 * <p>Fairness Rule 11: the value must travel as an opaque byte array on both
 * backends. No JSON, no Java serialization, no POJO mapping (PRD section 15).
 * Hazelcast stores byte[] through its built-in byte array serializer, so this
 * codec gives Redis the same treatment.
 */
public final class StringByteArrayCodec implements RedisCodec<String, byte[]> {

    @Override
    public String decodeKey(ByteBuffer bytes) {
        return StandardCharsets.UTF_8.decode(bytes).toString();
    }

    @Override
    public byte[] decodeValue(ByteBuffer bytes) {
        byte[] value = new byte[bytes.remaining()];
        bytes.get(value);
        return value;
    }

    @Override
    public ByteBuffer encodeKey(String key) {
        return StandardCharsets.UTF_8.encode(key);
    }

    @Override
    public ByteBuffer encodeValue(byte[] value) {
        return ByteBuffer.wrap(value);
    }
}
