package ru.rexchange.exception;

public class UserException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public UserException(String s) {
		super(s);
	}

	public UserException(String s, Object... params) {
		super(String.format(s, params));
	}

	public UserException(Throwable e) {
		super(e);
	}

	public UserException(String s, Throwable e) {
		super(s, e);
	}
}
