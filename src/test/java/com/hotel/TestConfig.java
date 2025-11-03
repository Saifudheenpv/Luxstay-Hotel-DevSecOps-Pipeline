package com.hotel;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.hotel.util.SimplePasswordEncoder;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public SimplePasswordEncoder passwordEncoder() {
        return new SimplePasswordEncoder();
    }
}