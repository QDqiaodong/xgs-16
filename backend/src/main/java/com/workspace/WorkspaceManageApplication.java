package com.workspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class WorkspaceManageApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkspaceManageApplication.class, args);
    }
}
