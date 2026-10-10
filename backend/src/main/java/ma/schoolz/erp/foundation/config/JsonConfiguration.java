package ma.schoolz.erp.foundation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class JsonConfiguration {

    @Bean
    ObjectMapper internalObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
