package org.unsynchronized;

/**
 * Represents an opaque block of data written to the stream.  Primarily, these are used to
 * write class and object annotations by ObjectOutputStream overrides; they can also occur
 * inside an object, when the object overrides Serializable.writeObject().  Their
 * interpretation is hereby left to users.
 */
public class BlockData extends ContentBase {

    /**
     * The block data read from the stream.
     */
    byte[] buf;

    /**
     * Constructor.
     *
     * @param buf the block data
     */
    BlockData(byte[] buf) {
        super(ContentType.BLOCK_DATA);
        this.buf = buf;
    }

    @Override
    public String toString() {
        return String.format("[blockdata %s: %d bytes]", JDeserialize.hex(handle), buf.length);
    }
}
