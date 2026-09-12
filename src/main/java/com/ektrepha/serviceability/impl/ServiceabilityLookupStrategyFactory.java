package com.ektrepha.serviceability.impl;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ektrepha.exception.InvalidSearchParametersException;
import com.ektrepha.serviceability.service.ServiceabilityLookupStrategy;
import com.ektrepha.serviceability.service.ServiceabilityLookupStrategy.SearchCriteria;

import lombok.RequiredArgsConstructor;

/**
 * Factory Method: Spring collects every {@link ServiceabilityLookupStrategy} bean into this list;
 * picking the one bean that applies to a given request is this factory's only job, so
 * {@code ServiceabilitySearchServiceImpl} never has to know how many search modes exist.
 * Precedence when a caller supplies more than one (pincode &gt; coordinates &gt; query &gt;
 * city+state) matches the order the API doc lists them in.
 */
@Component
@RequiredArgsConstructor
public class ServiceabilityLookupStrategyFactory {

	private final List<ServiceabilityLookupStrategy> strategies;

	public ServiceabilityLookupStrategy resolve(SearchCriteria criteria) {
		return strategies.stream()
				.filter(strategy -> strategy.supports(criteria))
				.findFirst()
				.orElseThrow(() -> new InvalidSearchParametersException(
						"Provide one of: pincode, query, lat & lng, or city & state"));
	}

}
