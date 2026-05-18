package com.mywebsite.infra.itest.probe;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProbePingController {

    @GetMapping("/infra-itest/ping")
    String ping() {
        return "ok";
    }
}
