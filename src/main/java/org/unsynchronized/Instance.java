package org.unsynchronized;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an instance of a non-enum, non-Class, non-ObjectStreamClass,
 * non-array class, including the non-transient field values, for all classes in its
 * hierarchy and inner classes.
 */
public class Instance extends ContentBase {

    /**
     * Collection of field data, organized by class description.
     */
    Map<ClassDesc, Map<Field, Object>> fieldData;

    /**
     * Class description for this instance.
     */
    ClassDesc classDesc;

    /**
     * Object annotation data.
     */
    Map<ClassDesc, List<Content>> annotations;

    /**
     * Constructor.
     */
    public Instance() {
        super(ContentType.INSTANCE);
        this.fieldData = new HashMap<>();
    }

    @Override
    public String toString() {
        return String.format("%s _h%s = r_%s;  ",
                classDesc.name, JDeserialize.hex(handle), JDeserialize.hex(classDesc.handle)
        );
    }
}
