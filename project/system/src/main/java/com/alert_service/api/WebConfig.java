package com.alert_service.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the React UI (served from another local origin, e.g. its dev server) call the history endpoints.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final ApiProperties properties;

	public WebConfig(ApiProperties properties) {
		this.properties = properties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
			.allowedOriginPatterns(properties.allowedOriginPatterns().toArray(String[]::new))
			.allowedMethods("GET");
	}
}
