package sidekick.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The entire "vector storage format" of this project (ADR-002): a float[]
 * becomes 4 bytes per number, little-endian, no header — and back. Postgres
 * stores the bytes in a bytea column and never looks inside them; only this
 * codec knows what they mean.
 *
 * Little-endian is pinned by test, not convention: if this codec ever changes,
 * every stored vector becomes silent garbage, so the byte layout is a contract.
 */
public final class VectorCodec {

    private VectorCodec() {
    }

    public static byte[] toBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    public static float[] toFloats(byte[] bytes) {
        if (bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException(
                    "byte length " + bytes.length + " is not a multiple of " + Float.BYTES);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }
}
