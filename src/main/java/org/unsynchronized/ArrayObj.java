package org.unsynchronized;

/**
 * <p>Represents an array instance, including the values the comprise the array.  </p>
 *
 * <p>Note that in arrays of primitives, the classdesc will be named "[x", where x is the
 * field type code representing the primitive type.  See jdeserialize.resolveJavaType()
 * for an example of analysis/generation of human-readable names from these class names.</p>
 */
public class ArrayObj extends ContentBase {

    /**
     * Type of the array instance.
     */
    ClassDesc classDesc;

    /**
     * Values of the array, in the order they were read from the stream.
     */
    ArrayColl data;

    ArrayObj(int handle, ClassDesc cd, ArrayColl data) {
        super(ContentType.ARRAY);
        this.handle = handle;
        this.classDesc = cd;
        this.data = data;
    }

    @Override
    public String toString() {
        return String.format("[array %s classdesc %s: %s]",
                JDeserialize.hex(handle), classDesc.toString(), data.toString());
    }
}

