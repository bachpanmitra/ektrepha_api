package com.ektrepha.caremanager.impl;

import org.springframework.stereotype.Component;

import com.ektrepha.caremanager.CmPersonRefMapper;
import com.ektrepha.caremanager.CmPhoneMasker;
import com.ektrepha.caremanager.dto.response.NannyListItemResponse;
import com.ektrepha.caremanager.service.CmNannyMapper;
import com.ektrepha.model.Nanny;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CmNannyMapperImpl implements CmNannyMapper {

	private final ReviewRepository reviewRepository;
	private final BookingRepository bookingRepository;

	@Override
	public NannyListItemResponse map(Nanny nanny) {
		Double avgRating = null;
		var aggregate = reviewRepository.findRatingAggregate(nanny.getId());
		if (!aggregate.isEmpty() && aggregate.get(0)[0] != null) {
			avgRating = (Double) aggregate.get(0)[0];
		}
		long lowRatingsCount = reviewRepository.countByNannyIdAndRatingLessThanEqual(nanny.getId(), (short) 3);
		long familiesCount = bookingRepository.countDistinctParentsByNannyId(nanny.getId());

		return new NannyListItemResponse(
				nanny.getPublicId(),
				(nanny.getFirstName() + " " + nanny.getLastName()).trim(),
				CmPhoneMasker.mask(nanny.getUser().getPhone()),
				nanny.getStatus(),
				avgRating,
				lowRatingsCount,
				familiesCount,
				nanny.getCareManager() == null ? null : CmPersonRefMapper.of(nanny.getCareManager()));
	}

}
