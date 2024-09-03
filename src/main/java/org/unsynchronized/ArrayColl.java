package org.unsynchronized;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * <p>Typed collection used for storing the values of a serialized array.  </p>
 *
 * <p>Primitive types are stored using their corresponding objects; for instance, an int is
 * stored as an Integer.  To determine whether or not this is an array of ints or of
 * Integer instances, check the name in the arrayobj's class description.</p>
 */
public class ArrayColl extends ArrayList<Object> {

    /**
     * The field type of the array.
     */
    FieldType fType;

    /**
     * Constructor.
     *
     * @param ft field type of the array
     */
    ArrayColl(FieldType ft) {
        super();
        this.fType = ft;
    }

    @Override
    public String toString() {
        return String.format("[arraycoll sz %d%s",
                this.size(),
                this.stream().map(Object::toString).collect(Collectors.joining(", "))
        );
    }
}
