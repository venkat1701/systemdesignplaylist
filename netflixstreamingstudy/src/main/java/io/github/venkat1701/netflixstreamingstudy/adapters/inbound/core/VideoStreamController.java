package io.github.venkat1701.netflixstreamingstudy.adapters.inbound.core;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RequestMapping("/stream")
public interface VideoStreamController {

    @GetMapping("/videos/")
    ResponseEntity<StreamingResponseBody> streamVideo(
        @RequestHeader(value="Range", required = false) String rangeHeader
    ) throws IOException;

    @GetMapping("/videos/download/{fileName}")
    ResponseEntity<byte[]> downloadVideo(
        @PathVariable String fileName
    ) throws IOException;
}
