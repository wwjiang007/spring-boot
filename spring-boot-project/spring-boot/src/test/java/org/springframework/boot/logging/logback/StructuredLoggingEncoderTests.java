/*
 * Copyright 2012-2024 the original author or authors.
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

package org.springframework.boot.logging.logback;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.logging.structured.ApplicationMetadata;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link StructuredLogEncoder}.
 *
 * @author Moritz Halbritter
 */
class StructuredLoggingEncoderTests extends AbstractStructuredLoggingTests {

	private StructuredLogEncoder encoder;

	@Override
	@BeforeEach
	void setUp() {
		super.setUp();
		this.encoder = new StructuredLogEncoder();
	}

	@Override
	@AfterEach
	void tearDown() {
		super.tearDown();
		this.encoder.stop();
	}

	@Test
	void shouldSupportEcsCommonFormat() {
		this.encoder.setFormat("ecs");
		this.encoder.start();
		LoggingEvent event = createEvent();
		event.setMDCPropertyMap(Collections.emptyMap());
		String json = encode(event);
		Map<String, Object> deserialized = deserialize(json);
		assertThat(deserialized).containsKey("ecs.version");
	}

	@Test
	void shouldSupportLogstashCommonFormat() {
		this.encoder.setFormat("logstash");
		this.encoder.start();
		LoggingEvent event = createEvent();
		event.setMDCPropertyMap(Collections.emptyMap());
		String json = encode(event);
		Map<String, Object> deserialized = deserialize(json);
		assertThat(deserialized).containsKey("@version");
	}

	@Test
	void shouldSupportCustomFormat() {
		this.encoder.setFormat(CustomLogbackStructuredLoggingFormatter.class.getName());
		this.encoder.start();
		LoggingEvent event = createEvent();
		event.setMDCPropertyMap(Collections.emptyMap());
		String format = encode(event);
		assertThat(format).isEqualTo("custom-format");
	}

	@Test
	void shouldInjectCustomFormatConstructorParameters() {
		this.encoder.setFormat(CustomLogbackStructuredLoggingFormatterWithInjection.class.getName());
		this.encoder.setPid(1L);
		this.encoder.start();
		LoggingEvent event = createEvent();
		event.setMDCPropertyMap(Collections.emptyMap());
		String format = encode(event);
		assertThat(format).isEqualTo("custom-format-with-injection pid=1 hasThrowableProxyConverter=true");
	}

	@Test
	void shouldCheckTypeArgument() {
		assertThatIllegalArgumentException().isThrownBy(() -> {
			this.encoder.setFormat(CustomLogbackStructuredLoggingFormatterWrongType.class.getName());
			this.encoder.start();
		}).withMessageContaining("must be ch.qos.logback.classic.spi.ILoggingEvent but was java.lang.String");
	}

	@Test
	void shouldCheckTypeArgumentWithRawType() {
		assertThatIllegalArgumentException().isThrownBy(() -> {
			this.encoder.setFormat(CustomLogbackStructuredLoggingFormatterRawType.class.getName());
			this.encoder.start();
		}).withMessageContaining("must be ch.qos.logback.classic.spi.ILoggingEvent but was null");
	}

	@Test
	void shouldFailIfNoCommonOrCustomFormatIsSet() {
		assertThatIllegalArgumentException().isThrownBy(() -> {
			this.encoder.setFormat("does-not-exist");
			this.encoder.start();
		})
			.withMessageContaining(
					"Unknown format 'does-not-exist'. Values can be a valid fully-qualified class name or one of the common formats: [ecs, logstash]");
	}

	private String encode(LoggingEvent event) {
		return new String(this.encoder.encode(event), StandardCharsets.UTF_8);
	}

	static final class CustomLogbackStructuredLoggingFormatter implements StructuredLogFormatter<ILoggingEvent> {

		@Override
		public String format(ILoggingEvent event) {
			return "custom-format";
		}

	}

	static final class CustomLogbackStructuredLoggingFormatterWithInjection
			implements StructuredLogFormatter<ILoggingEvent> {

		private final ApplicationMetadata metadata;

		private final ThrowableProxyConverter throwableProxyConverter;

		CustomLogbackStructuredLoggingFormatterWithInjection(ApplicationMetadata metadata,
				ThrowableProxyConverter throwableProxyConverter) {
			this.metadata = metadata;
			this.throwableProxyConverter = throwableProxyConverter;
		}

		@Override
		public String format(ILoggingEvent event) {
			boolean hasThrowableProxyConverter = this.throwableProxyConverter != null;
			return "custom-format-with-injection pid=" + this.metadata.pid() + " hasThrowableProxyConverter="
					+ hasThrowableProxyConverter;
		}

	}

	static final class CustomLogbackStructuredLoggingFormatterWrongType implements StructuredLogFormatter<String> {

		@Override
		public String format(String event) {
			return event;
		}

	}

	@SuppressWarnings("rawtypes")
	static final class CustomLogbackStructuredLoggingFormatterRawType implements StructuredLogFormatter {

		@Override
		public String format(Object event) {
			return "";
		}

	}

}
