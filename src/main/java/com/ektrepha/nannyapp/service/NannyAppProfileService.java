package com.ektrepha.nannyapp.service;

import java.util.List;

import com.ektrepha.nannyapp.dto.request.BankAccountRequest;
import com.ektrepha.nannyapp.dto.request.EmergencyContactRequest;
import com.ektrepha.nannyapp.dto.request.UpdateMeRequest;
import com.ektrepha.nannyapp.dto.response.BankAccountResponse;
import com.ektrepha.nannyapp.dto.response.DocumentResponse;
import com.ektrepha.nannyapp.dto.response.EmergencyContactResponse;
import com.ektrepha.nannyapp.dto.response.NannyMeResponse;
import com.ektrepha.nannyapp.dto.response.OnboardingResponse;
import com.ektrepha.nannyapp.dto.response.StatutoryResponse;
import com.ektrepha.nannyapp.dto.response.TrainingResponse;

/** Nanny staff app - the nanny's own profile, joining checklist and HR details. */
public interface NannyAppProfileService {

	NannyMeResponse me(Long userId);

	NannyMeResponse updateMe(Long userId, UpdateMeRequest request);

	OnboardingResponse onboarding(Long userId);

	List<DocumentResponse> documents(Long userId);

	BankAccountResponse bankAccount(Long userId);

	BankAccountResponse saveBankAccount(Long userId, BankAccountRequest request);

	StatutoryResponse statutory(Long userId);

	List<TrainingResponse> trainings(Long userId);

	EmergencyContactResponse emergencyContact(Long userId);

	EmergencyContactResponse saveEmergencyContact(Long userId, EmergencyContactRequest request);

}
