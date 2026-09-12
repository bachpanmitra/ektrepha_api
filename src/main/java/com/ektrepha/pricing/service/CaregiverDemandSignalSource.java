package com.ektrepha.pricing.service;

public interface CaregiverDemandSignalSource {

	/** Count of still-open booking requests for a zone x service-type, for the demand-ratio recompute. */
	int countOpenBookingRequests(Long zoneAreaId, Long serviceTypeId, String serviceTypeCode);

}
