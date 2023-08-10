package io.mosip.kernel.masterdata.dto;

import lombok.Data;

@Data
public class PacketWorkflowInstanceRequestDto {

	private String registrationId;

	private String process;
	
	private String source;
	
	private String additionalInfoReqId;
	
	private RegistrationAdditionalInfoDTO additionalInfo;

}
