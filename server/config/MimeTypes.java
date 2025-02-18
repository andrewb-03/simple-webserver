/** 
* @author Andrew Brockenborough
* @author Mazen Zwin
* 2/17/25 - A. Brockenborough coded the methods for MimeTypes.java and added comments.
*/ 

package server.config;

import java.util.HashMap;
import java.util.Map;

/**
 * This is a simple utility to map file extensions to their respective MIME types.
 */
public class MimeTypes {

    private final Map<String, String> mimeTypes;

    public static MimeTypes getDefault() {
        MimeTypes mimeTypes = new MimeTypes();

        mimeTypes.addMimeType("png", "image/png");
        mimeTypes.addMimeType("jpg", "image/jpeg");
        mimeTypes.addMimeType("jpeg", "image/jpeg");
        mimeTypes.addMimeType("txt", "text/plain");
        mimeTypes.addMimeType("html", "text/html");
        mimeTypes.addMimeType("htm", "text/html");
        mimeTypes.addMimeType("css", "text/css");
        mimeTypes.addMimeType("js", "application/javascript");
        mimeTypes.addMimeType("json", "application/json");

        return mimeTypes;
    }

    public MimeTypes() {
        this.mimeTypes = new HashMap<>();
    }

    /**
     * Adds or overrides a MIME type mapping.
     */
    public void addMimeType(String extension, String mimeType) {
        this.mimeTypes.put(extension, mimeType);
    }

    /**
     * Returns the associated MIME type for the given extension, or
     * "application/octet-stream" if no mapping is found.
     */
    public String getMimeTypeFromExtension(String extension) {
        return this.mimeTypes.getOrDefault(extension, "application/octet-stream");
    }
}

