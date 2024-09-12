package org.unsynchronized;

import java.io.ObjectStreamConstants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * Represents the entire serialized prototype of the class, including all fields,
 * inner classes, class annotations, and inheritance hierarchy.  This includes proxy class
 * descriptions.
 * </p>
 *
 * <p>
 * Generally, this class is used to represent the type of an instance written to an
 * ObjectOutputStream with its writeObject() method, or of a related array or field type.
 * However, there's a notable exception: when instances of type java.io.ObjectStreamClass
 * are written with writeObject(), only their class description is written (cf. Object
 * Serialization Specification, 4.3).  They will be represented with an instance of
 * classdesc as well.
 * </p>
 */
public class ClassDesc extends ContentBase {

    /**
     * Type of the class being represented; either a normal class or a proxy class.
     */
    ClassDescType classType;

    /**
     * Class name.
     */
    String name;

    /**
     * Serial version UID, as recorded in the stream.
     */
    long serialVersionUID;

    /**
     * Description flags byte; this should be a mask of values from the ObjectStreamContants
     * class.  Refer to chapter 6 of the Object Stream Serialization Protocol for details.
     */
    byte descFlags;

    /**
     * Array of fields in the class, in the order serialized by the stream writer.
     */
    Field[] fields;

    /**
     * List of inner classes, in the order serialized by the stream writer.
     */
    List<ClassDesc> innerClasses;

    /**
     * List of annotation objects; these are *not* Java annotations, but data written by
     * the <pre>annotateClass(Class<?>)<pre> and <pre>annotateProxyClass(Class<?>)</pre> methods of an
     * ObjectOutputStream.
     */
    List<Content> annotations;

    /**
     * The superclass of the object, if available.
     */
    ClassDesc superclass;

    /**
     * Array of serialized interfaces, in the order serialized by the stream writer.
     */
    String[] interfaces;

    /**
     * Set of enum constants, for enum classes.
     */
    Set<String> enumConstants;

    private boolean isInnerClass = false;

    private boolean isLocalInnerClass = false;

    private boolean isStaticMemberClass = false;

    /**
     * Constructor.
     *
     * @param classType the type of the class
     */
    ClassDesc(ClassDescType classType) {
        super(ContentType.CLASSDESC);
        this.classType = classType;
        this.enumConstants = new HashSet<>();
        this.innerClasses = new ArrayList<>();
    }

    /**
     * Add an inner class to the description's list.
     *
     * @param cd inner class to add
     */
    public void addInnerClass(ClassDesc cd) {
        innerClasses.add(cd);
    }

    /**
     * Add an enum constant to the description's set.
     *
     * @param constVal enum constant string
     */
    public void addEnum(String constVal) {
        this.enumConstants.add(constVal);
    }

    /**
     * Determines whether this is an array type.
     *
     * @return true if this is an array type.
     */
    public boolean isArrayClass() {
        return name != null && name.length() > 1 && name.charAt(0) == '[';
    }

    /**
     * True if this class has been determined to be an inner class; this determination is
     * generally made by connectMemberClasses().
     *
     * @return true if the class is an inner class
     */
    public boolean isInnerClass() {
        return isInnerClass;
    }

    /**
     * Sets the value that denotes that the class is an inner class.
     *
     * @param nis the value to set
     */
    public void setIsInnerClass(boolean nis) {
        this.isInnerClass = nis;
    }


    /**
     * True if this class has been determined to be a local inner class; this
     * determination is generally made by connectMemberClasses().
     *
     * @return true if the class is a local inner class
     */
    public boolean isLocalInnerClass() {
        return isLocalInnerClass;
    }

    /**
     * Sets the flag that denotes whether this class is a local inner class.
     *
     * @param nis the value to set
     */
    public void setIsLocalInnerClass(boolean nis) {
        this.isLocalInnerClass = nis;
    }

    /**
     * <p>
     * True if this class has been determined to be a static member class; this
     * determination is generally made by connectMemberClasses().
     * </p>
     *
     * <p>
     * Note that in some cases, static member classes' descriptions will be serialized
     * even though their enclosing class is not.  In these cases, this may return false.
     * See connectMemberClasses() for details.
     * </p>
     *
     * @return true if this is a static member class
     */
    public boolean isStaticMemberClass() {
        return isStaticMemberClass;
    }

    /**
     * Sets the flag that denotes whether this class is a static member class.
     *
     * @param nis the value to set
     */
    public void setIsStaticMemberClass(boolean nis) {
        this.isStaticMemberClass = nis;
    }

    @Override
    public String toString() {
        return String.format("[cd %s: name %s uid %d]",
                JDeserialize.hex(handle), name, serialVersionUID);
    }

    /**
     * Generates a list of all class descriptions in this class's hierarchy, in the order
     * described by the Object Stream Serialization Protocol.  This is the order in which
     * fields are read from the stream.
     *
     * @param classes a list to be filled in with the hierarchy
     */
    public void getHierarchy(List<ClassDesc> classes) {
        if (superclass != null && superclass.classType != ClassDescType.PROXY_CLASS) {
            superclass.getHierarchy(classes);
        }
        classes.add(this);
    }

    @Override
    public void validate() throws ValidityException {
        // If neither SC_SERIALIZABLE nor SC_EXTERNALIZABLE is set, then the number of
        // fields is always zero.  (spec section 4.3)
        if ((descFlags & (ObjectStreamConstants.SC_SERIALIZABLE | ObjectStreamConstants.SC_EXTERNALIZABLE)) == 0 && fields != null && fields.length > 0) {
            throw new ValidityException("non-serializable, non-externalizable class has fields!");
        }
        if ((descFlags & (ObjectStreamConstants.SC_SERIALIZABLE | ObjectStreamConstants.SC_EXTERNALIZABLE)) == (ObjectStreamConstants.SC_SERIALIZABLE | ObjectStreamConstants.SC_EXTERNALIZABLE)) {
            throw new ValidityException("both Serializable and Externalizable are set!");
        }
        if ((descFlags & ObjectStreamConstants.SC_ENUM) != 0) {
            // we're an enum; shouldn't have any fields/superinterfaces
            if ((fields != null && fields.length > 0) || interfaces != null) {
                throw new ValidityException("enums shouldn't implement interfaces or have non-constant fields!");
            }
        } else {
            // non-enums shouldn't have enum constant fields.  
            if (enumConstants != null && !enumConstants.isEmpty()) {
                throw new ValidityException("non-enum classes shouldn't have enum constants!");
            }
        }
    }

}

