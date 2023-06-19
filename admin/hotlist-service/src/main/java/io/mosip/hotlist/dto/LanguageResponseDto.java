package io.mosip.hotlist.dto;

import java.util.List;

import lombok.Data;

@Data
public class LanguageResponseDto {

	/**
	 * List of Languages.
	 */
	private List<LanguageDto> languages;

}