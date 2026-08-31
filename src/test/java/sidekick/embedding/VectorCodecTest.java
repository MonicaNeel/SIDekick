package sidekick.embedding;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VectorCodecTest {

    @Test
    void roundTripPreservesEveryBit() {
        Random random = new Random(42);
        float[] vector = new float[384];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (random.nextFloat() - 0.5f) * 2;
        }

        float[] back = VectorCodec.toFloats(VectorCodec.toBytes(vector));

        // Exact equality on purpose: storage must be lossless, not "close".
        assertArrayEquals(vector, back);
    }

    @Test
    void byteLayoutIsLittleEndianAndStable() {
        // 1.0f is 0x3F800000; little-endian on disk = 00 00 80 3F.
        // Pinned so a future refactor cannot silently change the format and
        // turn every vector already stored in Postgres into garbage.
        byte[] bytes = VectorCodec.toBytes(new float[]{1.0f});

        assertArrayEquals(new byte[]{0x00, 0x00, (byte) 0x80, 0x3F}, bytes);
    }

    @Test
    void vectorOf384FloatsIs1536Bytes() {
        assertEquals(1536, VectorCodec.toBytes(new float[384]).length);
    }

    @Test
    void rejectsByteArraysThatAreNotWholeFloats() {
        assertThrows(IllegalArgumentException.class, () -> VectorCodec.toFloats(new byte[10]));
    }
}
