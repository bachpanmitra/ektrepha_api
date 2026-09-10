package com.ektrepha.nannysearch.service;

import java.util.List;

import com.ektrepha.nannysearch.dto.request.NannySearchRequest;
import com.ektrepha.nannysearch.dto.response.LanguageOptionResponse;
import com.ektrepha.nannysearch.dto.response.NannySearchResponse;
import com.ektrepha.nannysearch.dto.response.SkillOptionResponse;

public interface NannySearchService {

	/** Runs a filtered, geo-bounded, ranked nanny search for the given authenticated user (a parent). */
	NannySearchResponse search(Long userId, NannySearchRequest request);

	/** Lists active catalog languages, for populating the search form's language filter. */
	List<LanguageOptionResponse> listActiveLanguages();

	/** Lists the skill catalog, for populating the search form's skills filter. */
	List<SkillOptionResponse> listSkills();

}
