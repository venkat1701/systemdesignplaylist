package io.github.venkat1701.websitevisitcounter.controllers;

import io.github.venkat1701.websitevisitcounter.dto.WebsiteVisitDTO;
import io.github.venkat1701.websitevisitcounter.model.WebsiteCounter;
import io.github.venkat1701.websitevisitcounter.services.WebsiteVisitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/visits")
public class WebsiteVisitController {

    private final WebsiteVisitService websiteVisitService;

    public WebsiteVisitController(WebsiteVisitService websiteVisitService) {
        this.websiteVisitService = websiteVisitService;
    }


    @PostMapping("/{pageNumber}")
    public ResponseEntity<String> incrementVisit(@PathVariable("pageNumber") String pageNumber) {
        this.websiteVisitService.incrementVisit(pageNumber);
        return ResponseEntity.ok("Successfully incremented visit");
    }

    @GetMapping("/{pageNumber}")
    public ResponseEntity<WebsiteVisitDTO> getVisits(@PathVariable("pageNumber") String pageNumber) {
        int visits = this.websiteVisitService.getVisitCount(pageNumber);
        System.out.println(visits);
        String source = this.websiteVisitService.getVisitCount(pageNumber) > 0 ? "Local Cache" : "Redis";
        return ResponseEntity.ok(new WebsiteVisitDTO(visits, source));
    }

}
