package kr.kimchimap.ingestion.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.ingestion.scheduling-enabled", havingValue = "true")
public class IngestionSchedulingConfiguration {}
