package com.ektrepha.caremanager.service;

import java.util.Optional;

import com.ektrepha.model.CmArea;
import com.ektrepha.model.Parent;

/**
 * Resolves which Care Manager coverage area (BROOKEFIELD/WHITEFIELD) a parent is in, from their
 * address pincode (migration 054's reference data). Empty when the parent has no address yet, or
 * their pincode isn't in either known area — callers must not guess an area in that case
 * (business decision: skip auto-assignment rather than risk assigning the wrong CM).
 */
public interface CmAreaResolver {

	Optional<CmArea> resolve(Parent parent);

}
