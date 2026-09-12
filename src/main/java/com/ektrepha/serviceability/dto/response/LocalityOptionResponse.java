package com.ektrepha.serviceability.dto.response;

/** One typeahead match for the "select your area" search box - {@code live} lets the client gray out areas that aren't serviceable yet, the way quick-commerce apps show "coming soon" areas alongside live ones. */
public record LocalityOptionResponse(
		Long zoneAreaId,
		String name,
		String city,
		String state,
		boolean live) {
}
