package io.mosip.hotlist.service.impl;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.hotlist.constant.HotlistErrorConstants;
import io.mosip.hotlist.dto.HotlistRequestResponseDTO;
import io.mosip.hotlist.entity.Hotlist;
import io.mosip.hotlist.entity.HotlistHistory;
import io.mosip.hotlist.event.HotlistEventHandler;
import io.mosip.hotlist.exception.ApisResourceAccessException;
import io.mosip.hotlist.exception.HotlistAppException;
import io.mosip.hotlist.logger.HotlistLogger;
import io.mosip.hotlist.repository.HotlistHistoryRepository;
import io.mosip.hotlist.repository.HotlistRepository;
import io.mosip.hotlist.security.HotlistSecurityManager;
import io.mosip.hotlist.service.HotlistService;
import io.mosip.kernel.core.hotlist.constant.HotlistStatus;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.hotlist.service.NotificationService;
import io.mosip.hotlist.constant.NotificationTemplateCode;
import io.mosip.hotlist.dto.NotificationRequestDto;

/**
 * The Class HotlistServiceImpl.
 *
 * @author Manoj SP
 */
@Service
@Transactional
public class HotlistServiceImpl implements HotlistService {

	/** The Constant RETRIEVE_HOTLIST. */
	private static final String RETRIEVE_HOTLIST = "retrieveHotlist";

	/** The Constant BLOCK. */
	private static final String BLOCK = "block";

	/** The Constant HOTLIST_SERVICE_IMPL. */
	private static final String HOTLIST_SERVICE_IMPL = "HotlistServiceImpl";

	/** The mosip logger. */
	private static Logger mosipLogger = HotlistLogger.getLogger(HotlistServiceImpl.class);

	/** The topic. */
	@Value("${mosip.hotlist.topic-to-publish}")
	private String topic;

	/** The web sub hub url. */
	@Value("${websub.publish.url}")
	private String webSubHubUrl;

	/** The app id. */
	@Value("${spring.application.name:HOTLIST}")
	private String appId;
	
	/** Send notification flag */
	@Value("${hotlist.notification.send:NO}")
	private String sendNotification;

	/** The hotlist repo. */
	@Autowired
	private HotlistRepository hotlistRepo;

	/** The hotlist H repo. */
	@Autowired
	private HotlistHistoryRepository hotlistHRepo;

	/** The mapper. */
	@Autowired
	private ObjectMapper mapper;

	/** The event handler. */
	@Autowired
	private HotlistEventHandler eventHandler;
	
	 @Autowired
	 private NotificationService notificationService;

	/**
	 * Block.
	 *
	 * @param blockRequest the block request
	 * @return the hotlist request response DTO
	 * @throws HotlistAppException the hotlist app exception
	 * @throws ApisResourceAccessException 
	 */
	@Override
	public HotlistRequestResponseDTO block(HotlistRequestResponseDTO blockRequest) throws HotlistAppException, ApisResourceAccessException {
		try {
			String idHash = HotlistSecurityManager.hash(blockRequest.getId().getBytes());
			Optional<Hotlist> hotlistedOptionalData = hotlistRepo.findByIdHashAndIdTypeAndIsDeleted(idHash,
					blockRequest.getIdType(), false);
			String dbStatus = HotlistStatus.UNBLOCKED;
			String requestedStatus = HotlistStatus.BLOCKED;
			String description = blockRequest.getDescription();
			LocalDateTime expiryTimestamp = blockRequest.getExpiryTimestamp();
			if (hotlistedOptionalData.isPresent()) {
				updateStatus(blockRequest, idHash, hotlistedOptionalData, dbStatus, requestedStatus);
			} else {
				String status = Objects.nonNull(expiryTimestamp) ? dbStatus : requestedStatus;
				Hotlist hotlist = new Hotlist();
				buildHotlistEntity(blockRequest, idHash, status, hotlist);
				hotlist.setCreatedBy(HotlistSecurityManager.getUser());
				hotlist.setCreatedDateTime(DateUtils.getUTCCurrentDateTime());
				hotlist.setIsDeleted(false);
				hotlistHRepo.save(mapper.convertValue(hotlist, HotlistHistory.class));
				hotlistRepo.save(hotlist);
				eventHandler.publishEvent(idHash, blockRequest.getIdType(), status, hotlist.getExpiryTimestamp(), description);
			}
			
			if(sendNotification.equalsIgnoreCase("YES")) {
				NotificationRequestDto notificationRequestDto = new NotificationRequestDto();			
				NotificationTemplateCode templateTypeCode = NotificationTemplateCode.HS_UIN_BLOCK;
				
				if(blockRequest.getIdType().equalsIgnoreCase("UIN")) {
					templateTypeCode = NotificationTemplateCode.HS_UIN_BLOCK;
				}
				
				if(blockRequest.getIdType().equalsIgnoreCase("VID")) {
					templateTypeCode = NotificationTemplateCode.HS_VID_BLOCK;
				}
				
				notificationRequestDto.setId(blockRequest.getId());
				notificationRequestDto.setIdType(blockRequest.getIdType());
				notificationRequestDto.setTemplateTypeCode(templateTypeCode);
				notificationService.sendNotification(notificationRequestDto);
			}
			
			return buildResponse(blockRequest.getId(), null, requestedStatus, description, isExpired(expiryTimestamp));
		} catch (DataAccessException | TransactionException e) {
			mosipLogger.error(HotlistSecurityManager.getUser(), HOTLIST_SERVICE_IMPL, BLOCK, e.getMessage());
			throw new HotlistAppException(HotlistErrorConstants.DATABASE_ACCESS_ERROR, e);
		}
	}

	/**
	 * Retrieve hotlist.
	 *
	 * @param id     the id
	 * @param idType the id type
	 * @return the hotlist request response DTO
	 * @throws HotlistAppException the hotlist app exception
	 */
	@Override
	public HotlistRequestResponseDTO retrieveHotlist(String id, String idType) throws HotlistAppException {
		try {
			Optional<Hotlist> hotlistedOptionalData = hotlistRepo
					.findByIdHashAndIdTypeAndIsDeleted(HotlistSecurityManager.hash(id.getBytes()), idType, false);
			if (hotlistedOptionalData.isPresent()) {
				Hotlist hotlistedData = hotlistedOptionalData.get();
				String status = hotlistedData.getStatus();
				String description = hotlistedData.getDescription();
				if (Objects.nonNull(isExpired(hotlistedData.getExpiryTimestamp()))) {
					switch (status) {
					case HotlistStatus.BLOCKED:
						status = HotlistStatus.UNBLOCKED;
						break;
					case HotlistStatus.UNBLOCKED:
						status = HotlistStatus.BLOCKED;
						break;
					}
					return buildResponse(id, idType, status, description, hotlistedData.getExpiryTimestamp());
				}
				return buildResponse(id, idType, status, description, null);
			} else {
				return buildResponse(id, idType, HotlistStatus.UNBLOCKED, null, null);
			}
		} catch (DataAccessException | TransactionException e) {
			mosipLogger.error(HotlistSecurityManager.getUser(), HOTLIST_SERVICE_IMPL, RETRIEVE_HOTLIST, e.getMessage());
			throw new HotlistAppException(HotlistErrorConstants.DATABASE_ACCESS_ERROR, e);
		}
	}

	/**
	 * Update hotlist.
	 *
	 * @param unblockRequest the update request
	 * @return the hotlist request response DTO
	 * @throws HotlistAppException the hotlist app exception
	 * @throws ApisResourceAccessException 
	 */
	@Override
	public HotlistRequestResponseDTO unblock(HotlistRequestResponseDTO unblockRequest) throws HotlistAppException, ApisResourceAccessException {
		try {
			String idHash = HotlistSecurityManager.hash(unblockRequest.getId().getBytes());
			Optional<Hotlist> hotlistedOptionalData = hotlistRepo.findByIdHashAndIdTypeAndIsDeleted(idHash,
					unblockRequest.getIdType(), false);
			String dbStatus = HotlistStatus.BLOCKED;
			String requestedStatus = HotlistStatus.UNBLOCKED;
			if (hotlistedOptionalData.isPresent()) {
				updateStatus(unblockRequest, idHash, hotlistedOptionalData, dbStatus, requestedStatus);
			}
			
			if(sendNotification.equalsIgnoreCase("YES")) {

				NotificationRequestDto notificationRequestDto = new NotificationRequestDto();
				NotificationTemplateCode templateTypeCode = NotificationTemplateCode.HS_UIN_UNBLOCK;
				
				if(unblockRequest.getIdType().equalsIgnoreCase("UIN")) {
					templateTypeCode = NotificationTemplateCode.HS_UIN_UNBLOCK;
				}
				
				if(unblockRequest.getIdType().equalsIgnoreCase("VID")) {
					templateTypeCode = NotificationTemplateCode.HS_VID_UNBLOCK;
				}
				
				notificationRequestDto.setId(unblockRequest.getId());
				notificationRequestDto.setIdType(unblockRequest.getIdType());
				notificationRequestDto.setTemplateTypeCode(templateTypeCode);
				notificationService.sendNotification(notificationRequestDto);
			}
			
			return buildResponse(unblockRequest.getId(), null, requestedStatus, unblockRequest.getDescription(), isExpired(unblockRequest.getExpiryTimestamp()));
		} catch (DataAccessException | TransactionException e) {
			mosipLogger.error(HotlistSecurityManager.getUser(), HOTLIST_SERVICE_IMPL, "unblock", e.getMessage());
			throw new HotlistAppException(HotlistErrorConstants.DATABASE_ACCESS_ERROR, e);
		}
	}

	private void updateStatus(HotlistRequestResponseDTO request, String idHash, Optional<Hotlist> hotlistedOptionalData,
			String dbStatus, String requestedStatus) {
		if (hotlistedOptionalData.isPresent() && hotlistedOptionalData.get().getStatus().contentEquals(dbStatus)) {
			updateHotlist(request, idHash, Objects.nonNull(request.getExpiryTimestamp()) ? dbStatus : requestedStatus,
					hotlistedOptionalData);
		} else {
			request.setExpiryTimestamp(null);
			updateHotlist(request, idHash, requestedStatus, hotlistedOptionalData);
		}
	}

	/**
	 * Update hotlist.
	 *
	 * @param updateRequest         the update request
	 * @param idHash                the id hash
	 * @param status                the status
	 * @param hotlistedOptionalData the hotlisted optional data
	 * @return the hotlist request response DTO
	 */
	private HotlistRequestResponseDTO updateHotlist(HotlistRequestResponseDTO updateRequest, String idHash, String status,
			Optional<Hotlist> hotlistedOptionalData) {
		if(!hotlistedOptionalData.isPresent())
			return null;
		Hotlist hotlist = hotlistedOptionalData.get();
		hotlistHRepo.save(mapper.convertValue(hotlist, HotlistHistory.class));
		buildHotlistEntity(updateRequest, idHash, status, hotlist);
		hotlist.setUpdatedBy(HotlistSecurityManager.getUser());
		hotlist.setUpdatedDateTime(DateUtils.getUTCCurrentDateTime());
		hotlistRepo.save(hotlist);
		eventHandler.publishEvent(idHash, updateRequest.getIdType(), status, hotlist.getExpiryTimestamp(), updateRequest.getDescription());
		return buildResponse(hotlist.getIdValue(), null, updateRequest.getStatus(), updateRequest.getDescription(), null);
	}

	/**
	 * Builds the hotlist entity.
	 *
	 * @param request the request
	 * @param idHash  the id hash
	 * @param status  the status
	 * @param hotlist the hotlist
	 */
	private void buildHotlistEntity(HotlistRequestResponseDTO request, String idHash, String status, Hotlist hotlist) {
		hotlist.setIdHash(idHash);
		hotlist.setIdValue(request.getId());
		hotlist.setIdType(request.getIdType());
		hotlist.setStatus(status);
		hotlist.setDescription(request.getDescription());
		hotlist.setStartTimestamp(DateUtils.getUTCCurrentDateTime());
		hotlist.setExpiryTimestamp(Objects.nonNull(request.getExpiryTimestamp()) ? request.getExpiryTimestamp() : null);
	}

	private LocalDateTime isExpired(LocalDateTime expiryTimestamp) {
		return Objects.nonNull(expiryTimestamp) && expiryTimestamp.isAfter(DateUtils.getUTCCurrentDateTime()) ? expiryTimestamp
				: null;
	}

	/**
	 * Builds the response.
	 *
	 * @param id              the id
	 * @param idType          the id type
	 * @param status          the status
	 * @param expiryTimestamp the expiry timestamp
	 * @return the hotlist request response DTO
	 */
	private HotlistRequestResponseDTO buildResponse(String id, String idType, String status, String description, LocalDateTime expiryTimestamp) {
		HotlistRequestResponseDTO response = new HotlistRequestResponseDTO();
		response.setId(id);
		response.setIdType(idType);
		response.setStatus(status);
		response.setDescription(description);
		response.setExpiryTimestamp(expiryTimestamp);
		return response;
	}

}