package org.unsynchronized;

/**
 * <p>
 * Generic interface for all data that may be read from the stream (except null).  
 * </p>
 *
 * <p>
 * A successful read of the stream will result in a series of content instances or null
 * references.  For details on specific metadata, see documentation on implementing
 * classes/subinterfaces.
 * </p>
 */
public interface Content {

    /**
     * @return the type of instance represented by this object.
     */
    ContentType getType();

    /**
     * <p>
     * Get the numeric handle by which this object was referred to in the object stream.
     * These handles are used internally by Object{Output,Input}Stream as a mechanism to
     * avoid costly duplication.
     * </p>
     *
     * <p>
     * CAUTION: they are *not* necessarily unique across all objects in a given stream!
     * If an exception was thrown during serialization (which is most likely to happen
     * during a serialized objct's writeObject() implementation), then the stream resets
     * before and after the exception is serialized.  
     * </p>
     *
     * @return the handle assigned in the stream
     */
    int getHandle();

    /**
     * Performs extra object-specific validity checks.  
     *
     * @throws ValidityException if the object's state is invalid
     */
    default void validate() throws ValidityException {
        // do nothing
    }

    /**
     * <p>
     * Tells whether or not this object is an exception that was caught during
     * serialization.  
     * </p>
     *
     * <p>
     * <b>Note</b>:  Not every Throwable or Exception in the stream will have this flag set to
     * true; only those which were thrown <i>during serialization</i> will
     * </p>
     * 
     * @return true iff the object was an exception thrown during serialization
     */
    boolean isExceptionObject();

    /**
     * Sets the flag that tells whether or not this object is an exception that was caught
     * during serialization.
     *
     * @param value the new value to use
     */
    void setIsExceptionObject(boolean value);
}

