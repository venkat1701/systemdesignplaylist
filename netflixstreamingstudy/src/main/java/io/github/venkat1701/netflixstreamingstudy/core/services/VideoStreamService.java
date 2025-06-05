package io.github.venkat1701.netflixstreamingstudy.core.services;

import java.io.IOException;

import org.springframework.http.ResponseEntity;

public interface VideoStreamService {

    ResponseEntity<byte[]> downloadVideo(String fileName) throws IOException;
    ResponseEntity<byte[]> streamVideo(String fileName) throws IOException;
}
