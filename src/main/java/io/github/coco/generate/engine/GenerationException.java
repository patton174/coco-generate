package io.github.coco.generate.engine;

/** Raised when source generation cannot safely build or apply a plan. */
public final class GenerationException extends RuntimeException {
    public GenerationException(String message) {
        super(message);
    }

    public GenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
