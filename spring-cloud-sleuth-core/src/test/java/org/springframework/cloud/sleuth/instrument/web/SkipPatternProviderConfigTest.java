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

import org.junit.Test;
import org.springframework.boot.actuate.autoconfigure.ManagementServerProperties;
import org.springframework.cloud.sleuth.instrument.web.TraceHttpAutoConfiguration.SkipPatternProviderConfig;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class SkipPatternProviderConfigTest {

	@Test
	public void should_combine_skip_pattern_and_management_context_when_they_are_both_not_empty() throws Exception {
		SleuthWebProperties sleuthWebProperties = new SleuthWebProperties();
		sleuthWebProperties.setSkipPattern("foo.*|bar.*");
		Pattern pattern = SkipPatternProviderConfig.getPatternForManagementServerProperties(
				managementServerPropertiesWithContextPath(), sleuthWebProperties);

		then(pattern.pattern()).isEqualTo("foo.*|bar.*|/management/context.*");
	}

	@Test
	public void should_pick_skip_pattern_when_its_not_empty_and_management_context_is_empty() throws Exception {
		SleuthWebProperties sleuthWebProperties = new SleuthWebProperties();
		sleuthWebProperties.setSkipPattern("foo.*|bar.*");

		Pattern pattern = SkipPatternProviderConfig.getPatternForManagementServerProperties(new ManagementServerProperties(), sleuthWebProperties);

		then(pattern.pattern()).isEqualTo("foo.*|bar.*");
	}

	@Test
	public void should_pick_management_context_when_skip_patterns_is_empty_and_context_path_is_not() throws Exception {
		SleuthWebProperties sleuthWebProperties = new SleuthWebProperties();
		sleuthWebProperties.setSkipPattern("");

		Pattern pattern = SkipPatternProviderConfig.getPatternForManagementServerProperties(
				managementServerPropertiesWithContextPath(), sleuthWebProperties);

		then(pattern.pattern()).isEqualTo("/management/context.*");
	}

	@Test
	public void should_pick_default_pattern_when_both_management_context_and_skip_patterns_are_empty() throws Exception {
		SleuthWebProperties sleuthWebProperties = new SleuthWebProperties();
		sleuthWebProperties.setSkipPattern("");

		Pattern pattern = SkipPatternProviderConfig.getPatternForManagementServerProperties(new ManagementServerProperties(), sleuthWebProperties);

		then(pattern.pattern()).isEqualTo(SleuthWebProperties.DEFAULT_SKIP_PATTERN);
	}

	private ManagementServerProperties managementServerPropertiesWithContextPath() {
		ManagementServerProperties managementServerProperties = new ManagementServerProperties();
		managementServerProperties.setContextPath("/management/context");
		return managementServerProperties;
	}
}