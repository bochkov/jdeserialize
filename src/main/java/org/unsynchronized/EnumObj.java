package org.unsynchronized;

/**
 * <p>
 * Represents an enum instance.  As noted in the serialization spec, this consists of
 * merely the class description (represented by a classdesc) and the string corresponding
 * to the enum's value.  No other fields are ever serialized.
 * </p>
 */
public class EnumObj extends ContentBase {

    /**
     * The enum's class description.
     */
    ClassDesc classdesc;

    /**
     * The string that represents the enum's value.
     */
    StringObj value;

    /**
     * Constructor.
     *
     * @param handle the enum's handle
     * @param cd     the enum's class description
     * @param so     the enum's value
     */
    public EnumObj(int handle, ClassDesc cd, StringObj so) {
        super(ContentType.ENUM);
        this.handle = handle;
        this.classdesc = cd;
        this.value = so;
    }

    @Override
    public String toString() {
        return String.format("[enum %s: %s]", JDeserialize.hex(handle), value.value);
    }
}
