package io.github.venkat1701.netflixstreamingstudy.services.impl;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import io.github.venkat1701.netflixstreamingstudy.core.services.VideoStreamService;

@Service
public class RangeProtocolVideoStreamService implements VideoStreamService {

    @Override
    public ResponseEntity<byte[]> downloadVideo(String fileName) throws IOException {
        return null;
    }

    @Override
    public ResponseEntity<byte[]> streamVideo(String fileName) throws IOException {
        return null;
    }
}
