package org.unsynchronized;

/**
 * <p>
 * Enum class that describes the type of a field encoded inside a classdesc description.
 * </p>
 *
 * <p>
 * This stores both information on the type (reference/array vs. primitive) and, in cases
 * of reference or array types, the name of the class being referred to.
 * </p>
 */
public enum FieldType {

    BYTE('B', "byte"),
    CHAR('C', "char"),
    DOUBLE('D', "double"),
    FLOAT('F', "float"),
    INTEGER('I', "int"),
    LONG('J', "long"),
    SHORT('S', "String"),
    BOOLEAN('Z', "boolean"),
    ARRAY('['),
    OBJECT('L');

    private final char ch;
    private final String javaType;

    /**
     * Constructor for non-object (primitive) types.
     *
     * @param ch the character representing the type (must match one of those listed in
     *           prim_typecode or obj_typecode in the Object Serialization Stream Protocol)
     */
    FieldType(char ch) {
        this(ch, null);
    }

    /**
     * Constructor.
     *
     * @param ch       the character representing the type (must match one of those listed in
     *                 prim_typecode or obj_typecode in the Object Serialization Stream Protocol)
     * @param javaType the name of the object class, where applicable (or null if not)
     */
    FieldType(char ch, String javaType) {
        this.ch = ch;
        this.javaType = javaType;
    }

    /**
     * Gets the class name for a reference or array type.
     *
     * @return the name of the class being referred to, or null if this is not a
     * reference/array type
     */
    public String getJavaType() {
        return this.javaType;
    }

    /**
     * Given a byte containing a type code, return the corresponding enum.
     *
     * @param b the type code; must be one of the charcaters in obj_typecode or
     *          prim_typecode in the protocol spec
     * @return the corresponding fieldtype enum
     * @throws ValidityException if the type code is invalid
     */
    public static FieldType get(byte b) throws ValidityException {
        for (FieldType type : values()) {
            if (type.ch == b)
                return type;
        }
        throw new ValidityException("invalid field type char: " + b);
    }
}

