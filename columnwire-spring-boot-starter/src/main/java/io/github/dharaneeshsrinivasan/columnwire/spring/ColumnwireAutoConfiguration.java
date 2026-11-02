package io.github.dharaneeshsrinivasan.columnwire.spring;

import io.github.dharaneeshsrinivasan.columnwire.ArrowRecordEncoder;
import io.github.dharaneeshsrinivasan.columnwire.ArrowRuntime;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({WebMvcConfigurer.class, ArrowRecordEncoder.class})
@ConditionalOnBooleanProperty(name = "columnwire.enabled", matchIfMissing = true)
@EnableConfigurationProperties(ColumnwireProperties.class)
public class ColumnwireAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    ArrowRecordEncoder columnwireArrowRecordEncoder(ColumnwireProperties properties) {
        ArrowRuntime.verify();
        return new ArrowRecordEncoder(properties.batchSize());
    }

    @Bean
    WebMvcConfigurer columnwireWebMvcConfigurer(ArrowRecordEncoder encoder) {
        ArrowHttpMessageConverter converter = new ArrowHttpMessageConverter(encoder);
        return new WebMvcConfigurer() {
            @Override
            public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
                builder.configureMessageConvertersList(converters -> converters.add(converter));
            }
        };
    }

    @Bean
    VaryOnAcceptAdvice columnwireVaryOnAcceptAdvice() {
        return new VaryOnAcceptAdvice();
    }
}
