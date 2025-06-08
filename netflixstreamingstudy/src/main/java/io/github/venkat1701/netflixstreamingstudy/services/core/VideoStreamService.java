package io.github.venkat1701.netflixstreamingstudy.services.core;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public interface VideoStreamService {

    ResponseEntity<byte[]> downloadVideo(String fileName) throws IOException;
    ResponseEntity<StreamingResponseBody> streamVideo(String fileName, String rangeHeader) throws IOException;
}
