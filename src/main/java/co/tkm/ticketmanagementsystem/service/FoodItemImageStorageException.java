package co.tkm.ticketmanagementsystem.service;

import lombok.Getter;

@Getter
public class FoodItemImageStorageException extends RuntimeException {

    public enum Type {
        INVALID_FORMAT,
        FILE_TOO_LARGE,
        STORAGE_FAILED,
        INVALID_PATH
    }

    private final Type type;

    public FoodItemImageStorageException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public FoodItemImageStorageException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }
}
