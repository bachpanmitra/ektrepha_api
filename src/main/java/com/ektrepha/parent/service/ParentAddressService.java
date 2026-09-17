package com.ektrepha.parent.service;

import java.util.List;

import com.ektrepha.parent.dto.request.AddressUpsertRequest;
import com.ektrepha.parent.dto.response.AddressResponse;

public interface ParentAddressService {

	List<AddressResponse> list(Long userId);

	AddressResponse create(Long userId, AddressUpsertRequest request);

	AddressResponse update(Long userId, Long addressId, AddressUpsertRequest request);

	void delete(Long userId, Long addressId);

	AddressResponse makePrimary(Long userId, Long addressId);

}
