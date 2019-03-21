/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Zipkin settings
 *
 * @author Spencer Gibb
 * @since 1.0.0
 */
@ConfigurationProperties("spring.zipkin")
public class ZipkinProperties {
	/** URL of the zipkin query server instance. */
	private String baseUrl = "http://localhost:9411/";
	private boolean enabled = true;
	private int flushInterval = 1;
	private Compression compression = new Compression();

	private Service service = new Service();

	private Locator locator = new Locator();

	public Locator getLocator() {
		return this.locator;
	}

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public int getFlushInterval() {
		return this.flushInterval;
	}

	public Compression getCompression() {
		return this.compression;
	}

	public Service getService() {
		return this.service;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setFlushInterval(int flushInterval) {
		this.flushInterval = flushInterval;
	}

	public void setCompression(Compression compression) {
		this.compression = compression;
	}

	public void setService(Service service) {
		this.service = service;
	}

	public void setLocator(Locator locator) {
		this.locator = locator;
	}

	/** When enabled, spans are gzipped before sent to the zipkin server */
	public static class Compression {

		private boolean enabled = false;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}

	/** When set will override the default {@code spring.application.name} value of the service id */
	public static class Service {

		/** The name of the service, from which the Span was sent via HTTP, that should appear in Zipkin */
		private String name;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static class Locator {

		private Discovery discovery;

		public Discovery getDiscovery() {
			return this.discovery;
		}

		public void setDiscovery(Discovery discovery) {
			this.discovery = discovery;
		}

		public static class Discovery {

			/** Enabling of locating the host name via service discovery */
			private boolean enabled;

			public boolean isEnabled() {
				return this.enabled;
			}

			public void setEnabled(boolean enabled) {
				this.enabled = enabled;
			}
		}
	}
}
