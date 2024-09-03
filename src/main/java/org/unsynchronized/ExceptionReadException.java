package org.unsynchronized;

import java.io.IOException;

/**
 * Exception used to signal that an exception object was successfully read from the
 * stream.  This object holds a reference to the serialized exception object.
 */
public class ExceptionReadException extends IOException {

    private final Content exceptionObj;

    /**
     * Constructor.
     *
     * @param c the serialized exception object that was read
     */
    public ExceptionReadException(Content c) {
        super("serialized exception read during stream");
        this.exceptionObj = c;
    }

    /**
     * Gets the Exception object that was thrown.
     *
     * @return the content representing the serialized exception object
     */
    public Content getExceptionObject() {
        return exceptionObj;
    }
}

