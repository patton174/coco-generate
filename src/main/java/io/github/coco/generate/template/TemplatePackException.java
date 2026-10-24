package io.github.coco.generate.template;

import io.github.coco.generate.core.GenerationException;

/** Raised when a template pack is missing, invalid, or cannot be rendered safely. */
public final class TemplatePackException extends GenerationException {
    public TemplatePackException(String message) {
        super(message);
    }

    public TemplatePackException(String message, Throwable cause) {
        super(message, cause);
    }
}
