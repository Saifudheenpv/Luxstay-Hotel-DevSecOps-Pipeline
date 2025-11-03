package com.hotel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ApplicationStartupTest {

    @Autowired
    private ApplicationContext context;

    @Test
    public void testContextLoads() {
        assertNotNull(context);
    }

    @Test
    public void testBasicApplicationStartup() {
        // This test just verifies the application can start
        assertTrue(true);
    }
}
