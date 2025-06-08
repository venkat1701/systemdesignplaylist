package io.github.venkat1701.netflixstreamingstudy.adapters.inbound.impl;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.github.venkat1701.netflixstreamingstudy.adapters.inbound.core.VideoStreamController;
import io.github.venkat1701.netflixstreamingstudy.services.core.VideoStreamService;

@RestController
public class RangeProtocolStreamController implements VideoStreamController {

    private final VideoStreamService videoStreamService;

    public RangeProtocolStreamController(VideoStreamService videoStreamService) {
        this.videoStreamService = videoStreamService;
    }

    @Override
    @GetMapping("/stream/videos")
    public ResponseEntity<StreamingResponseBody> streamVideo(
        @RequestHeader(value="Range", required = true) String rangeHeader) throws IOException {
        // currently hardcoded for the resource name
        return this.videoStreamService.streamVideo("version1.mp4", rangeHeader);
    }

    @Override
    public ResponseEntity<byte[]> downloadVideo(String fileName) throws IOException {
        return this.videoStreamService.downloadVideo(fileName);
    }
}
