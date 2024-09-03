package org.unsynchronized;

/**
 * Provides a skeleton content implementation.
 */
public class ContentBase implements Content {

    int handle;

    boolean isExceptionObject;

    ContentType type;

    public ContentBase(ContentType type) {
        this.type = type;
    }

    @Override
    public boolean isExceptionObject() {
        return isExceptionObject;
    }

    @Override
    public void setIsExceptionObject(boolean value) {
        isExceptionObject = value;
    }

    @Override
    public ContentType getType() {
        return type;
    }

    @Override
    public int getHandle() {
        return this.handle;
    }

}

