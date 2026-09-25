package com.ektrepha.parent.service;

import com.ektrepha.parent.dto.response.AddressResponse;
import com.ektrepha.parent.dto.response.CareLocationResponse;

/** Resolves/selects which saved address a parent's care requests should default to - see {@link com.ektrepha.parent.controller.CareLocationController}. */
public interface CareLocationService {

	CareLocationResponse resolve(Long userId);

	AddressResponse select(Long userId, Long addressId);

}
