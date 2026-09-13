package com.ektrepha.waitlistsignup.service;

import com.ektrepha.waitlistsignup.dto.request.WaitlistSignupRequest;
import com.ektrepha.waitlistsignup.dto.response.WaitlistSignupResponse;

public interface WaitlistSignupService {

	WaitlistSignupResponse join(WaitlistSignupRequest request);

}
