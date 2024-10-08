package org.unsynchronized;

/**
 * <p>
 * This object contains embedded information about a serialization that failed, throwing
 * an exception.  It includes the actual exception object (which was serialized by the
 * ObjectOutputStream) and the raw bytes of the stream data that was read before the
 * exception was recognized.
 * </p>
 *
 * <p>
 * For the mechanics of exception serialization, see the Object Serialization
 * Specification.
 * </p>
 */
public class ExceptionState extends ContentBase {

    /**
     * The serialized exception object.
     */
    Content exceptionObj;

    /**
     * <p>
     * An array of bytes representing the data read before the exception was encountered.
     * Generally, this starts with the first "tc" byte (cf. protocol spec), which is an
     * ObjectStreamConstants value and ends with 0x78 (the tc byte corresponding to
     * TC_EXCEPTION).  However, this isn't guaranteed; it may include *more* data.
     * </p>
     *
     * <p>
     * In other words, this is the incomplete object that was being written while the
     * exception was caught by the ObjectOutputStream.  It is not likely to be cleanly
     * parseable.
     * </p>
     *
     * <p>
     * The uncertainty centers around the fact that this data is gathered by jdeserialize
     * using a LoggerInputStream, and the underlying DataInputStream may have read more
     * than is necessary.  In all tests conducted so far, the above description is
     * accurate.
     * </p>
     */
    byte[] streamData;

    /**
     * Constructor.
     *
     * @param exObj the serialized exception object
     * @param data  the array of stream bytes that led up to the exception
     */
    public ExceptionState(Content exObj, byte[] data) {
        super(ContentType.EXCEPTION_STATE);
        this.exceptionObj = exObj;
        this.streamData = data;
        this.handle = exObj.getHandle();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[exceptionstate object " + exceptionObj.toString() + "  buflen " + streamData.length);
        if (streamData.length > 0) {
            for (int i = 0; i < streamData.length; i++) {
                if ((i % 16) == 0) {
                    sb.append(JDeserialize.LINE_SEP).append(String.format("%7x: ", i));
                }
                sb.append(" ").append(JDeserialize.hexNoPrefix(streamData[i]));
            }
            sb.append(JDeserialize.LINE_SEP);
        }
        sb.append("]");
        return sb.toString();
    }
}
