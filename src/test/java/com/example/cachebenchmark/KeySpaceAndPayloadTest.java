package com.example.cachebenchmark;

import com.example.cachebenchmark.key.KeySpace;
import com.example.cachebenchmark.workload.PayloadFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fairness Rules 2 and 4: both backends must see the same key pattern and
 * byte-identical values.
 */
class KeySpaceAndPayloadTest {

    @Test
    void keysFollowTheAgreedPattern() {
        KeySpace keySpace = KeySpace.create(1000, true);
        assertEquals("benchmark:0", keySpace.key(0));
        assertEquals("benchmark:1", keySpace.key(1));
        assertEquals("benchmark:999", keySpace.key(999));
    }

    @Test
    void precomputedAndOnTheFlyKeysAgree() {
        KeySpace precomputed = KeySpace.create(1000, true);
        KeySpace onTheFly = KeySpace.create(1000, false);

        assertTrue(precomputed.isPrecomputed());
        assertFalse(onTheFly.isPrecomputed());
        assertNotNull(precomputed.precomputed());
        assertNull(onTheFly.precomputed());

        for (int i = 0; i < 1000; i++) {
            assertEquals(precomputed.key(i), onTheFly.key(i));
        }
    }

    @Test
    void rejectsAnEmptyKeyspace() {
        assertThrows(IllegalArgumentException.class, () -> KeySpace.create(0, true));
    }

    @Test
    void payloadHasTheConfiguredSize() {
        assertEquals(100, PayloadFactory.create(100).length);
        assertEquals(1024, PayloadFactory.create(1024).length);
        assertEquals(10240, PayloadFactory.create(10240).length);
    }

    @Test
    void payloadIsDeterministic() {
        // The same configuration must produce byte-identical values, otherwise
        // Redis and Hazelcast are not storing the same thing.
        assertArrayEquals(PayloadFactory.create(1024), PayloadFactory.create(1024));
    }

    @Test
    void payloadContainsNoZeroBytes() {
        // A run of zeros would be trivially compressible and could be treated
        // differently by a backend that compresses. Keeping the pattern in
        // 1..251 avoids that asymmetry.
        byte[] payload = PayloadFactory.create(4096);
        for (int i = 0; i < payload.length; i++) {
            assertTrue(payload[i] != 0, "zero byte at offset " + i);
        }
    }

    @Test
    void rejectsAnEmptyPayload() {
        assertThrows(IllegalArgumentException.class, () -> PayloadFactory.create(0));
    }
}
