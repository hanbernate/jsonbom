package io.github.hanbernate.jsonbom.api;
/**
 * Exception thrown when an error occurs during JSON BOM (Bill of Materials)
 * mapping or transformation operations.
 * <p>
 * This is a runtime exception, allowing callers to handle or propagate
 * errors without mandatory catch clauses.
 *
 * @author hanbernate
 * @since 0.0.1
 */
public class JsonBomException extends RuntimeException{
    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param msg the detail message explaining the cause of the exception
     * @since 0.0.1
     */
    public JsonBomException(String msg){
        super(msg);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param msg the detail message explaining the cause of the exception
     * @param e the underlying cause of the exception
     * @since 0.0.1
     */
    public JsonBomException(String msg, Exception e){
        super(msg, e);
    }
}
