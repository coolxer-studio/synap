package com.coolxer.synap.starter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties(SynapAgentProperties.class)
@ConditionalOnProperty(prefix = "synap.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SynapAgentAutoConfiguration {
    @Bean @ConditionalOnMissingBean SynapAgentBootstrap synapAgentBootstrap(SynapAgentProperties properties, Environment environment) { return new SynapAgentBootstrap(properties, environment); }
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean ApiObservationAggregator synapApiObservationAggregator(SynapAgentProperties properties) { return new ApiObservationAggregator(properties.getApi().getObservationWindow(), properties.getApi().getMaxAssets()); }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    static class MvcConfiguration {
        @Bean MvcApiInstrumentation synapMvcApiInstrumentation(ObjectProvider<org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping> mappings,
                                                               ApiObservationAggregator observations, SynapAgentProperties properties) {
            return new MvcApiInstrumentation(mappings, observations, properties.getAppId());
        }
    }
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.reactive.DispatcherHandler")
    @ConditionalOnProperty(name = "spring.main.web-application-type", havingValue = "reactive")
    static class WebFluxConfiguration {
        @Bean WebFluxApiInstrumentation synapWebFluxApiInstrumentation(ObjectProvider<org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping> mappings,
                                                                       ApiObservationAggregator observations, SynapAgentProperties properties) {
            return new WebFluxApiInstrumentation(mappings, observations, properties.getAppId());
        }
    }
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.web.SecurityFilterChain")
    static class SecurityConfiguration {
        @Bean SpringSecurityInstrumentation synapSpringSecurityInstrumentation(
                ObjectProvider<org.springframework.security.web.SecurityFilterChain> chains) { return new SpringSecurityInstrumentation(chains); }
    }
}
