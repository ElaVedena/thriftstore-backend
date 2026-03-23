package com.vedathrifts.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class SimpleTestController {
    
    @GetMapping("/simple-test")
    public ResponseEntity<String> simpleGet() {
        System.out.println("🔥 Simple GET endpoint hit!");
        return ResponseEntity.ok("Simple GET works!");
    }
    
    @PostMapping("/simple-test")
    public ResponseEntity<Map<String, String>> simplePost(@RequestBody Map<String, String> body) {
        System.out.println("🔥 Simple POST endpoint hit with body: " + body);
        return ResponseEntity.ok(Map.of(
            "message", "Simple POST works!",
            "received", body.toString()
        ));
    }
}