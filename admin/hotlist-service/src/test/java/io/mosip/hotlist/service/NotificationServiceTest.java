package io.mosip.hotlist.service;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.core.templatemanager.spi.TemplateManager;
import io.mosip.hotlist.constant.ApiName;
import io.mosip.hotlist.constant.NotificationTemplateCode;
import io.mosip.hotlist.dto.NotificationRequestDto;
import io.mosip.hotlist.dto.NotificationResponseDTO;
import io.mosip.hotlist.exception.ApisResourceAccessException;
import io.mosip.hotlist.exception.HotlistAppException;
import io.mosip.hotlist.util.AuditUtil;
import io.mosip.hotlist.util.JsonUtil;
import io.mosip.hotlist.util.HotlistServiceRestClient;
import io.mosip.hotlist.util.Utilities;
import io.mosip.hotlist.util.Utility;
import io.mosip.hotlist.validator.HotlistValidator;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*"})
@PrepareForTest({ JsonUtil.class, IOUtils.class, HashSet.class})
public class NotificationServiceTest {
	
	@InjectMocks
	private NotificationService notificationService;

	@Mock
	private Utility utility;
	
	@Mock
	private Utilities utilities;
	
	@Mock
	private Environment env;

	@Mock
	private HotlistServiceRestClient restClient;

	@Mock
	private TemplateManager templateManager;
	
	@Mock
	private AuditUtil audit;

	@Mock
	private HotlistValidator hotlistValidator;
	private Map<String, Object> mailingAttributes;
	private NotificationRequestDto reqDto;
	private ResponseWrapper<NotificationResponseDTO> smsNotificationResponse;
	Map<String, Object> additionalAttributes = new HashMap<>();

	private static final String SMS_EMAIL_SUCCESS = "Notification has been sent to the provided contact detail(s)";
	private static final String SMS_SUCCESS = "Notification has been sent to the provided contact phone number";
	private static final String EMAIL_SUCCESS = "Notification has been sent to the provided email ";

	@Before
	public void setUp() throws Exception {
		additionalAttributes.put("RID", "10008200070004420191203104356");
		mailingAttributes = new HashMap<String, Object>();
		mailingAttributes.put("fullName_eng", "Test");
		mailingAttributes.put("fullName_ara", "Test");
		mailingAttributes.put("phone", "9876543210");
		mailingAttributes.put("email", "test@test.com");
		Set<String> templateLangauges = new HashSet<String>();
		templateLangauges.add("eng");
		templateLangauges.add("ara");
		ReflectionTestUtils.setField(notificationService, "notificationType", "SMS|EMAIL");
		ReflectionTestUtils.setField(notificationService, "notificationEmails", "test@test.com|test1@test1.com");
		Mockito.when(utilities.getPhoneAttribute()).thenReturn("phone");
		Mockito.when(utilities.getEmailAttribute()).thenReturn("email");
		Mockito.when(env.getProperty(ApiName.EMAILNOTIFIER.name())).thenReturn("https://int.mosip.io/template/email");
		Mockito.when(hotlistValidator.emailValidator(Mockito.anyString())).thenReturn(true);
		Mockito.when(hotlistValidator.phoneValidator(Mockito.anyString())).thenReturn(true);
		
		smsNotificationResponse = new ResponseWrapper<>();
		NotificationResponseDTO notificationResp = new NotificationResponseDTO();
		notificationResp.setMessage("Notification has been sent to provided contact details");
		notificationResp.setStatus("success");
		smsNotificationResponse.setResponse(notificationResp);
		Mockito.when(restClient.postApi(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(Class.class))).thenReturn(smsNotificationResponse);
		Mockito.doNothing().when(audit).setAuditRequestDto(Mockito.any());

	}

	@Test
	public void sendUinSmsEmailNotificationTest()
			throws ApisResourceAccessException, HotlistAppException, IOException {
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3527812406");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_UIN_BLOCK);
		reqDto.setIdType("UIN");
		reqDto.setAdditionalAttributes(additionalAttributes);
		
		NotificationResponseDTO response = notificationService.sendNotification(reqDto);
		assertEquals(SMS_EMAIL_SUCCESS, response.getMessage());

	}
	
	@Test
	public void sendVidSmsEmailNotificationTest()
			throws ApisResourceAccessException, HotlistAppException, IOException {
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3973146025865084");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_VID_BLOCK);
		reqDto.setIdType("VID");
		reqDto.setAdditionalAttributes(additionalAttributes);
		
		NotificationResponseDTO response = notificationService.sendNotification(reqDto);
		assertEquals(SMS_EMAIL_SUCCESS, response.getMessage());

	}

	@Test
	public void uinSmsFailedAndEmailSuccessTest() throws HotlistAppException, ApisResourceAccessException {
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);
		Mockito.when(hotlistValidator.phoneValidator(Mockito.anyString())).thenReturn(false);
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3527812406");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_UIN_BLOCK);
		reqDto.setIdType("UIN");
		reqDto.setAdditionalAttributes(additionalAttributes);
		
		NotificationResponseDTO response = notificationService.sendNotification(reqDto);
		assertEquals(EMAIL_SUCCESS, response.getMessage());

	}
	
	@Test
	public void vidSmsFailedAndEmailSuccessTest() throws HotlistAppException, ApisResourceAccessException {
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);
		Mockito.when(hotlistValidator.phoneValidator(Mockito.anyString())).thenReturn(false);
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3973146025865084");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_VID_BLOCK);
		reqDto.setIdType("VID");
		reqDto.setAdditionalAttributes(additionalAttributes);
		
		NotificationResponseDTO response = notificationService.sendNotification(reqDto);
		assertEquals(EMAIL_SUCCESS, response.getMessage());

	}

	@Test
	public void uinEmailFailedAndSMSSuccessTest() throws HotlistAppException, ApisResourceAccessException {
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);
		Mockito.when(hotlistValidator.emailValidator(Mockito.anyString())).thenReturn(false);
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3527812406");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_UIN_BLOCK);
		reqDto.setIdType("UIN");
		reqDto.setAdditionalAttributes(additionalAttributes);
		
		NotificationResponseDTO response = notificationService.sendNotification(reqDto);
		assertEquals(SMS_SUCCESS, response.getMessage());

	}
	
	@Test
	public void vidEmailFailedAndSMSSuccessTest() throws HotlistAppException, ApisResourceAccessException {
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);
		Mockito.when(hotlistValidator.emailValidator(Mockito.anyString())).thenReturn(false);
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3973146025865084");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_VID_BLOCK);
		reqDto.setIdType("VID");
		reqDto.setAdditionalAttributes(additionalAttributes);
		
		NotificationResponseDTO response = notificationService.sendNotification(reqDto);
		assertEquals(SMS_SUCCESS, response.getMessage());

	}
	
	@Test
	public void uinSMSSuccessTest() throws HotlistAppException, ApisResourceAccessException {
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);
		ReflectionTestUtils.setField(notificationService, "notificationType", "SMS");
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3527812406");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_UIN_BLOCK);
		reqDto.setIdType("UIN");
		reqDto.setAdditionalAttributes(additionalAttributes);
		
		NotificationResponseDTO response = notificationService.sendNotification(reqDto);
		assertEquals(SMS_SUCCESS, response.getMessage());

	}
	
	@Test
	public void uinEmailSuccessTest() throws HotlistAppException, ApisResourceAccessException {
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);
		ReflectionTestUtils.setField(notificationService, "notificationType", "EMAIL");
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3527812406");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_UIN_BLOCK);
		reqDto.setIdType("UIN");
		reqDto.setAdditionalAttributes(additionalAttributes);
		
		NotificationResponseDTO response = notificationService.sendNotification(reqDto);
		assertEquals(EMAIL_SUCCESS, response.getMessage());

	}

	@Test(expected = HotlistAppException.class)
	public void testUinNotificationFailure() throws Exception {
		ResponseWrapper<NotificationResponseDTO> smsNotificationResponse = new ResponseWrapper<>();
		NotificationResponseDTO notificationResp = new NotificationResponseDTO();
		notificationResp.setMessage("Notification failure");
		notificationResp.setStatus("failed");
		smsNotificationResponse.setResponse(notificationResp);
		Mockito.when(restClient.postApi(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(Class.class))).thenReturn(smsNotificationResponse);
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);

		reqDto = new NotificationRequestDto();
		reqDto.setId("3527812406");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_UIN_BLOCK);
		reqDto.setIdType("UIN");
		reqDto.setAdditionalAttributes(additionalAttributes);
		
		notificationService.sendNotification(reqDto);

	}
	
	@Test(expected = HotlistAppException.class)
	public void testVidNotificationFailure() throws Exception {
		ResponseWrapper<NotificationResponseDTO> smsNotificationResponse = new ResponseWrapper<>();
		NotificationResponseDTO notificationResp = new NotificationResponseDTO();
		notificationResp.setMessage("Notification failure");
		notificationResp.setStatus("failed");
		smsNotificationResponse.setResponse(notificationResp);
		Mockito.when(restClient.postApi(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(Class.class))).thenReturn(smsNotificationResponse);
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);

		reqDto = new NotificationRequestDto();
		reqDto.setId("3973146025865084");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_VID_BLOCK);
		reqDto.setIdType("VID");
		reqDto.setAdditionalAttributes(additionalAttributes);
		
		notificationService.sendNotification(reqDto);

	}

	@Test(expected = HotlistAppException.class)
	public void getTemplateNullResponseTest() throws ApisResourceAccessException, HotlistAppException {
		Mockito.when(restClient.getApi(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any(),
				Mockito.any(Class.class))).thenReturn(null);
		Mockito.when(hotlistValidator.emailValidator(Mockito.anyString())).thenReturn(false);
		Mockito.when(hotlistValidator.phoneValidator(Mockito.anyString())).thenReturn(false);
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3527812406");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_UIN_BLOCK);
		reqDto.setIdType("UIN");
		reqDto.setAdditionalAttributes(mailingAttributes);
		
		notificationService.sendNotification(reqDto);
	}

	@Test(expected = HotlistAppException.class)
	public void sendSMSClientException() throws ApisResourceAccessException, HotlistAppException {
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);
		HttpClientErrorException clientExp = new HttpClientErrorException(HttpStatus.BAD_GATEWAY);
		ApisResourceAccessException apiResourceAccessExp = new ApisResourceAccessException("BadGateway", clientExp);
		Mockito.when(restClient.postApi(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(Class.class))).thenThrow(apiResourceAccessExp);
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3527812406");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_UIN_BLOCK);
		reqDto.setIdType("UIN");
		reqDto.setAdditionalAttributes(mailingAttributes);
		
		notificationService.sendNotification(reqDto);

	}

	@Test(expected = HotlistAppException.class)
	public void sendSMSServerException() throws ApisResourceAccessException, HotlistAppException {
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);
		HttpServerErrorException serverExp = new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
		ApisResourceAccessException apiResourceAccessExp = new ApisResourceAccessException("BadGateway", serverExp);
		Mockito.when(restClient.postApi(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(Class.class))).thenThrow(apiResourceAccessExp);
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3527812406");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_UIN_BLOCK);
		reqDto.setIdType("UIN");
		reqDto.setAdditionalAttributes(mailingAttributes);
		
		notificationService.sendNotification(reqDto);
	}

	@Test(expected = HotlistAppException.class)
	public void sendSMSUnknownException() throws ApisResourceAccessException, HotlistAppException {
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);
		RuntimeException runTimeExp = new RuntimeException();
		ApisResourceAccessException apiResourceAccessExp = new ApisResourceAccessException("runtime exp", runTimeExp);
		Mockito.when(restClient.postApi(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(Class.class))).thenThrow(apiResourceAccessExp);
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3527812406");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_UIN_BLOCK);
		reqDto.setIdType("UIN");
		reqDto.setAdditionalAttributes(mailingAttributes);
		
		notificationService.sendNotification(reqDto);
	}

	@Test(expected = HotlistAppException.class)
	public void sendEmailClientException() throws ApisResourceAccessException, HotlistAppException {
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);
		HttpClientErrorException clientExp = new HttpClientErrorException(HttpStatus.BAD_GATEWAY);
		ApisResourceAccessException apiResourceAccessExp = new ApisResourceAccessException("BadGateway", clientExp);
		Mockito.when(restClient.postApi(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(Class.class))).thenReturn(smsNotificationResponse).thenThrow(apiResourceAccessExp);
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3527812406");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_UIN_BLOCK);
		reqDto.setIdType("UIN");
		reqDto.setAdditionalAttributes(mailingAttributes);
		
		notificationService.sendNotification(reqDto);
	}

	@Test(expected = HotlistAppException.class)
	public void sendEmailServerException() throws ApisResourceAccessException, HotlistAppException {
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);
		HttpServerErrorException serverExp = new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
		ApisResourceAccessException apiResourceAccessExp = new ApisResourceAccessException("BadGateway", serverExp);
		Mockito.when(restClient.postApi(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(Class.class))).thenReturn(smsNotificationResponse).thenThrow(apiResourceAccessExp);
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3527812406");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_UIN_BLOCK);
		reqDto.setIdType("UIN");
		reqDto.setAdditionalAttributes(mailingAttributes);
		
		notificationService.sendNotification(reqDto);
	}

	@Test(expected = HotlistAppException.class)
	public void sendEmailUnknownException() throws ApisResourceAccessException, HotlistAppException {
		Mockito.when(utility.getMailingAttributes(Mockito.any(), Mockito.any())).thenReturn(mailingAttributes);
		RuntimeException runTimeExp = new RuntimeException();
		ApisResourceAccessException apiResourceAccessExp = new ApisResourceAccessException("runtime exp", runTimeExp);
		Mockito.when(restClient.postApi(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(Class.class))).thenReturn(smsNotificationResponse).thenThrow(apiResourceAccessExp);
		
		reqDto = new NotificationRequestDto();
		reqDto.setId("3527812406");
		reqDto.setTemplateTypeCode(NotificationTemplateCode.HS_UIN_BLOCK);
		reqDto.setIdType("UIN");
		reqDto.setAdditionalAttributes(mailingAttributes);
		
		notificationService.sendNotification(reqDto);
	}

	
}
