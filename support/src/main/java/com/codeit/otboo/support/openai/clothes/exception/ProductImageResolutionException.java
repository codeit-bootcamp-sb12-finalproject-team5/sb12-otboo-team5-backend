package com.codeit.otboo.support.openai.clothes.exception;

public class ProductImageResolutionException extends RuntimeException {

    public ProductImageResolutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProductImageResolutionException(String message) {
        super(message);
    }
}
