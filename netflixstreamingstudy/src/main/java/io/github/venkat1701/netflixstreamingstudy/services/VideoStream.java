package io.github.venkat1701.netflixstreamingstudy.services;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public interface VideoStream {
    RandomAccessFile getVideoFile(String filename) throws FileNotFoundException, IOException;
}
