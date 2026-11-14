package com.ektrepha.caremanager;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** No {@code Clock} bean exists app-wide — this feature's task-due-time math (CmTaskEventProcessor) needs one injectable for deterministic tests. */
@Configuration
public class CareManagerConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
