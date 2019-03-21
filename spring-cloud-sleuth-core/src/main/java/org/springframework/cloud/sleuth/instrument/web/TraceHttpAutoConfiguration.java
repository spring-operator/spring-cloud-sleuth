/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.sleuth.instrument.web;

import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * related to HTTP based communication.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.12
 */
@Configuration
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
@EnableConfigurationProperties({ TraceKeys.class, SleuthWebProperties.class })
public class TraceHttpAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public HttpTraceKeysInjector httpTraceKeysInjector(Tracer tracer, TraceKeys traceKeys) {
		return new HttpTraceKeysInjector(tracer, traceKeys);
	}

	@Bean
	@ConditionalOnMissingBean
	public HttpSpanExtractor httpSpanExtractor(SleuthWebProperties sleuthWebProperties) {
		return new ZipkinHttpSpanExtractor(Pattern.compile(sleuthWebProperties.getSkipPattern()));
	}

	@Bean
	@ConditionalOnMissingBean
	public HttpSpanInjector httpSpanInjector() {
		return new ZipkinHttpSpanInjector();
	}
}
