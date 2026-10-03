package com.codesupreme.sifarisqrupu.push;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class PushSchedulingConfiguration {
    @Bean(name="taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {return scheduler("application-scheduled-");}
    @Bean(name="pushTaskScheduler")
    public ThreadPoolTaskScheduler pushTaskScheduler() {return scheduler("fcm-scheduled-");}
    private ThreadPoolTaskScheduler scheduler(String prefix) {
        var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix(prefix);
        return scheduler;
    }
}
