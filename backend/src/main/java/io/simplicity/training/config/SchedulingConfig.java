package io.simplicity.training.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling, on its own and unconditionally.
 *
 * <p>This lived on {@link MediaConfig} until the audit retention job started depending on it. That
 * was harmless while the only scheduled work was the transcode poller, but the moment anybody made
 * the media configuration conditional — which its own comment shows was considered — every
 * scheduled task in the application would have stopped silently, including the one whose whole
 * purpose is to stop retaining personal information.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
