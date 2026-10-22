package com.onlinesanta.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC 層的攔截器註冊。 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final SuspendedOrganizationInterceptor suspendedOrganization;
    private final SuspendedUserInterceptor suspendedUser;

    public WebMvcConfig(SuspendedOrganizationInterceptor suspendedOrganization,
                        SuspendedUserInterceptor suspendedUser) {
        this.suspendedOrganization = suspendedOrganization;
        this.suspendedUser = suspendedUser;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(suspendedOrganization).addPathPatterns("/api/**");
        registry.addInterceptor(suspendedUser).addPathPatterns("/api/**");
    }
}
