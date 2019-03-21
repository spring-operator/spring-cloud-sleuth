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

package org.springframework.cloud.sleuth.annotation;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.annotation.SleuthSpanCreatorAspectNegativeTests.TestConfiguration;
import org.springframework.cloud.sleuth.assertions.ListOfSpans;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestConfiguration.class)
public class SleuthSpanCreatorAspectNegativeTests {

	@Autowired NotAnnotatedTestBeanInterface testBean;
	@Autowired TestBeanInterface annotatedTestBean;
	@Autowired ArrayListSpanAccumulator accumulator;

	@Before
	public void setup() {
		ExceptionUtils.setFail(true);
		this.accumulator.clear();
	}

	@Test
	public void shouldNotCallAdviceForNotAnnotatedBean() {
		this.testBean.testMethod();

		then(this.accumulator.getSpans()).isEmpty();
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void shouldCallAdviceForAnnotatedBean() throws Throwable {
		this.annotatedTestBean.testMethod();

		List<Span> spans = new ArrayList<>(this.accumulator.getSpans());
		then(new ListOfSpans(spans)).hasSize(1).hasASpanWithName("test-method");
		then(ExceptionUtils.getLastException()).isNull();
	}
	
	protected interface NotAnnotatedTestBeanInterface {

		void testMethod();
	}

	protected static class NotAnnotatedTestBean implements NotAnnotatedTestBeanInterface {

		@Override
		public void testMethod() {
		}

	}
	
	protected interface TestBeanInterface {
		
		@NewSpan
		void testMethod();
		
		void testMethod2();
		
		void testMethod3();
		
		@NewSpan(name = "testMethod4")
		void testMethod4();
		
		@NewSpan(name = "testMethod5")
		void testMethod5(@SpanTag("testTag") String test);
		
		void testMethod6(String test);
		
		void testMethod7();
	}
	
	protected static class TestBean implements TestBeanInterface {

		@Override
		public void testMethod() {
		}

		@NewSpan
		@Override
		public void testMethod2() {
		}

		@NewSpan(name = "testMethod3")
		@Override
		public void testMethod3() {
		}

		@Override
		public void testMethod4() {
		}
		
		@Override
		public void testMethod5(String test) {
		}

		@NewSpan(name = "testMethod6")
		@Override
		public void testMethod6(@SpanTag("testTag6") String test) {
			
		}

		@Override
		public void testMethod7() {
		}
	}

	@Configuration
	@EnableAutoConfiguration
	protected static class TestConfiguration {
		@Bean SpanReporter spanReporter() {
			return new ArrayListSpanAccumulator();
		}

		@Bean
		public NotAnnotatedTestBeanInterface testBean() {
			return new NotAnnotatedTestBean();
		}

		@Bean
		public TestBeanInterface annotatedTestBean() {
			return new TestBean();
		}
	}
}
