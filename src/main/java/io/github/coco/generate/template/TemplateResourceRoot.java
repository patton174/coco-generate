package io.github.coco.generate.template;

import java.io.IOException;

/** Reads files below one template-pack root without escaping it. */
public interface TemplateResourceRoot {

    /** Maximum size of one manifest or template file. */
    long MAX_FILE_BYTES = 1024L * 1024L;

    byte[] read(String relativePath) throws IOException;

    boolean exists(String relativePath);

    String describe();
}
