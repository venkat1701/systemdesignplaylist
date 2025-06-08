package io.github.venkat1701.netflixstreamingstudy.services.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.github.venkat1701.netflixstreamingstudy.services.core.VideoStreamService;

@Service
public class RangeProtocolVideoStreamService implements VideoStreamService {

    @Override
    public ResponseEntity<byte[]> downloadVideo(String fileName) throws IOException {
        Resource resource = new ClassPathResource("videos/" + fileName);
        File video = resource.getFile();
        return ResponseEntity
            .ok()
            .header("Content-Type", "video/mp4")
            .body(Files.readAllBytes(Paths.get(video.getAbsolutePath())));
    }

    public ResponseEntity<StreamingResponseBody> streamVideo(String fileName, String rangeHeader) throws IOException {
        Resource resource = new ClassPathResource("videos/" + fileName);
        File video = resource.getFile();
        long fileSize = video.length();
        long start = 0, end = fileSize-1;
        System.out.println(rangeHeader);
        if(rangeHeader != null) {
            String[] ranges = rangeHeader.replace("bytes=", "").split("-");
            start = Long.parseLong(ranges[0]);
            if(ranges.length > 1 && !ranges[1].isEmpty()) {
                end = Long.parseLong(ranges[1]);
            }
            long contentLength = end - start + 1;
            StreamingResponseBody data = this.readByteRange(video, start, end);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header("Content-Type", "video/mp4")
                .header("Accept-Ranges", "bytes")
                .header("Content-Length", String.valueOf(contentLength))
                .header("Content-Range", String.format("bytes %d-%d/%d", start, end, fileSize))
                .body(data);
        } else return null;
    }

    private StreamingResponseBody readByteRange(File file, long start, long end) throws IOException {
        return outputStream -> {
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
                randomAccessFile.seek(start);
                byte[] buffer = new byte[1024];
                long bytesToRead = end - start + 1;
                int bytesRead;

                while (bytesToRead > 0 && (bytesRead = randomAccessFile.read(buffer, 0, (int)Math.min(buffer.length, bytesToRead))) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    bytesToRead -= bytesRead;
                }
            }
        };
    }
}
