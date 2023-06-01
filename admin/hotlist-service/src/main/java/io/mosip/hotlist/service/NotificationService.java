package io.mosip.hotlist.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.templatemanager.spi.TemplateManager;
import io.mosip.hotlist.logger.HotlistLogger;
import io.mosip.hotlist.constant.ApiName;
import io.mosip.hotlist.constant.LoggerFileConstant;
import io.mosip.hotlist.constant.NotificationTemplateCode;
import io.mosip.hotlist.constant.HotlistErrorConstants;
import io.mosip.hotlist.dto.NotificationRequestDto;
import io.mosip.hotlist.dto.NotificationResponseDTO;
import io.mosip.hotlist.dto.SMSRequestDTO;
import io.mosip.hotlist.dto.TemplateDto;
import io.mosip.hotlist.dto.TemplateResponseDto;
import io.mosip.hotlist.exception.ApisResourceAccessException;
import io.mosip.hotlist.exception.HotlistAppException;
//import io.mosip.resident.exception.ResidentServiceException;
import io.mosip.hotlist.util.AuditUtil;
import io.mosip.hotlist.util.EventEnum;
import io.mosip.hotlist.util.JsonUtil;
import io.mosip.hotlist.util.HotlistServiceRestClient;
import io.mosip.hotlist.util.Utilities;
import io.mosip.hotlist.util.Utility;
import io.mosip.hotlist.validator.HotlistValidator;

/**
 * 
 * @author Anchit Ayush Guria
 *
 */
@Component
public class NotificationService {
	private static final Logger logger = HotlistLogger.getLogger(NotificationService.class);
	@Autowired
	private TemplateManager templateManager;

	@Value("${hotlist.notification.emails}")
	private String notificationEmails;

	@Value("${mosip.notificationtype}")
	private String notificationType;

	@Autowired
	private Environment env;

	@Autowired
	private HotlistServiceRestClient restClient;

	@Autowired
	private Utility utility;
	
	@Autowired
	private Utilities utilities;

	@Autowired
	private HotlistValidator requestValidator;
	
	@Autowired
	private AuditUtil audit;

	private static final String LINE_SEPARATOR = new  StringBuilder().append('\n').append('\n').append('\n').toString();
	private static final String EMAIL = "_EMAIL";
	private static final String SMS = "_SMS";
	private static final String SUBJECT = "_SUB";
	private static final String SMS_EMAIL_SUCCESS = "Notification has been sent to the provided contact detail(s)";
	private static final String SMS_SUCCESS = "Notification has been sent to the provided contact phone number";
	private static final String EMAIL_SUCCESS = "Notification has been sent to the provided email ";
	private static final String SMS_EMAIL_FAILED = "Invalid phone number and email";
	private static final String IS_SMS_NOTIFICATION_SUCCESS = "NotificationService::sendSMSNotification()::isSuccess?::";
	private static final String IS_EMAIL_NOTIFICATION_SUCCESS = "NotificationService::sendEmailNotification()::isSuccess?::";
	private static final String TEMPLATE_CODE = "Template Code";
	private static final String SUCCESS = "success";
	private static final String SEPARATOR = "/";

	public NotificationResponseDTO sendNotification(NotificationRequestDto dto) throws HotlistAppException {
		logger.debug(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), dto.getId(),
				"NotificationService::sendNotification()::entry");
		boolean smsStatus = false;
		boolean emailStatus = false;
		Set<String> templateLangauges = new HashSet<String>();
		Map<String, Object> notificationAttributes = utility.getMailingAttributes(dto.getId(), templateLangauges);
		
		if(dto.getIdType().equalsIgnoreCase("UIN")) {
			notificationAttributes.put("UIN",utility.convertToMaskDataFormat(dto.getId()));
		}
		
		if(dto.getIdType().equalsIgnoreCase("VID")) {
			notificationAttributes.put("VID",utility.convertToMaskDataFormat(dto.getId()));
		}
		
		if (dto.getAdditionalAttributes() != null && dto.getAdditionalAttributes().size() > 0) {
			notificationAttributes.putAll(dto.getAdditionalAttributes());
		}
		if (notificationType.equalsIgnoreCase("SMS|EMAIL")) {
			smsStatus = sendSMSNotification(notificationAttributes, dto.getTemplateTypeCode(), templateLangauges);
			emailStatus = sendEmailNotification(notificationAttributes, dto.getTemplateTypeCode(), null,
					templateLangauges);
		} else if (notificationType.equalsIgnoreCase("EMAIL")) {
			emailStatus = sendEmailNotification(notificationAttributes, dto.getTemplateTypeCode(), null,
					templateLangauges);
		} else if (notificationType.equalsIgnoreCase("SMS")) {
			smsStatus = sendSMSNotification(notificationAttributes, dto.getTemplateTypeCode(), templateLangauges);
		}

		logger.info(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), dto.getId(),
				IS_SMS_NOTIFICATION_SUCCESS + smsStatus);
		logger.info(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), dto.getId(),
				IS_EMAIL_NOTIFICATION_SUCCESS + emailStatus);
		NotificationResponseDTO notificationResponse = new NotificationResponseDTO();
		if (smsStatus && emailStatus) {
			notificationResponse.setMessage(SMS_EMAIL_SUCCESS);
			notificationResponse.setStatus(SUCCESS);
		} else if (smsStatus) {
			notificationResponse.setMessage(SMS_SUCCESS);
		} else if (emailStatus) {
			notificationResponse.setMessage(EMAIL_SUCCESS);
		} else {
			notificationResponse.setMessage(SMS_EMAIL_FAILED);
			throw new HotlistAppException(HotlistErrorConstants.NOTIFICATION_FAILURE.getErrorCode(),
					HotlistErrorConstants.NOTIFICATION_FAILURE.getErrorMessage() + " " + SMS_EMAIL_FAILED);
		}

		logger.info(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), dto.getId(),
				"NotificationService::sendSMSNotification()::isSuccess?::" + notificationResponse.getMessage());
		logger.debug(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), dto.getId(),
				"NotificationService::sendNotification()::exit");
		return notificationResponse;
	}

	@SuppressWarnings("unchecked")
	private String getTemplate(String langCode, String templatetypecode) throws HotlistAppException {
		logger.debug(LoggerFileConstant.APPLICATIONID.toString(), TEMPLATE_CODE, templatetypecode,
				"NotificationService::getTemplate()::entry");
		List<String> pathSegments = new ArrayList<>();
		pathSegments.add(langCode);
		pathSegments.add(templatetypecode);
		try {
			ResponseWrapper<TemplateResponseDto> resp = (ResponseWrapper<TemplateResponseDto>) restClient.getApi(
					ApiName.TEMPLATES, pathSegments, "", null, ResponseWrapper.class);
			if (resp == null || resp.getErrors() != null && !resp.getErrors().isEmpty()) {
				audit.setAuditRequestDto(EventEnum.TEMPLATE_EXCEPTION);
				throw new HotlistAppException(HotlistErrorConstants.TEMPLATE_EXCEPTION.getErrorCode(),
						HotlistErrorConstants.TEMPLATE_EXCEPTION.getErrorMessage()
								+ (resp != null ? resp.getErrors().get(0) : ""));
			}
			TemplateResponseDto templateResponse = JsonUtil.readValue(JsonUtil.writeValueAsString(resp.getResponse()),
					TemplateResponseDto.class);
			logger.info(LoggerFileConstant.APPLICATIONID.toString(), TEMPLATE_CODE, templatetypecode,
					"NotificationService::getTemplate()::getTemplateResponse::" + JsonUtil.writeValueAsString(resp));
			List<TemplateDto> response = templateResponse.getTemplates();
			logger.debug(LoggerFileConstant.APPLICATIONID.toString(), TEMPLATE_CODE, templatetypecode,
					"NotificationService::getTemplate()::exit");
			return response.get(0).getFileText().replaceAll("^\"|\"$", "");
		} catch (IOException e) {
			audit.setAuditRequestDto(EventEnum.TOKEN_GENERATION_FAILED);
			throw new HotlistAppException(HotlistErrorConstants.TOKEN_GENERATION_FAILED.getErrorCode(),
					HotlistErrorConstants.TOKEN_GENERATION_FAILED.getErrorMessage(), e);
		} catch (ApisResourceAccessException e) {
			if (e.getCause() instanceof HttpClientErrorException) {
				HttpClientErrorException httpClientException = (HttpClientErrorException) e.getCause();
				throw new HotlistAppException(
						HotlistErrorConstants.API_RESOURCE_ACCESS_EXCEPTION.getErrorCode(),
						httpClientException.getResponseBodyAsString());

			} else if (e.getCause() instanceof HttpServerErrorException) {
				HttpServerErrorException httpServerException = (HttpServerErrorException) e.getCause();
				throw new HotlistAppException(
						HotlistErrorConstants.API_RESOURCE_ACCESS_EXCEPTION.getErrorCode(),
						httpServerException.getResponseBodyAsString());
			} else {
				throw new HotlistAppException(
						HotlistErrorConstants.API_RESOURCE_ACCESS_EXCEPTION.getErrorCode(),
						HotlistErrorConstants.API_RESOURCE_ACCESS_EXCEPTION.getErrorMessage() + e.getMessage(), e);
			}
		}

	}

	private String templateMerge(String fileText, Map<String, Object> mailingAttributes)
			throws HotlistAppException {
		logger.debug(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), "",
				"NotificationService::templateMerge()::entry");
		try {
			String mergeTemplate;
			InputStream templateInputStream = new ByteArrayInputStream(fileText.getBytes(Charset.forName("UTF-8")));

			InputStream resultedTemplate = templateManager.merge(templateInputStream, mailingAttributes);

			mergeTemplate = IOUtils.toString(resultedTemplate, StandardCharsets.UTF_8.name());
			logger.debug(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), "",
					"NotificationService::templateMerge()::exit");
			return mergeTemplate;
		} catch (IOException e) {
			throw new HotlistAppException(HotlistErrorConstants.IO_EXCEPTION.getErrorCode(),
					HotlistErrorConstants.IO_EXCEPTION.getErrorMessage(), e);
		}
	}

	private boolean sendSMSNotification(Map<String, Object> mailingAttributes,
			NotificationTemplateCode notificationTemplate, Set<String> templateLangauges)
			throws HotlistAppException {
		logger.debug(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), " ",
				"NotificationService::sendSMSNotification()::entry");
		String phone = (String) mailingAttributes.get(utilities.getPhoneAttribute());
		if (nullValueCheck(phone) || !(requestValidator.phoneValidator(phone))) {
			logger.info(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), " ",
					"NotificationService::sendSMSNotification()::phoneValidatio::" + "false :: invalid phone number");
			return false;
		}
		String mergedTemplate = "";
		
		for (String language : templateLangauges) {
			String languageTemplate = templateMerge(getTemplate(language, notificationTemplate + SMS),
					mailingAttributes);
			if (mergedTemplate.isBlank()) {
				mergedTemplate = languageTemplate;
			}else {
				mergedTemplate = mergedTemplate + LINE_SEPARATOR
						+ languageTemplate;
			}
		}
		
		SMSRequestDTO smsRequestDTO = new SMSRequestDTO();
		smsRequestDTO.setMessage(mergedTemplate);
		smsRequestDTO.setNumber(phone);
		RequestWrapper<SMSRequestDTO> req = new RequestWrapper<>();
		req.setRequest(smsRequestDTO);
		ResponseWrapper<NotificationResponseDTO> resp;
		try {
			resp = restClient.postApi(env.getProperty(ApiName.SMSNOTIFIER.name()), MediaType.APPLICATION_JSON, req,
					ResponseWrapper.class);
			if (nullCheckForResponse(resp)) {
				throw new HotlistAppException(HotlistErrorConstants.INVALID_API_RESPONSE.getErrorCode(),
						HotlistErrorConstants.INVALID_API_RESPONSE.getErrorMessage() + " SMSNOTIFIER API"
								+ (resp != null ? resp.getErrors().get(0) : ""));
			}
			NotificationResponseDTO notifierResponse = JsonUtil
					.readValue(JsonUtil.writeValueAsString(resp.getResponse()), NotificationResponseDTO.class);
			logger.info(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), " ",
					"NotificationService::sendSMSNotification()::response::"
							+ JsonUtil.writeValueAsString(notifierResponse));

			if (SUCCESS.equalsIgnoreCase(notifierResponse.getStatus())) {
				logger.debug(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), " ",
						"NotificationService::sendSMSNotification()::exit");
				return true;
			}
		} catch (ApisResourceAccessException e) {

			if (e.getCause() instanceof HttpClientErrorException) {
				HttpClientErrorException httpClientException = (HttpClientErrorException) e.getCause();
				logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
						e.getMessage() + httpClientException.getResponseBodyAsString());
				throw new HotlistAppException(
						HotlistErrorConstants.API_RESOURCE_ACCESS_EXCEPTION.getErrorCode(),
						httpClientException.getResponseBodyAsString());

			} else if (e.getCause() instanceof HttpServerErrorException) {
				HttpServerErrorException httpServerException = (HttpServerErrorException) e.getCause();
				logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
						e.getMessage() + httpServerException.getResponseBodyAsString());
				throw new HotlistAppException(
						HotlistErrorConstants.API_RESOURCE_ACCESS_EXCEPTION.getErrorCode(),
						httpServerException.getResponseBodyAsString());
			} else {
				logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
						e.getMessage() + ExceptionUtils.getStackTrace(e));
				throw new HotlistAppException(
						HotlistErrorConstants.API_RESOURCE_ACCESS_EXCEPTION.getErrorCode(),
						HotlistErrorConstants.API_RESOURCE_ACCESS_EXCEPTION.getErrorMessage() + e.getMessage(), e);
			}

		} catch (IOException e) {
			logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
					e.getMessage() + ExceptionUtils.getStackTrace(e));
			audit.setAuditRequestDto(EventEnum.TOKEN_GENERATION_FAILED);
			throw new HotlistAppException(HotlistErrorConstants.TOKEN_GENERATION_FAILED.getErrorCode(),
					HotlistErrorConstants.TOKEN_GENERATION_FAILED.getErrorMessage(), e);
		}
		logger.debug(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), " ",
				"NotificationService::sendSMSNotification()::exit");

		return false;

	}

	private boolean sendEmailNotification(Map<String, Object> mailingAttributes,
			NotificationTemplateCode notificationTemplate, MultipartFile[] attachment, Set<String> templateLangauges)
			throws HotlistAppException {
		logger.debug(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), " ",
				"NotificationService::sendEmailNotification()::entry");
		String email = String.valueOf(mailingAttributes.get(utilities.getEmailAttribute()));
		if (nullValueCheck(email) || !(requestValidator.emailValidator(email))) {
			logger.info(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), " ",
					"NotificationService::sendEmailNotification()::emailValidation::" + "false :: invalid email");
			return false;
		}
		String mergedEmailSubject = "";
		String mergedTemplate = "";
		
		for (String language : templateLangauges) {
			String emailSubject = getTemplate(language, notificationTemplate + EMAIL + SUBJECT);
			String languageTemplate = templateMerge(getTemplate(language, notificationTemplate + EMAIL),
					mailingAttributes);
			if (mergedTemplate.isBlank() || mergedEmailSubject.isBlank()) {
				mergedTemplate = languageTemplate;
				mergedEmailSubject = emailSubject;
			} else {
				mergedTemplate = mergedTemplate + LINE_SEPARATOR + languageTemplate;
				mergedEmailSubject = mergedEmailSubject + SEPARATOR + emailSubject;
			}
		}
		
		LinkedMultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
		String[] mailTo = { String.valueOf(mailingAttributes.get("email")) };
		String[] mailCc = notificationEmails.split("\\|");

		for (String item : mailTo) {
			params.add("mailTo", item);
		}

		if (mailCc != null) {
			for (String item : mailCc) {
				params.add("mailCc", item);
			}
		}

		try {
			params.add("mailSubject", mergedEmailSubject);
			params.add("mailContent", mergedTemplate);
			params.add("attachments", attachment);
			ResponseWrapper<NotificationResponseDTO> response;

			response = restClient.postApi(env.getProperty(ApiName.EMAILNOTIFIER.name()), MediaType.MULTIPART_FORM_DATA, params,
					ResponseWrapper.class);
			if (nullCheckForResponse(response)) {
				throw new HotlistAppException(HotlistErrorConstants.INVALID_API_RESPONSE.getErrorCode(),
						HotlistErrorConstants.INVALID_API_RESPONSE.getErrorMessage() + " EMAILNOTIFIER API"
								+ (response != null ? response.getErrors().get(0) : ""));
			}
			NotificationResponseDTO notifierResponse = JsonUtil
					.readValue(JsonUtil.writeValueAsString(response.getResponse()), NotificationResponseDTO.class);
			logger.info(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), " ",
					"NotificationService::sendEmailNotification()::response::"
							+ JsonUtil.writeValueAsString(notifierResponse));

			if ("success".equals(notifierResponse.getStatus())) {
				logger.debug(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), " ",
						"NotificationService::sendEmailNotification()::exit");
				return true;
			}
		} catch (ApisResourceAccessException e) {
			if (e.getCause() instanceof HttpClientErrorException) {
				HttpClientErrorException httpClientException = (HttpClientErrorException) e.getCause();
				throw new HotlistAppException(
						HotlistErrorConstants.API_RESOURCE_ACCESS_EXCEPTION.getErrorCode(),
						httpClientException.getResponseBodyAsString());

			} else if (e.getCause() instanceof HttpServerErrorException) {
				HttpServerErrorException httpServerException = (HttpServerErrorException) e.getCause();
				throw new HotlistAppException(
						HotlistErrorConstants.API_RESOURCE_ACCESS_EXCEPTION.getErrorCode(),
						httpServerException.getResponseBodyAsString());
			} else {
				throw new HotlistAppException(
						HotlistErrorConstants.API_RESOURCE_ACCESS_EXCEPTION.getErrorCode(),
						HotlistErrorConstants.API_RESOURCE_ACCESS_EXCEPTION.getErrorMessage() + e.getMessage(), e);
			}

		} catch (IOException e) {
			audit.setAuditRequestDto(EventEnum.TOKEN_GENERATION_FAILED);
			throw new HotlistAppException(HotlistErrorConstants.TOKEN_GENERATION_FAILED.getErrorCode(),
					HotlistErrorConstants.TOKEN_GENERATION_FAILED.getErrorMessage(), e);
		}
		logger.debug(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), " ",
				"NotificationService::sendEmailNotification()::exit");
		return false;

	}

	public boolean nullValueCheck(String value) {
		if (value == null || value.isEmpty())
			return true;
		return false;
	}

	public boolean nullCheckForResponse(ResponseWrapper<NotificationResponseDTO> response) {
		if (response == null || response.getResponse() == null
				|| response.getErrors() != null && !response.getErrors().isEmpty())
			return true;
		return false;

	}

	public UriComponentsBuilder prepareBuilder(String[] mailTo, String[] mailCc) {
		String apiHost = env.getProperty(ApiName.EMAILNOTIFIER.name());
		UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiHost);
		for (String item : mailTo) {
			builder.queryParam("mailTo", item);
		}

		if (mailCc != null) {
			for (String item : mailCc) {
				builder.queryParam("mailCc", item);
			}
		}
		return builder;
	}
}
