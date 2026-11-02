package io.github.dharaneeshsrinivasan.columnwire.spring;

import io.github.dharaneeshsrinivasan.columnwire.ArrowRecordEncoder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("columnwire")
public record ColumnwireProperties(@DefaultValue("true") boolean enabled,
                                   @DefaultValue("" + ArrowRecordEncoder.DEFAULT_BATCH_SIZE) int batchSize) {
}
