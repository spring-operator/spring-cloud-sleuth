/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Test;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;

/**
 * @author Marcin Grzejszczak
 */
public class HeaderBasedMessagingExtractorTests {

	@Test
	public void overridesTheSampleFlagWithSpanFlagForSampledScenario() {
		HeaderBasedMessagingExtractor extractor = new HeaderBasedMessagingExtractor();
		SpanTextMap spanTextMap = spanTextMap();
		spanTextMap.put(TraceMessageHeaders.SPAN_ID_NAME, Span.idToHex(10L));
		spanTextMap.put(TraceMessageHeaders.TRACE_ID_NAME, Span.idToHex(20L));
		spanTextMap.put(TraceMessageHeaders.SAMPLED_NAME, "0");
		spanTextMap.put(TraceMessageHeaders.SPAN_FLAGS_NAME, "1");

		Span span = extractor.joinTrace(spanTextMap);

		then(span).isExportable().isShared();
	}

	@Test
	public void doesNotOverrideTheSampleHeaderWithSpanFlagWhenTheSpanFlagIsNot1() {
		HeaderBasedMessagingExtractor extractor = new HeaderBasedMessagingExtractor();
		SpanTextMap spanTextMap = spanTextMap();
		spanTextMap.put(TraceMessageHeaders.SPAN_ID_NAME, Span.idToHex(10L));
		spanTextMap.put(TraceMessageHeaders.TRACE_ID_NAME, Span.idToHex(20L));
		spanTextMap.put(TraceMessageHeaders.SAMPLED_NAME, "1");
		spanTextMap.put(TraceMessageHeaders.SPAN_FLAGS_NAME, "0");

		Span span = extractor.joinTrace(spanTextMap);

		then(span).isExportable().isShared();
	}

	@Test
	public void samplesASpanWhenSampledFlagIsSetTo1() {
		HeaderBasedMessagingExtractor extractor = new HeaderBasedMessagingExtractor();
		SpanTextMap spanTextMap = spanTextMap();
		spanTextMap.put(TraceMessageHeaders.SPAN_ID_NAME, Span.idToHex(10L));
		spanTextMap.put(TraceMessageHeaders.TRACE_ID_NAME, Span.idToHex(20L));
		spanTextMap.put(TraceMessageHeaders.SAMPLED_NAME, "1");

		Span span = extractor.joinTrace(spanTextMap);

		then(span).isExportable().isShared();
	}

	@Test
	public void doesNotSampleASpanWhenSampledFlagIsSetTo0() {
		HeaderBasedMessagingExtractor extractor = new HeaderBasedMessagingExtractor();
		SpanTextMap spanTextMap = spanTextMap();
		spanTextMap.put(TraceMessageHeaders.SPAN_ID_NAME, Span.idToHex(10L));
		spanTextMap.put(TraceMessageHeaders.TRACE_ID_NAME, Span.idToHex(20L));
		spanTextMap.put(TraceMessageHeaders.SAMPLED_NAME, "0");

		Span span = extractor.joinTrace(spanTextMap);

		then(span).isNotExportable().isNotShared();
	}

	@Test
	public void samplesWhenDebugFlagIsSetTo1RegardlessOfTraceAndSpanId() {
		HeaderBasedMessagingExtractor extractor = new HeaderBasedMessagingExtractor();
		SpanTextMap spanTextMap = spanTextMap();
		spanTextMap.put(TraceMessageHeaders.SPAN_FLAGS_NAME, "1");

		Span span = extractor.joinTrace(spanTextMap);

		then(span).isExportable().isNotShared();
		then(span.traceIdString()).isNotEmpty();
		then(span.getSpanId()).isNotNull();
	}

	@Test
	public void samplesWhenDebugFlagIsSetTo1AndOnlySpanIdIsSet() {
		HeaderBasedMessagingExtractor extractor = new HeaderBasedMessagingExtractor();
		SpanTextMap spanTextMap = spanTextMap();
		spanTextMap.put(TraceMessageHeaders.SPAN_FLAGS_NAME, "1");
		spanTextMap.put(TraceMessageHeaders.SPAN_ID_NAME, Span.idToHex(10L));

		Span span = extractor.joinTrace(spanTextMap);

		then(span).isExportable().isNotShared();
		then(span.traceIdString()).isNotEmpty();
		then(span.getSpanId()).isEqualTo(10L);
	}

	@Test
	public void samplesWhenDebugFlagIsSetTo1AndOnlyTraceIdIsSet() {
		HeaderBasedMessagingExtractor extractor = new HeaderBasedMessagingExtractor();
		SpanTextMap spanTextMap = spanTextMap();
		spanTextMap.put(TraceMessageHeaders.SPAN_FLAGS_NAME, "1");
		spanTextMap.put(TraceMessageHeaders.TRACE_ID_NAME, Span.idToHex(10L));

		Span span = extractor.joinTrace(spanTextMap);

		then(span).isExportable().isNotShared();
		then(span.getTraceId()).isEqualTo(10L);
		then(span.getSpanId()).isEqualTo(10L);
	}

	private SpanTextMap spanTextMap() {
		return new SpanTextMap() {
			private final Map<String, String> map = new HashMap<>();

			@Override public Iterator<Map.Entry<String, String>> iterator() {
				return this.map.entrySet().iterator();
			}

			@Override public void put(String key, String value) {
				this.map.put(key, value);
			}
		};
	}
}