package com.example.demo.application;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppController {

    @GetMapping("/health")
    public String healthCheck() {
        return "Application is running";
    }
}
