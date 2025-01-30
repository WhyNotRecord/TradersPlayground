package ru.rexchange.exception;

public class SystemException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public SystemException(String s) {
		super(s);
	}

	public SystemException(String s, Object... params) {
		super(String.format(s, params));
	}

	public SystemException(Throwable e, String s, Object... params) {
		super(String.format(s, params), e);
	}

	public SystemException(Throwable e) {
		super(e);
	}

	public SystemException(String s, Throwable e) {
		super(s, e);
	}
}
