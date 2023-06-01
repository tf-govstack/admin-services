package io.mosip.hotlist.exception;

import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.hotlist.constant.HotlistErrorConstants;

public class TokenGenerationFailedException extends BaseUncheckedException {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 1L;


	public TokenGenerationFailedException() {
		super(HotlistErrorConstants.TOKEN_GENERATION_FAILED.getErrorCode(), HotlistErrorConstants.TOKEN_GENERATION_FAILED.getErrorMessage());
	}

	public TokenGenerationFailedException(String errorMessage) {
		super(HotlistErrorConstants.TOKEN_GENERATION_FAILED.getErrorCode() + EMPTY_SPACE, errorMessage);
	}

	public TokenGenerationFailedException(String message, Throwable cause) {
		super(HotlistErrorConstants.TOKEN_GENERATION_FAILED.getErrorCode() + EMPTY_SPACE, message, cause);
	}
}

