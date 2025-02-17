package io.github.venkat1701.loadbalancer.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@RestController
public class LoadBalancedEndpoint {

    private final RestTemplate restTemplate;

    @Autowired
    public LoadBalancedEndpoint(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/invoke")
    public String invoke(@RequestParam("v1") int v1, @RequestParam("v2") int v2) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ciphercircuit-math-assistant.p.rapidapi.com/addition?numbers=-11.484216%2C%202%2C%2032%2C%20-45&decimalPlaces=5"))
                .header("x-rapidapi-key", "661fa8cfa5msh760bf94e75b6c13p11c42ajsnef40e40940c3")
                .header("x-rapidapi-host", "ciphercircuit-math-assistant.p.rapidapi.com")
                .header("Cache-Control", "no-cache")
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        String responseString = restTemplate.getForObject(response.toString(), String.class);
        return "Response from service: " + response;
    }
}
