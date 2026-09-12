package com.coolxer.synap.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.web.ServletTestExecutionListener;

@SpringBootTest(properties = "synap.agent.enabled=true")
@TestExecutionListeners(listeners = {
        ServletTestExecutionListener.class,
        DependencyInjectionTestExecutionListener.class
}, inheritListeners = false)
class DemoApplicationContextTest {
    @Test
    void applicationContextStartsWithoutCircularDependencies() {
    }
}
