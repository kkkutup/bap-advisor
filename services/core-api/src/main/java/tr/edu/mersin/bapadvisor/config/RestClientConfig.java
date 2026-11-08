package tr.edu.mersin.bapadvisor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate(ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setMessageConverters(List.of(
                new MappingJackson2HttpMessageConverter(objectMapper)
        ));
        return restTemplate;
    }
}

