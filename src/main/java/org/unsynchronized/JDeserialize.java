package org.unsynchronized;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The main user-facing class for the jdeserialize library.
 * <br/>
 * The jdeserialize class parses the stream (method run()). From there, call the
 * getContent() method to get an itemized list of all items written to the stream,
 * or getHandleMaps() to get a list of all handle->content maps generated during parsing.
 * The objects are generally instances that implement the interface "content"; see the
 * documentation of various implementors to get more information about the inner
 * representations.<br/>
 * <br/>
 * References: <br/>
 * - Java Object Serialization Specification ch. 6 (Object Serialization StreamProtocol): <br/>
 * <a href="http://download.oracle.com/javase/6/docs/platform/serialization/spec/protocol.html">http://download.oracle.com/javase/6/docs/platform/serialization/spec/protocol.html</a> <br/>
 * - "Modified UTF-8 Strings" within the JNI specification:<br/>
 * <a href="http://download.oracle.com/javase/1.5.0/docs/guide/jni/spec/types.html#wp16542">http://download.oracle.com/javase/1.5.0/docs/guide/jni/spec/types.html#wp16542</a> <br/>
 * - "Inner Classes Specification" within the JDK 1.1.8 docs:<br/>
 * <a href="http://java.sun.com/products/archive/jdk/1.1/">http://java.sun.com/products/archive/jdk/1.1/</a> <br/>
 * - "Java Language Specification", third edition, particularly section 3:<br/>
 * <a href="http://java.sun.com/docs/books/jls/third_edition/html/j3TOC.html">http://java.sun.com/docs/books/jls/third_edition/html/j3TOC.html</a> <br/>
 *
 * @see Content
 */
@Slf4j
public class JDeserialize {

    public static final String LINE_SEP = System.getProperty("line.separator");

    private static final String INDENT_CHARS = "    ";
    private static final int CODE_WIDTH = 90;
    private static final String[] KEYWORDS = new String[]{
            "abstract", "continue", "for", "new", "switch", "assert", "default", "if",
            "package", "synchronized", "boolean", "do", "goto", "private", "this",
            "break", "double", "implements", "protected", "throw", "byte", "else",
            "import", "public", "throws", "case", "enum", "instanceof", "return",
            "transient", "catch", "extends", "int", "short", "try", "char", "final",
            "interface", "static", "void", "class", "finally", "long", "strictfp",
            "volatile", "const", "float", "native", "super", "while"};
    private static final Set<String> KEYWORD_SET = new HashSet<>();

    private final Map<Integer, Content> handles = new HashMap<>();
    private final List<Map<Integer, Content>> handleMaps = new ArrayList<>();

    private List<Content> content;
    private int curHandle;

    static {
        KEYWORD_SET.addAll(Arrays.asList(KEYWORDS));
    }

    /**
     * <p>
     * Retrieves the list of content objects that were written to the stream.  Each item
     * generally corresponds to an invocation of an ObjectOutputStream writeXXX() method.
     * A notable exception is the class ExceptionState, which represents an embedded
     * exception that was caught during serialization.
     * </p>
     *
     * <p>
     * See the various implementors of content to get information about what data is
     * available.
     * </p>
     *
     * <p>
     * Entries in the list may be null, because it's perfectly legitimate to write a null
     * reference to the stream.
     * </p>
     *
     * @return a list of content objects
     * @see Content
     * @see ExceptionState
     */
    public List<Content> getContent() {
        return content;
    }

    /**
     * <p>
     * Return a list of Maps containing every object with a handle.  The keys are integers
     * -- the handles themselves -- and the values are instances of type content.
     * </p>
     *
     * <p>
     * Although there is only one map active at a given point, a stream may have multiple
     * logical maps: when a reset happens (indicated by TC_RESET), the current map is
     * cleared.
     * </p>
     *
     * <p>
     * See the spec for details on handles.
     * </p>
     *
     * @return a list of <Integer,content> maps
     */
    public List<Map<Integer, Content>> getHandleMaps() {
        return handleMaps;
    }

    private void readClassData(DataInputStream dis, Instance inst) throws IOException {
        List<ClassDesc> classes = new ArrayList<>();
        inst.classDesc.getHierarchy(classes);
        Map<ClassDesc, Map<Field, Object>> allData = new HashMap<>();
        Map<ClassDesc, List<Content>> ann = new HashMap<>();
        for (ClassDesc cd : classes) {
            Map<Field, Object> values = new HashMap<>();
            if ((cd.descFlags & ObjectStreamConstants.SC_SERIALIZABLE) != 0) {
                if ((cd.descFlags & ObjectStreamConstants.SC_EXTERNALIZABLE) != 0) {
                    throw new IOException("SC_EXTERNALIZABLE & SC_SERIALIZABLE encountered");
                }
                for (Field f : cd.fields) {
                    Object o = readFieldValue(f.type, dis);
                    values.put(f, o);
                }
                allData.put(cd, values);
                if ((cd.descFlags & ObjectStreamConstants.SC_WRITE_METHOD) != 0) {
                    if ((cd.descFlags & ObjectStreamConstants.SC_ENUM) != 0) {
                        throw new IOException("SC_ENUM & SC_WRITE_METHOD encountered!");
                    }
                    ann.put(cd, readClassAnnotation(dis));
                }
            } else if ((cd.descFlags & ObjectStreamConstants.SC_EXTERNALIZABLE) != 0) {
                if ((cd.descFlags & ObjectStreamConstants.SC_SERIALIZABLE) != 0) {
                    throw new IOException("SC_SERIALIZABLE & SC_EXTERNALIZABLE encountered");
                }
                if ((cd.descFlags & ObjectStreamConstants.SC_BLOCK_DATA) != 0) {
                    throw new EOFException("hit externalizable with nonzero SC_BLOCK_DATA; can't interpret data");
                } else {
                    ann.put(cd, readClassAnnotation(dis));
                }
            }
        }
        inst.annotations = ann;
        inst.fieldData = allData;
    }

    private Object readFieldValue(FieldType f, DataInputStream dis) throws IOException {
        switch (f) {
            case BYTE:
                return dis.readByte();
            case CHAR:
                return dis.readChar();
            case DOUBLE:
                return dis.readDouble();
            case FLOAT:
                return dis.readFloat();
            case INTEGER:
                return dis.readInt();
            case LONG:
                return dis.readLong();
            case SHORT:
                return dis.readShort();
            case BOOLEAN:
                return dis.readBoolean();
            case OBJECT:
            case ARRAY:
                byte stc = dis.readByte();
//                if(f == FieldType.ARRAY && stc != ObjectStreamConstants.TC_ARRAY) { FIXME
//                    throw new IOException("array type listed, but typecode is not TC_ARRAY: " + hex(stc));
//                }
                Content c = readContent(stc, dis, false);
                if (c != null && c.isExceptionObject()) {
                    throw new ExceptionReadException(c);
                }
//                if (f == FieldType.ARRAY && !(c instanceof ArrayObj)) {
//                    throw new IOException("expected an array, but got something else!");
//                }
                return c;
            default:
                throw new IOException("can't process type: " + f);
        }
    }

    private int newHandle() {
        return curHandle++;
    }

    static String resolveJavaType(FieldType type, String classname, boolean convertSlashes, boolean fixName) throws IOException {
        if (type == FieldType.ARRAY) {
            StringBuilder asb = new StringBuilder();
            for (int i = 0; i < classname.length(); i++) {
                char ch = classname.charAt(i);
                switch (ch) {
                    case '[':
                        asb.append("[]");
                        continue;
                    case 'L':
                        String cn = decodeClassName(classname.substring(i), convertSlashes);
                        if (fixName) {
                            cn = fixClassName(cn);
                        }
                        return cn + asb;
                    default:
                        if (ch < 1 || ch > 127) {
                            throw new ValidityException("invalid array field type descriptor character: " + classname);
                        }
                        FieldType ft = FieldType.get((byte) ch);
                        if (i != (classname.length() - 1)) {
                            throw new ValidityException("array field type descriptor is too long: " + classname);
                        }
                        String ftn = ft.getJavaType();
                        if (fixName) {
                            ftn = fixClassName(ftn);
                        }
                        return ftn + asb;
                }
            }
            throw new ValidityException("array field type descriptor is too short: " + classname);
        } else if (type == FieldType.OBJECT) {
            return decodeClassName(classname, convertSlashes);
        } else {
            return type.getJavaType();
        }
    }

    private List<Content> readClassAnnotation(DataInputStream dis) throws IOException {
        List<Content> list = new ArrayList<>();
        while (true) {
            byte tc = dis.readByte();
            if (tc == ObjectStreamConstants.TC_ENDBLOCKDATA) {
                return list;
            }
            if (tc == ObjectStreamConstants.TC_RESET) {
                reset();
                continue;
            }
            Content c = readContent(tc, dis, true);
            if (c != null && c.isExceptionObject()) {
                throw new ExceptionReadException(c);
            }
            list.add(c);
        }
    }

    private void dumpInstance(Instance inst, PrintStream ps) {
        StringBuilder sb = new StringBuilder();
        sb.append("[instance ").append(hex(inst.handle)).append(": ").append(hex(inst.classDesc.handle)).append("/").append(inst.classDesc.name);
        if (inst.annotations != null && inst.annotations.size() > 0) {
            sb.append(LINE_SEP).append("  object annotations:").append(LINE_SEP);
            for (ClassDesc cd : inst.annotations.keySet()) {
                sb.append("    ").append(cd.name).append(LINE_SEP);
                for (Content c : inst.annotations.get(cd)) {
                    sb.append("        ").append(c.toString()).append(LINE_SEP);
                }
            }
        }
        if (inst.fieldData != null && inst.fieldData.size() > 0) {
            sb.append(LINE_SEP).append("  field data:").append(LINE_SEP);
            for (ClassDesc cd : inst.fieldData.keySet()) {
                sb.append("    ").append(hex(cd.handle)).append("/").append(cd.name).append(":").append(LINE_SEP);
                for (Field f : inst.fieldData.get(cd).keySet()) {
                    Object o = inst.fieldData.get(cd).get(f);
                    sb.append("        ").append(f.name).append(": ");
                    if (o instanceof Content) {
                        Content c = (Content) o;
                        int h = c.getHandle();
                        if (h == inst.handle) {
                            sb.append("this");
                        } else {
                            sb.append("r").append(hex(h));
                        }
                        sb.append(": ").append(c);
                        sb.append(LINE_SEP);
                    } else {
                        sb.append(o).append(LINE_SEP);
                    }
                }
            }
        }
        sb.append("]");
        ps.println(sb);
    }

    /**
     * "Fix" the given name by transforming illegal characters, such that the end result
     * is a legal Java identifier that is not a keyword.
     * If the string is modified at all, the result will be prepended with "$__".
     *
     * @param name the name to be transformed
     * @return the unmodified string if it is legal, otherwise a legal-identifier version
     */
    private static String fixClassName(String name) {
        if (name == null) {
            return "$__null";
        }
        if (KEYWORD_SET.contains(name)) {
            return "$__" + name;
        }
        StringBuilder sb = new StringBuilder();
        int cpLen = name.codePointCount(0, name.length());
        if (cpLen < 1) {
            return "$__zerolen";
        }
        boolean modified = false;
        int scp = name.codePointAt(0);
        if (!Character.isJavaIdentifierStart(scp)) {
            modified = true;
            if (!Character.isJavaIdentifierPart(scp) || Character.isIdentifierIgnorable(scp)) {
                sb.append("x");
            } else {
                sb.appendCodePoint(scp);
            }
        } else {
            sb.appendCodePoint(scp);
        }

        for (int i = 1; i < cpLen; i++) {
            int cp = name.codePointAt(i);
            if (!Character.isJavaIdentifierPart(cp) || Character.isIdentifierIgnorable(cp)) {
                modified = true;
                sb.append("x");
            } else {
                sb.appendCodePoint(cp);
            }
        }
        if (modified) {
            return "$__" + sb;
        } else {
            return name;
        }
    }

    private void dumpClassDesc(int indentLevel, ClassDesc cd, PrintStream ps, boolean fixName) throws IOException {
        String classname = cd.name;
        if (fixName) {
            classname = fixClassName(classname);
        }
        if (cd.annotations != null && !cd.annotations.isEmpty()) {
            ps.println(indent(indentLevel) + "// annotations: ");
            for (Content c : cd.annotations) {
                ps.print(indent(indentLevel) + "// " + indent(1));
                ps.println(c.toString());
            }
        }
        if (cd.classType == ClassDescType.NORMAL_CLASS) {
            if ((cd.descFlags & ObjectStreamConstants.SC_ENUM) != 0) {
                ps.print(indent(indentLevel) + "enum " + classname + " {");
                boolean shouldindent = true;
                int len = indent(indentLevel + 1).length();
                for (String econst : cd.enumConstants) {
                    if (shouldindent) {
                        ps.println();
                        ps.print(indent(indentLevel + 1));
                        shouldindent = false;
                    }
                    len += econst.length();
                    ps.print(econst + ", ");
                    if (len >= CODE_WIDTH) {
                        len = indent(indentLevel + 1).length();
                        shouldindent = true;
                    }
                }
                ps.println();
                ps.println(indent(indentLevel) + "}");
                return;
            }
            ps.print(indent(indentLevel));
            if (cd.isStaticMemberClass()) {
                ps.print("static ");
            }
            ps.print("class " + (classname.charAt(0) == '[' ? resolveJavaType(FieldType.ARRAY, cd.name, false, fixName) : classname));
            if (cd.superclass != null) {
                ps.print(" extends " + cd.superclass.name);
            }
            ps.print(" implements ");
            if ((cd.descFlags & ObjectStreamConstants.SC_EXTERNALIZABLE) != 0) {
                ps.print("java.io.Externalizable");
            } else {
                ps.print("java.io.Serializable");
            }
            if (cd.interfaces != null) {
                for (String intf : cd.interfaces) {
                    ps.print(", " + intf);
                }
            }
            ps.println(" {");
            for (Field f : cd.fields) {
                if (f.isInnerClassReference()) {
                    continue;
                }
                ps.print(indent(indentLevel + 1) + f.getJavaType());
                ps.println(" " + f.name + ";");
            }
            for (ClassDesc icd : cd.innerClasses) {
                dumpClassDesc(indentLevel + 1, icd, ps, fixName);
            }
            ps.println(indent(indentLevel) + "}");
        } else if (cd.classType == ClassDescType.PROXY_CLASS) {
            ps.print(indent(indentLevel) + "// proxy class " + hex(cd.handle));
            if (cd.superclass != null) {
                ps.print(" extends " + cd.superclass.name);
            }
            ps.println(" implements ");
            for (String intf : cd.interfaces) {
                ps.println(indent(indentLevel) + "//    " + intf + ", ");
            }
            if ((cd.descFlags & ObjectStreamConstants.SC_EXTERNALIZABLE) != 0) {
                ps.println(indent(indentLevel) + "//    java.io.Externalizable");
            } else {
                ps.println(indent(indentLevel) + "//    java.io.Serializable");
            }
        } else {
            throw new ValidityException("encountered invalid ClassDesc type!");
        }
    }

    private void setHandle(int handle, Content c) throws IOException {
        if (handles.containsKey(handle)) {
            throw new IOException("trying to reset handle " + hex(handle));
        }
        handles.put(handle, c);
    }

    private void reset() {
        LOG.trace("reset ordered!");
        if (handles.size() > 0) {
            handleMaps.add(new HashMap<>(handles));
        }
        handles.clear();
        curHandle = ObjectStreamConstants.baseWireHandle;  // 0x7e0000
    }

    /**
     * Read the content of a thrown exception object.  According to the spec, this must be
     * an object of type Throwable.  Although the Sun JDK always appears to provide enough
     * information about the hierarchy to reach all the way back to java.lang.Throwable,
     * it's unclear whether this is actually a requirement.  From my reading, it's
     * possible that some other ObjectOutputStream implementations may leave some gaps in
     * the hierarchy, forcing this app to hit the classloader.  To avoid this, we merely
     * ensure that the written object is indeed an instance; ensuring that the object is
     * indeed a Throwable is an exercise left to the user.
     */
    private Content readException(DataInputStream dis) throws IOException {
        reset();
        byte tc = dis.readByte();
        if (tc == ObjectStreamConstants.TC_RESET) {
            throw new ValidityException("TC_RESET for object while reading exception: what should we do?");
        }
        Content c = readContent(tc, dis, false);
        if (c == null) {
            throw new ValidityException("stream signaled for an exception, but exception object was null!");
        }
        if (!(c instanceof Instance)) {
            throw new ValidityException("stream signaled for an exception, but content is not an object!");
        }
        if (c.isExceptionObject()) {
            throw new ExceptionReadException(c);
        }
        c.setIsExceptionObject(true);
        reset();
        return c;
    }

    private ClassDesc readClassDesc(DataInputStream dis) throws IOException {
        byte tc = dis.readByte();
        return handleClassDesc(tc, dis, false);
    }

    private ClassDesc readNewClassDesc(DataInputStream dis) throws IOException {
        byte tc = dis.readByte();
        return handleNewClassDesc(tc, dis);
    }

    private Content readPrevObject(DataInputStream dis) throws IOException {
        int handle = dis.readInt();
        if (!handles.containsKey(handle)) {
            throw new ValidityException("can't find an entry for handle " + hex(handle));
        }
        Content c = handles.get(handle);
        LOG.trace("prevObject: handle {} ClassDesc {}", hex(c.getHandle()), c);
        return c;
    }

    private ClassDesc handleNewClassDesc(byte tc, DataInputStream dis) throws IOException {
        return handleClassDesc(tc, dis, true);
    }

    private ClassDesc handleClassDesc(byte tc, DataInputStream dis, boolean mustBeNew) throws IOException {
        if (tc == ObjectStreamConstants.TC_CLASSDESC) {
            String name = dis.readUTF();
            long serialVersionUID = dis.readLong();
            int handle = newHandle();
            byte descFlags = dis.readByte();
            short nFields = dis.readShort();
            if (nFields < 0) {
                throw new IOException("invalid field count: " + nFields);
            }
            Field[] fields = new Field[nFields];
            for (short s = 0; s < nFields; s++) {
                byte fType = dis.readByte();
                if (fType == 'B' || fType == 'C' || fType == 'D'
                        || fType == 'F' || fType == 'I' || fType == 'J'
                        || fType == 'S' || fType == 'Z') {
                    String fieldName = dis.readUTF();
                    fields[s] = new Field(FieldType.get(fType), fieldName);
                } else if (fType == '[' || fType == 'L') {
                    String fieldBame = dis.readUTF();
                    byte stc = dis.readByte();
                    StringObj classname = readNewString(stc, dis);
                    fields[s] = new Field(FieldType.get(fType), fieldBame, classname);
                } else {
                    throw new IOException("invalid field type char: " + hex(fType));
                }
            }
            ClassDesc cd = new ClassDesc(ClassDescType.NORMAL_CLASS);
            cd.name = name;
            cd.serialVersionUID = serialVersionUID;
            cd.handle = handle;
            cd.descFlags = descFlags;
            cd.fields = fields;
            cd.annotations = readClassAnnotation(dis);
            cd.superclass = readClassDesc(dis);
            setHandle(handle, cd);
            LOG.trace("read new ClassDesc: handle {} name {}", hex(handle), name);
            return cd;
        } else if (tc == ObjectStreamConstants.TC_NULL) {
            if (mustBeNew) {
                throw new ValidityException("expected new class description -- got null!");
            }
            LOG.trace("read null ClassDesc");
            return null;
        } else if (tc == ObjectStreamConstants.TC_REFERENCE) {
            if (mustBeNew) {
                throw new ValidityException("expected new class description -- got a reference!");
            }
            Content c = readPrevObject(dis);
            if (!(c instanceof ClassDesc)) {
                throw new IOException("referenced object not a class description!");
            }
            return (ClassDesc) c;
        } else if (tc == ObjectStreamConstants.TC_PROXYCLASSDESC) {
            int handle = newHandle();
            int iCount = dis.readInt();
            if (iCount < 0) {
                throw new IOException("invalid proxy interface count: " + hex(iCount));
            }
            String[] interfaces = new String[iCount];
            for (int i = 0; i < iCount; i++) {
                interfaces[i] = dis.readUTF();
            }
            ClassDesc cd = new ClassDesc(ClassDescType.PROXY_CLASS);
            cd.handle = handle;
            cd.interfaces = interfaces;
            cd.annotations = readClassAnnotation(dis);
            cd.superclass = readClassDesc(dis);
            setHandle(handle, cd);
            cd.name = "(proxy class; no name)";
            LOG.trace("read new proxy ClassDesc: handle {} names [{}]", hex(handle), Arrays.toString(interfaces));
            return cd;
        } else {
            throw new ValidityException("expected a valid class description starter got " + hex(tc));
        }
    }

    private ArrayObj readNewArray(DataInputStream dis) throws IOException {
        ClassDesc cd = readClassDesc(dis);
        int handle = newHandle();
        LOG.trace("reading new array: handle {} ClassDesc {}", hex(handle), cd.toString());
        if (cd.name.length() < 2) {
            throw new IOException("invalid name in array ClassDesc: " + cd.name);
        }
        ArrayColl ac = readArrayValues(cd.name.substring(1), dis);
        ArrayObj ao = new ArrayObj(handle, cd, ac);
        setHandle(handle, ao);
        return ao;
    }

    private ArrayColl readArrayValues(String str, DataInputStream dis) throws IOException {
        byte b = str.getBytes(StandardCharsets.UTF_8)[0];
        FieldType ft = FieldType.get(b);
        int size = dis.readInt();
        if (size < 0) {
            throw new IOException("invalid array size: " + size);
        }

        ArrayColl ac = new ArrayColl(ft);
        for (int i = 0; i < size; i++) {
            ac.add(readFieldValue(ft, dis));
        }
        return ac;
    }

    private ClassObj readNewClass(DataInputStream dis) throws IOException {
        ClassDesc cd = readClassDesc(dis);
        int handle = newHandle();
        LOG.trace("reading new class: handle {} ClassDesc {}", hex(handle), cd.toString());
        ClassObj c = new ClassObj(handle, cd);
        setHandle(handle, c);
        return c;
    }

    private EnumObj readNewEnum(DataInputStream dis) throws IOException {
        ClassDesc cd = readClassDesc(dis);
        if (cd == null) {
            throw new IOException("enum ClassDesc can't be null!");
        }
        int handle = newHandle();
        LOG.trace("reading new enum: handle {} ClassDesc {}", hex(handle), cd);
        byte tc = dis.readByte();
        StringObj so = readNewString(tc, dis);
        cd.addEnum(so.value);
        setHandle(handle, so);
        return new EnumObj(handle, cd, so);
    }

    private StringObj readNewString(byte tc, DataInputStream dis) throws IOException {
        byte[] data;
        if (tc == ObjectStreamConstants.TC_REFERENCE) {
            Content c = readPrevObject(dis);
            if (!(c instanceof StringObj)) {
                throw new IOException("got reference for a string, but referenced value was something else!");
            }
            return (StringObj) c;
        }
        int handle = newHandle();
        if (tc == ObjectStreamConstants.TC_STRING) {
            int len = dis.readUnsignedShort();
            data = new byte[len];
        } else if (tc == ObjectStreamConstants.TC_LONGSTRING) {
            long len = dis.readLong();
            if (len < 0) {
                throw new IOException("invalid long string length: " + len);
            }
            if (len > 2147483647) {
                throw new IOException("long string is too long: " + len);
            }
            if (len < 65536) {
                LOG.warn("small string length encoded as TC_LONGSTRING: " + len);
            }
            data = new byte[(int) len];
        } else if (tc == ObjectStreamConstants.TC_NULL) {
            throw new ValidityException("stream signaled TC_NULL when string type expected!");
        } else {
            throw new IOException("invalid tc byte in string: " + hex(tc));
        }
        dis.readFully(data);
        LOG.trace("reading new string: handle {} bufsz {}", hex(handle), data.length);
        StringObj sobj = new StringObj(handle, data);
        setHandle(handle, sobj);
        return sobj;
    }

    private BlockData readBlockData(byte tc, DataInputStream dis) throws IOException {
        int size;
        if (tc == ObjectStreamConstants.TC_BLOCKDATA) {
            size = dis.readUnsignedByte();
        } else if (tc == ObjectStreamConstants.TC_BLOCKDATALONG) {
            size = dis.readInt();
        } else {
            throw new IOException("invalid tc value for blockdata: " + hex(tc));
        }
        if (size < 0) {
            throw new IOException("invalid value for blockdata size: " + size);
        }
        byte[] b = new byte[size];
        dis.readFully(b);
        LOG.trace("read blockdata of size {}", size);
        return new BlockData(b);
    }

    private Instance readNewObject(DataInputStream dis) throws IOException {
        ClassDesc cd = readClassDesc(dis);
        int handle = newHandle();
        LOG.trace("reading new object: handle {} ClassDesc {}", hex(handle), cd.toString());
        Instance i = new Instance();
        i.classDesc = cd;
        i.handle = handle;
        setHandle(handle, i);
        readClassData(dis, i);
        LOG.trace("done reading object for handle {}", hex(handle));
        return i;
    }

    /**
     * <p>
     * Read the next object corresponding to the spec grammar rule "content", and return
     * an object of type content.
     * </p>
     *
     * <p>
     * Usually, there is a 1:1 mapping of content items and returned instances.  The
     * one case where this isn't true is when an exception is embedded inside another
     * object.  When this is encountered, only the serialized exception object is
     * returned; it's up to the caller to backtrack in order to gather any data from the
     * object that was being serialized when the exception was thrown.
     * </p>
     *
     * @param tc        the last byte read from the stream; it must be one of the TC_* values
     *                  within ObjectStreamConstants.*
     * @param dis       the DataInputStream to read from
     * @param blockData whether or not to read TC_BLOCKDATA (this is the difference
     *                  between spec rules "object" and "content").
     * @return an object representing the last read item from the stream
     * @throws IOException when a validity or I/O error occurs while reading
     */
    private Content readContent(byte tc, DataInputStream dis, boolean blockData) throws IOException {
        try {
            switch (tc) {
                case ObjectStreamConstants.TC_OBJECT:
                    return readNewObject(dis);
                case ObjectStreamConstants.TC_CLASS:
                    return readNewClass(dis);
                case ObjectStreamConstants.TC_ARRAY:
                    return readNewArray(dis);
                case ObjectStreamConstants.TC_STRING:
                case ObjectStreamConstants.TC_LONGSTRING:
                    return readNewString(tc, dis);
                case ObjectStreamConstants.TC_ENUM:
                    return readNewEnum(dis);
                case ObjectStreamConstants.TC_CLASSDESC:
                case ObjectStreamConstants.TC_PROXYCLASSDESC:
                    return handleNewClassDesc(tc, dis);
                case ObjectStreamConstants.TC_REFERENCE:
                    return readPrevObject(dis);
                case ObjectStreamConstants.TC_NULL:
                    return null;
                case ObjectStreamConstants.TC_EXCEPTION:
                    return readException(dis);
                case ObjectStreamConstants.TC_BLOCKDATA:
                case ObjectStreamConstants.TC_BLOCKDATALONG:
                    if (!blockData) {
                        throw new IOException("got a blockdata TC_*, but not allowed here: " + hex(tc));
                    }
                    return readBlockData(tc, dis);
                default:
                    throw new IOException("unknown content tc byte in stream: " + hex(tc));
            }
        } catch (ExceptionReadException ere) {
            return ere.getExceptionObject();
        }
    }

    /**
     * <p>
     * Reads in an entire ObjectOutputStream output on the given stream, filing
     * this object's content and handle maps with data about the objects in the stream.
     * </p>
     *
     * <p>
     * If shouldConnect is true, then jdeserialize will attempt to identify member classes
     * by their names according to the details laid out in the Inner Classes
     * Specification.  If it finds one, it will set the ClassDesc's flag indicating that
     * it is an member class, and it will create a reference in its enclosing class.
     * </p>
     *
     * @param is            an open InputStream on a serialized stream of data
     * @param shouldConnect true if jdeserialize should attempt to identify and connect
     *                      member classes with their enclosing classes
     *                      <p>
     *                      Also see the <pre>connectMemberClasses</pre> method for more information on the
     *                      member-class-detection algorithm.
     */
    public void run(InputStream is, boolean shouldConnect) throws IOException {
        try (LoggerInputStream lis = new LoggerInputStream(is);
             DataInputStream dis = new DataInputStream(lis)) {

            short magic = dis.readShort();
            if (magic != ObjectStreamConstants.STREAM_MAGIC) {
                throw new ValidityException("file magic mismatch!  expected " + ObjectStreamConstants.STREAM_MAGIC + ", got " + magic);
            }
            short streamVersion = dis.readShort();
            if (streamVersion != ObjectStreamConstants.STREAM_VERSION) {
                throw new ValidityException("file version mismatch!  expected " + ObjectStreamConstants.STREAM_VERSION + ", got " + streamVersion);
            }
            reset();
            content = new ArrayList<>();
            while (true) {
                byte tc;
                try {
                    lis.startRecording();
                    tc = dis.readByte();
                    if (tc == ObjectStreamConstants.TC_RESET) {
                        reset();
                        continue;
                    }
                } catch (EOFException eoe) {
                    break;
                }
                Content c = readContent(tc, dis, true);
                LOG.info("read: {}", c);
                if (c != null && c.isExceptionObject()) {
                    c = new ExceptionState(c, lis.getRecordedData());
                }
                content.add(c);
            }
        }
        for (Content c : handles.values()) {
            c.validate();
        }
        if (shouldConnect) {
            connectMemberClasses();
            for (Content c : handles.values()) {
                c.validate();
            }
        }
        if (handles.size() > 0) {
            handleMaps.add(new HashMap<>(handles));
        }
    }

    // Write raw blockdata out to the specified file
    // Write blockdata manifest out to the specified file
    public void dumpBlockData(String filename) throws IOException {
        String ext = filename.contains(".") ?
                filename.substring(filename.lastIndexOf('.')) :
                "";
        String fn = filename.contains(".") ?
                filename.substring(0, filename.lastIndexOf('.')) :
                filename;
        try (FileOutputStream bos = new FileOutputStream(filename);
             FileOutputStream mos = new FileOutputStream(fn + ".manifest" + ext);
             PrintWriter pw = new PrintWriter(mos)) {
            pw.println("# Each line in this file that doesn't begin with a '#' contains the size of");
            pw.println("# an individual blockdata block written to the stream.");
            for (Content c : content) {
                if (c instanceof BlockData) {
                    BlockData bd = (BlockData) c;
                    pw.println(bd.buf.length);
                    bos.write(bd.buf);
                }
            }
        }
    }

    public void dumpContent(PrintStream out) {
        out.println("//// BEGIN stream content output");
        for (Content c : content) {
            out.println(c.toString());
        }
        out.println("//// END stream content output");
        out.println();
    }

    public void dumpClasses(PrintStream out, boolean showArray, String filter, boolean fixNames) throws IOException {
        out.println("//// BEGIN class declarations"
                + (showArray ? "" : " (excluding array classes)")
                + (filter != null ? " (exclusion filter " + filter + ")" : ""));
        for (Content c : handles.values()) {
            if (c instanceof ClassDesc) {
                ClassDesc cl = (ClassDesc) c;
                if (!showArray && cl.isArrayClass()) {
                    continue;
                }
                // Member classes will be displayed as part of their enclosing classes.
                if (cl.isStaticMemberClass() || cl.isInnerClass()) {
                    continue;
                }
                if (filter != null && cl.name.matches(filter)) {
                    continue;
                }
                dumpClassDesc(0, cl, out, fixNames);
                out.println();
            }
        }
        out.println("//// END class declarations");
        out.println();
    }

    public void dumpInstances(PrintStream out) {
        out.println("//// BEGIN instance dump");
        for (Content c : handles.values()) {
            if (c instanceof Instance) {
                Instance i = (Instance) c;
                dumpInstance(i, out);
            }
        }
        out.println("//// END instance dump");
        out.println();
    }

    /**
     * <p>
     * Connects member classes according to the rules specified by the JDK 1.1 Inner
     * Classes Specification.
     * </p>
     *
     * <pre>
     * Inner classes:
     * for each class C containing an object reference member R named this$N, do:
     *     if the name of C matches the pattern O$I
     *     AND the name O matches the name of an existing type T
     *     AND T is the exact type referred to by R, then:
     *         don't display the declaration of R in normal dumping,
     *         consider C to be an inner class of O named I
     *
     * Static member classes (after):
     * for each class C matching the pattern O$I,
     * where O is the name of a class in the same package
     * AND C is not an inner class according to the above algorithm:
     *     consider C to be an inner class of O named I
     * </pre>
     *
     * <p>
     * This functions fills in the isInnerClass value in ClassDesc, the
     * isInnerClassReference value in field, the isLocalInnerClass value in
     * ClassDesc, and the isStaticMemberClass value in ClassDesc where necessary.
     * </p>
     *
     * <p>
     * A word on static classes: serializing a static member class S doesn't inherently
     * require serialization of its parent class P.  Unlike inner classes, S doesn't
     * retain an instance of P, and therefore P's class description doesn't need to be
     * written.  In these cases, if parent classes can be found, their static member
     * classes will be connected; but if they can't be found, the names will not be
     * changed and no ValidityException will be thrown.
     * </p>
     *
     * @throws ValidityException if the found values don't correspond to spec
     */
    private void connectMemberClasses() throws IOException {
        HashMap<ClassDesc, String> newNames = new HashMap<>();
        HashMap<String, ClassDesc> classes = new HashMap<>();
        HashSet<String> classnames = new HashSet<>();
        for (Content c : handles.values()) {
            if (!(c instanceof ClassDesc)) {
                continue;
            }
            ClassDesc cd = (ClassDesc) c;
            classes.put(cd.name, cd);
            classnames.add(cd.name);
        }
        Pattern fpat = Pattern.compile("^this\\$(\\d+)$");
        Pattern clpat = Pattern.compile("^((?:[^\\$]+\\$)*[^\\$]+)\\$([^\\$]+)$");
        for (ClassDesc cd : classes.values()) {
            if (cd.classType == ClassDescType.PROXY_CLASS) {
                continue;
            }
            for (Field f : cd.fields) {
                if (f.type != FieldType.OBJECT) {
                    continue;
                }
                Matcher m = fpat.matcher(f.name);
                if (!m.matches()) {
                    continue;
                }
                boolean isLocal = false;
                Matcher mat = clpat.matcher(cd.name);
                if (!mat.matches()) {
                    throw new ValidityException("inner class enclosing-class reference field exists, but class name doesn't match expected pattern: class " + cd.name + " field " + f.name);
                }
                String outer = mat.group(1);
                String inner = mat.group(2);
                ClassDesc outerClassDesc = classes.get(outer);
                if (outerClassDesc == null) {
                    throw new ValidityException("couldn't connect inner classes: outer class not found for field name " + f.name);
                }
                if (!outerClassDesc.name.equals(f.getJavaType())) {
                    throw new ValidityException("outer class field type doesn't match field type name: " + f.className.value + " outer class name " + outerClassDesc.name);
                }
                outerClassDesc.addInnerClass(cd);
                cd.setIsLocalInnerClass(isLocal);
                cd.setIsInnerClass(true);
                f.setIsInnerClassReference(true);
                newNames.put(cd, inner);
            }
        }
        for (ClassDesc cd : classes.values()) {
            if (cd.classType == ClassDescType.PROXY_CLASS) {
                continue;
            }
            if (cd.isInnerClass()) {
                continue;
            }
            Matcher matcher = clpat.matcher(cd.name);
            if (!matcher.matches()) {
                continue;
            }
            String outer = matcher.group(1);
            String inner = matcher.group(2);
            ClassDesc outerClassDesc = classes.get(outer);
            if (outerClassDesc != null) {
                outerClassDesc.addInnerClass(cd);
                cd.setIsStaticMemberClass(true);
                newNames.put(cd, inner);
            }
        }
        for (ClassDesc ncd : newNames.keySet()) {
            String newName = newNames.get(ncd);
            if (classnames.contains(newName)) {
                throw new ValidityException("can't rename class from " + ncd.name + " to " + newName + " -- class already exists!");
            }
            for (ClassDesc cd : classes.values()) {
                if (cd.classType == ClassDescType.PROXY_CLASS) {
                    continue;
                }
                for (Field f : cd.fields) {
                    if (f.getJavaType().equals(ncd.name)) {
                        f.setReferenceTypeName(newName);
                    }
                }
            }
            if (!classnames.remove(ncd.name)) {
                throw new ValidityException("tried to remove " + ncd.name + " from classnames cache, but couldn't find it!");
            }
            ncd.name = newName;
            if (!classnames.add(newName)) {
                throw new ValidityException("can't rename class to " + newName + " -- class already exists!");
            }
        }
    }

    /**
     * Decodes a class name according to the field-descriptor format in the jvm spec,
     * section 4.3.2.
     *
     * @param fDesc          name in field-descriptor format (Lfoo/bar/baz;)
     * @param convertSlashes true iff slashes should be replaced with periods (true for
     *                       "real" field-descriptor format; false for names in ClassDesc)
     * @return a fully-qualified class name
     * @throws ValidityException if the name isn't valid
     */
    private static String decodeClassName(String fDesc, boolean convertSlashes) throws ValidityException {
        if (fDesc.charAt(0) != 'L' || fDesc.charAt(fDesc.length() - 1) != ';' || fDesc.length() < 3) {
            throw new ValidityException("invalid name (not in field-descriptor format): " + fDesc);
        }
        String subs = fDesc.substring(1, fDesc.length() - 1);
        if (convertSlashes) {
            return subs.replace('/', '.');
        }
        return subs;
    }

    private static String indent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append(INDENT_CHARS);
        }
        return sb.toString();
    }

    static String hexNoPrefix(long value) {
        return hexNoPrefix(value, 2);
    }

    static String hexNoPrefix(long value, int len) {
        if (value < 0) {
            value = 256 + value;
        }
        String s = Long.toString(value, 16);
        while (s.length() < len) {
            s = "0" + s;
        }
        return s;
    }

    static String hex(long value) {
        return "0x" + hexNoPrefix(value);
    }
}
