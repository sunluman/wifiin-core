package com.wifiin.exception;

import java.net.SocketException;
import java.net.UnknownHostException;

public class LocalhostException extends RuntimeException{

	public LocalhostException() {
		super();
		// TODO Auto-generated constructor stub
	}

	public LocalhostException(String message, UnknownHostException cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
		// TODO Auto-generated constructor stub
	}

	public LocalhostException(String message, UnknownHostException cause) {
		super(message, cause);
		// TODO Auto-generated constructor stub
	}

	public LocalhostException(String message) {
		super(message);
		// TODO Auto-generated constructor stub
	}

	public LocalhostException(UnknownHostException cause) {
		super(cause);
		// TODO Auto-generated constructor stub
	}
	public LocalhostException(SocketException cause){
		super(cause);
	}
}
