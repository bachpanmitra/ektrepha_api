package com.ektrepha.exception;

/** The uploaded file isn't an image type this app accepts (see ChildServiceImpl#uploadPhoto). */
public class InvalidPhotoException extends RuntimeException {

	public InvalidPhotoException(String message) {
		super(message);
	}

}
