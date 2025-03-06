package org.unsynchronized;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class JExtract {

    private final Map<Integer, Content> contents = new HashMap<>();

    public JExtract(InputStream is) throws IOException {
        JDeserialize jd = new JDeserialize();
        jd.run(is, true);
        for (Map<Integer, Content> m : jd.getHandleMaps()) {
            contents.putAll(m);
        }
    }

    public int handleForClass(String name) {
        for (Content c : contents.values()) {
            if (c instanceof Instance in) {
                for (ClassDesc cd : in.fieldData.keySet()) {
                    if (cd.name.equals(name))
                        return cd.getHandle();
                }
            }
        }
        return -1;
    }

    public int handleForField(String name, int classHandle) {
        for (Content c : contents.values()) {
            if (c instanceof Instance in) {
                for (ClassDesc cd : in.fieldData.keySet()) {
                    if (cd.getHandle() == classHandle) {
                        for (Field f : cd.fields) {
                            if (f.name.equals(name)) {
                                Object o = in.fieldData.get(cd).get(f);
                                if (o instanceof Instance oi) {
                                    return oi.getHandle();
                                }
                            }
                        }
                    }
                }
            }
        }
        return -1;
    }

    public Object valueOf(String name, int parent) {
        if (contents.containsKey(parent)) {
            Instance cd = (Instance) contents.get(parent);
            for (Field f : cd.classDesc.fields) {
                if (f.name.equals(name)) {
                    return cd.fieldData.get(cd.classDesc).get(f);
                }
            }
        }
        return -1;
    }

    public Object valueOf(String className, String fieldName, String valueName) {
        int hType = handleForClass(className);
        int fType = handleForField(fieldName, hType);
        return valueOf(valueName, fType);
    }

    public Object valueOf(String className, String fieldName) {
        return valueOf(className, fieldName, "value");
    }
}
