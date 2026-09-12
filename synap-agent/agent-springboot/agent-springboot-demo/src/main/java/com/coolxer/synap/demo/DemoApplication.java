package com.coolxer.synap.demo;

import com.coolxer.synap.api.ActionEvent;
import com.coolxer.synap.api.SynapAgent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) { SpringApplication.run(DemoApplication.class, args); }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception { return http.authorizeHttpRequests(value -> value.requestMatchers("/public/**").permitAll().anyRequest().authenticated()).httpBasic(Customizer.withDefaults()).build(); }

    @RestController @RequestMapping("/api")
    static class DemoController {
        @GetMapping("/users/{id}") Map<String, Object> user(@PathVariable String id) { return Map.of("id", id); }
        @PostMapping("/action") Map<String, Object> action(@RequestParam String name) {
            String id = UUID.randomUUID().toString(); SynapAgent.markAction(new ActionEvent(id, name, ActionEvent.Phase.SUCCESS, Map.of("source", "demo"))); return Map.of("actionId", id);
        }
        @PostMapping("/unsafe-command") Map<String, Object> command(@RequestParam String command) throws IOException {
            new ProcessBuilder("sh", "-c", command).start(); return Map.of("started", true);
        }
    }
}
