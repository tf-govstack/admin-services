package io.mosip.hotlist.service;

import io.mosip.hotlist.dto.HotlistRequestResponseDTO;
import io.mosip.hotlist.exception.ApisResourceAccessException;
import io.mosip.hotlist.exception.HotlistAppException;

/**
 * The Interface HotlistService.
 *
 * @author Manoj SP
 */
public interface HotlistService {

	/**
	 * Block.
	 *
	 * @param blockRequest the block request
	 * @return the hotlist request response DTO
	 * @throws HotlistAppException the hotlist app exception
	 * @throws ApisResourceAccessException 
	 */
	public HotlistRequestResponseDTO block(HotlistRequestResponseDTO blockRequest) throws HotlistAppException, ApisResourceAccessException;

	/**
	 * Retrieve hotlist.
	 *
	 * @param id the id
	 * @param idType the id type
	 * @return the hotlist request response DTO
	 * @throws HotlistAppException the hotlist app exception
	 */
	public HotlistRequestResponseDTO retrieveHotlist(String id, String idType) throws HotlistAppException;

	/**
	 * Update hotlist.
	 *
	 * @param blockRequest the block request
	 * @return the hotlist request response DTO
	 * @throws HotlistAppException the hotlist app exception
	 * @throws ApisResourceAccessException 
	 */
	public HotlistRequestResponseDTO unblock(HotlistRequestResponseDTO unblockRequest) throws HotlistAppException, ApisResourceAccessException;
}
