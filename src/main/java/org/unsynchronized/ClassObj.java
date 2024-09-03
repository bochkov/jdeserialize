package org.unsynchronized;

/**
 * This represents a Class object (i.e. an instance of type Class) serialized in the
 * stream.
 */
public class ClassObj extends ContentBase {

    /**
     * The class description, including its name.
     */
    ClassDesc classdesc;

    /**
     * Constructor.
     *
     * @param handle the instance's handle
     * @param cd     the instance's class description
     */
    ClassObj(int handle, ClassDesc cd) {
        super(ContentType.CLASS);
        this.handle = handle;
        this.classdesc = cd;
    }

    @Override
    public String toString() {
        return String.format("[class %s: %s]", JDeserialize.hex(handle), classdesc.toString());
    }
}

