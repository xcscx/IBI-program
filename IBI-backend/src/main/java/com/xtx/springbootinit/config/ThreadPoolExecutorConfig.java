package com.xtx.springbootinit.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 自定义线程池
 *
 * @author IT蛋
 */
@Configuration
public class ThreadPoolExecutorConfig {

    ThreadFactory threadFactory = new ThreadFactory() {
        private int count = 1;

        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("线程" + count);
            count++;
            return thread;
        }
    };

    @Bean
    public ThreadPoolExecutor threadPoolExecutor() {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(2, 3, 100, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10), threadFactory);
        return threadPoolExecutor;
    }


}
