package org.unsynchronized;

import java.io.IOException;

/**
 * Exception that denotes that data in the stream did not conform to the constraints
 * imposed by the specification.
 */
public class ValidityException extends IOException {

    public ValidityException(String msg) {
        super(msg);
    }
}

