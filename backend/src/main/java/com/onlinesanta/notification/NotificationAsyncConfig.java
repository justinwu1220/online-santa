package com.onlinesanta.notification;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 通知信專用的執行緒池。
 *
 * <p>不用 {@code @Async} 預設的 {@code SimpleAsyncTaskExecutor}——那個實作每次呼叫都開
 * 一條新執行緒，沒有上限也不重用，流量一大會把執行緒資源耗盡。這裡的量很小
 * （單一使用者的單一動作最多觸發一封信），核心 2、上限 5、佇列 200 綽綽有餘，
 * 多的請求排隊等，不會無限增生執行緒。
 */
@Configuration
public class NotificationAsyncConfig {

    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notify-");
        executor.initialize();
        return executor;
    }
}
