package io.mosip.hotlist.util;

import io.mosip.kernel.core.exception.ServiceError;
import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.StringUtils;
//import io.mosip.hotlist.util.Serializable;
import io.mosip.hotlist.logger.HotlistLogger;
import io.mosip.hotlist.constant.ApiName;
import io.mosip.hotlist.constant.LoggerFileConstant;
import io.mosip.hotlist.constant.MappingJsonConstants;
import io.mosip.hotlist.constant.HotlistErrorConstants;
import io.mosip.hotlist.constant.LoggerFileConstant;
import io.mosip.hotlist.dto.IdRepoResponseDto;
import io.mosip.hotlist.dto.JsonValue;
import io.mosip.hotlist.exception.ApisResourceAccessException;
import io.mosip.hotlist.exception.HotlistAppException;
import org.assertj.core.util.Lists;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.mvel2.MVEL;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;
import com.nimbusds.jose.util.IOUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * @author Girish Yarru
 * @version 1.0
 */

@Component
public class Utility {

	private static final Logger logger = HotlistLogger.getLogger(Utility.class);

	@Autowired
	private HotlistServiceRestClient residentServiceRestClient;

	@Value("${config.server.file.storage.uri}")
	private String configServerFileStorageURL;

    @Value("${registration.processor.identityjson}")
	private String residentIdentityJson;
    
    @Autowired(required = true)
	@Qualifier("varres")
	private VariableResolverFactory functionFactory;
    
    @Value("${hotlist.email.mask.function}")
	private String emailMaskFunction;
	
	@Value("${hotlist.phone.mask.function}")
	private String phoneMaskFunction;
    
    @Value("${hotlist.data.mask.function}")
	private String maskingFunction;


	@Autowired
	@Qualifier("selfTokenRestTemplate")
	private RestTemplate residentRestTemplate;

	@Autowired
	private Environment env;

	private static final String IDENTITY = "identity";
	private static final String VALUE = "value";
	private static String regProcessorIdentityJson = "";

    @PostConstruct
    private void loadRegProcessorIdentityJson() {
        regProcessorIdentityJson = residentRestTemplate.getForObject(configServerFileStorageURL + residentIdentityJson, String.class);
        logger.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.APPLICATIONID.toString(),
                LoggerFileConstant.APPLICATIONID.toString(), "loadRegProcessorIdentityJson completed successfully");
    }

	@SuppressWarnings("unchecked")
	public JSONObject retrieveIdrepoJson(String id) throws HotlistAppException {
		logger.debug(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), id,
				"Utilitiy::retrieveIdrepoJson()::entry");
		List<String> pathsegments = new ArrayList<>();
		pathsegments.add(id);
		ResponseWrapper<IdRepoResponseDto> response = null;
		try {
				response = (ResponseWrapper<IdRepoResponseDto>) residentServiceRestClient.getApi(
						ApiName.IDREPOGETIDBYUIN, pathsegments, "", null, ResponseWrapper.class);

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
		
		return retrieveErrorCode(response, id);
	}
	
	public JSONObject retrieveErrorCode(ResponseWrapper<IdRepoResponseDto> response, String id)
			throws HotlistAppException {
		HotlistErrorConstants errorCode;
		errorCode = HotlistErrorConstants.INVALID_ID;
		try {
			if (response == null)
				throw new HotlistAppException(errorCode.getErrorCode(), errorCode.getErrorMessage(),
						"In valid response while requesting ID Repositary");
			if (!response.getErrors().isEmpty()) {
				List<ServiceError> error = response.getErrors();
				throw new HotlistAppException(errorCode.getErrorCode(), errorCode.getErrorMessage(),
						error.get(0).getMessage());
			}

			String jsonResponse = JsonUtil.writeValueAsString(response.getResponse());
			JSONObject json = JsonUtil.readValue(jsonResponse, JSONObject.class);
			logger.debug(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), id,
					"Utilitiy::retrieveIdrepoJson()::exit");
			return JsonUtil.getJSONObject(json, "identity");
		} catch (IOException e) {
			throw new HotlistAppException(HotlistErrorConstants.HOTLIST_SYS_EXCEPTION.getErrorCode(),
					HotlistErrorConstants.HOTLIST_SYS_EXCEPTION.getErrorMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> getMailingAttributes(String id, Set<String> templateLangauges)
			throws HotlistAppException {
		logger.debug(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), id,
				"Utilitiy::getMailingAttributes()::entry");
		Map<String, Object> attributes = new HashMap<>();
		String mappingJsonString = getMappingJson();
		if(mappingJsonString==null || mappingJsonString.trim().isEmpty()) {
			throw new HotlistAppException(HotlistErrorConstants.JSON_PROCESSING_EXCEPTION.getErrorCode(),
					HotlistErrorConstants.JSON_PROCESSING_EXCEPTION.getErrorMessage() );
		}
		JSONObject mappingJsonObject;
		try {
			JSONObject demographicIdentity = retrieveIdrepoJson(id);
			mappingJsonObject = JsonUtil.readValue(mappingJsonString, JSONObject.class);
			JSONObject mapperIdentity = JsonUtil.getJSONObject(mappingJsonObject, IDENTITY);
			List<String> mapperJsonKeys = new ArrayList<>(mapperIdentity.keySet());

			String preferredLanguage = getPreferredLanguage(demographicIdentity);
			if (StringUtils.isBlank(preferredLanguage)) {
				List<String> defaultTemplateLanguages = getDefaultTemplateLanguages();
				if (CollectionUtils.isEmpty(defaultTemplateLanguages)) {
					Set<String> dataCapturedLanguages = getDataCapturedLanguages(mapperIdentity, demographicIdentity);
					templateLangauges.addAll(dataCapturedLanguages);
				} else {
					templateLangauges.addAll(defaultTemplateLanguages);
				}
			} else {
				templateLangauges.add(preferredLanguage);
			}

			for (String key : mapperJsonKeys) {
				LinkedHashMap<String, String> jsonObject = JsonUtil.getJSONValue(mapperIdentity, key);
				String values = jsonObject.get(VALUE);
				for (String value : values.split(",")) {
					Object object = demographicIdentity.get(value);
					if (object instanceof ArrayList) {
						JSONArray node = JsonUtil.getJSONArray(demographicIdentity, value);
						JsonValue[] jsonValues = JsonUtil.mapJsonNodeToJavaObject(JsonValue.class, node);
						for (JsonValue jsonValue : jsonValues) {
							if (templateLangauges.contains(jsonValue.getLanguage()))
								attributes.put(value + "_" + jsonValue.getLanguage(), jsonValue.getValue());
						}
					} else if (object instanceof LinkedHashMap) {
						JSONObject json = JsonUtil.getJSONObject(demographicIdentity, value);
						attributes.put(value, (String) json.get(VALUE));
					} else {
						attributes.put(value, String.valueOf(object));
					}
				}
			}
		} catch (IOException | ReflectiveOperationException e) {
			throw new HotlistAppException(HotlistErrorConstants.HOTLIST_SYS_EXCEPTION.getErrorCode(),
					HotlistErrorConstants.HOTLIST_SYS_EXCEPTION.getErrorMessage(), e);
		}
		logger.debug(LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.UIN.name(), id,
				"Utilitiy::getMailingAttributes()::exit");
		return attributes;
	}

	private String getPreferredLanguage(JSONObject demographicIdentity) {
		String preferredLang = null;
		String preferredLangAttribute = env.getProperty("mosip.default.user-preferred-language-attribute");
		if (!StringUtils.isBlank(preferredLangAttribute)) {
			Object object = demographicIdentity.get(preferredLangAttribute);
			if(object!=null) {
				preferredLang = String.valueOf(object);
			}
		}
		return preferredLang;
	}

	private Set<String> getDataCapturedLanguages(JSONObject mapperIdentity, JSONObject demographicIdentity)
			throws ReflectiveOperationException {
		Set<String> dataCapturedLangauges = new HashSet<String>();
		LinkedHashMap<String, String> jsonObject = JsonUtil.getJSONValue(mapperIdentity, MappingJsonConstants.NAME);
		String values = jsonObject.get(VALUE);
		for (String value : values.split(",")) {
			Object object = demographicIdentity.get(value);
			if (object instanceof ArrayList) {
				JSONArray node = JsonUtil.getJSONArray(demographicIdentity, value);
				JsonValue[] jsonValues = JsonUtil.mapJsonNodeToJavaObject(JsonValue.class, node);
				for (JsonValue jsonValue : jsonValues) {
					dataCapturedLangauges.add(jsonValue.getLanguage());
				}
			}
		}
		return dataCapturedLangauges;
	}

	private List<String> getDefaultTemplateLanguages() {
		String defaultLanguages = env.getProperty("mosip.default.template-languages");
		if (!StringUtils.isBlank(defaultLanguages)) {
			String[] lanaguages = defaultLanguages.split(",");
			List<String> strList = Lists.newArrayList(lanaguages);
			return strList;
		}
		return null;
	}

    public String getMappingJson() {
        if (StringUtils.isBlank(regProcessorIdentityJson)) {
            return residentRestTemplate.getForObject(configServerFileStorageURL + residentIdentityJson, String.class);
        }
        return regProcessorIdentityJson;
    }

	public String maskData(Object object, String maskingFunctionName) {
		Map context = new HashMap();
		context.put("value", String.valueOf(object));
		VariableResolverFactory myVarFactory = new MapVariableResolverFactory(context);
		myVarFactory.setNextFactory(functionFactory);
		Serializable serializable = MVEL.compileExpression(maskingFunctionName + "(value);");
		String formattedObject = MVEL.executeExpression(serializable, context, myVarFactory, String.class);
		return formattedObject;
	}
	
	public String maskEmail(String email) {
		return maskData(email, emailMaskFunction);
	}

	public String maskPhone(String phone) {
		return maskData(phone, phoneMaskFunction);
	}
	
	public String convertToMaskDataFormat(String maskData) {
		return maskData(maskData, maskingFunction);
	}
	
	/**
	 * Read resource content.
	 *
	 * @param resFile the res file
	 * @return the string
	 * @throws HotlistAppException 
	 */
	public static String readResourceContent(Resource resFile) throws HotlistAppException {
		try {
			return IOUtils.readInputStreamToString(resFile.getInputStream(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			logger.error(e.getMessage());
			throw new HotlistAppException(HotlistErrorConstants.API_RESOURCE_ACCESS_EXCEPTION, e);
		}
	}

}
