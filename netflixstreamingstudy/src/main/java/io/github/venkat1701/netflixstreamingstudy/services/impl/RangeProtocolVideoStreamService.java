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

/**
 * RangeProtocol is one of the initial techniques to learn before HTTP Live Streaming.
 * Using live streaming, it's just the work of an encoder like ffmpeg to divide the video sample into different bitrate portions.
 * @author Krish Jaiswal
 */
@Service
public class RangeProtocolVideoStreamService implements VideoStreamService {

    /**
     * Downloads a video from the given resource.
     * @param fileName The filename relative to the resource you want to download.
     * @return ResponseEntity of byte stream of the video
     * @throws IOException If the resource isn't available, you get this exception.
     */
    @Override
    public ResponseEntity<byte[]> downloadVideo(String fileName) throws IOException {
        Resource resource = new ClassPathResource("videos/" + fileName);
        File video = resource.getFile();
        return ResponseEntity
            .ok()
            .header("Content-Type", "video/mp4")
            .body(Files.readAllBytes(Paths.get(video.getAbsolutePath())));
    }

    /**
     * This is the main portion of the program. We use a Streaming Response Body for a purpose.
     * Let's say you decide to use the same old byte[] stream way. You sent one request and with a Range header to get the first 1024 bytes.
     * Now who is going to send another request for requesting the next 1024 bytes? To do this on Frontend, you can use Long polling, but that's
     * not optimal.
     * Hence, you stream 1024 bytes using the Streaming Response Body, which basically returns the array of 1024 bytes aka the portion of the video and keeeps
     * streaming that until EOF.
     * @param fileName The Filename
     * @param rangeHeader Range Header to be specified using Postman. Make sure this is not a parameter, its a header.
     * @return StreamingResponseBody of bytes of the video.
     * @throws IOException If the file isn't found, here's its exception
     */
    public ResponseEntity<StreamingResponseBody> streamVideo(String fileName, String rangeHeader) throws IOException {
        Resource resource = new ClassPathResource("videos/" + fileName);
        File video = resource.getFile();    // Get the file from the resource path.
        long fileSize = video.length();
        long start = 0, end = fileSize-1;  // denotes where to start and end of the file is going to be.
        // we need a range header to classify which portion of the file to access. Loading the entire file into the memory and then streaming it
        // is good initially but vague optimally.
        if(rangeHeader != null) {
            String[] ranges = rangeHeader.replace("bytes=", "").split("-"); // Range: bytes=0-1024 splits as [0,1024]
            start = Long.parseLong(ranges[0]);
            if(ranges.length > 1 && !ranges[1].isEmpty()) {
                end = Long.parseLong(ranges[1]);
            }
            long contentLength = end - start + 1;   // content length of the range
            StreamingResponseBody data = this.readByteRange(video, start, end); // get the range of bytes for that content length
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header("Content-Type", "video/mp4")
                .header("Accept-Ranges", "bytes")
                .header("Content-Length", String.valueOf(contentLength))
                .header("Content-Range", String.format("bytes %d-%d/%d", start, end, fileSize))
                .body(data); // stream the data using the responsebody
        } else return null;
    }

    /**
     * Reads a specific byte range from a media file
     * @param file
     * @param start
     * @param end
     * @return Returns a StreamingResponseBody of the given byte range from the media file.
     * @throws IOException
     */
    private StreamingResponseBody readByteRange(File file, long start, long end) throws IOException {
        return outputStream -> {
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
                randomAccessFile.seek(start);
                byte[] buffer = new byte[1024];
                long bytesToRead = end - start + 1;
                int bytesRead;
                // Reading it simply like a file.
                while (bytesToRead > 0 && (bytesRead = randomAccessFile.read(buffer, 0, (int)Math.min(buffer.length, bytesToRead))) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    bytesToRead -= bytesRead;
                }
            }
        };
    }
}
