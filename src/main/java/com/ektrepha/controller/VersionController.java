package com.ektrepha.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VersionController {

	@Autowired(required = false)
	@Nullable
	private BuildProperties buildProperties;

	@GetMapping("/api/version")
	public ResponseEntity<Map<String, Object>> version() {
		if (buildProperties == null) {
			return ResponseEntity.ok(Map.of("version", "unknown"));
		}
		return ResponseEntity.ok(Map.of(
				"version", buildProperties.getVersion(),
				"buildTime", buildProperties.getTime().toString(),
				"artifact", buildProperties.getArtifact()));
	}

}
