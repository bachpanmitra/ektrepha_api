package com.ektrepha.nanny.service;

import com.ektrepha.nanny.dto.request.NannyEmergencyContactRequest;
import com.ektrepha.nanny.dto.request.NannyMeUpdateRequest;
import com.ektrepha.nanny.dto.response.NannyEmergencyContactResponse;
import com.ektrepha.nanny.dto.response.NannyMeResponse;

public interface NannyMeService {

	NannyMeResponse getMe(Long userId);

	NannyMeResponse updateMe(Long userId, NannyMeUpdateRequest request);

	NannyEmergencyContactResponse getEmergencyContact(Long userId);

	NannyEmergencyContactResponse saveEmergencyContact(Long userId, NannyEmergencyContactRequest request);

}
