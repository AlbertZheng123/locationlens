package net.bp4k.locationlens.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class SampleController {
    @GetMapping("/")
    public String home() {
        return "Unauthenticated";
    }

    @GetMapping("/secured")
    public String secure() {
        return "Authenticated";
    }
}
