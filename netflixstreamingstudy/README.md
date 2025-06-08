# Netflix Streaming Study: HTTP Range Protocol Video Streaming

This repository implements a foundational HTTP **Range Protocol** based video streaming system using **Spring Boot**. It serves as a stepping stone to understanding more advanced **HTTP Live Streaming (HLS)** techniques and protocols like **MPEG-DASH**, **HLS with m3u8**, or **adaptive bitrate streaming**.

---

## Objectives

- Understand the principles of **HTTP-based partial content delivery** using the `Range` header.
- Learn how `StreamingResponseBody` in Spring helps in efficient streaming of large files without loading them fully into memory.
- Lay the groundwork for implementing **HTTP Live Streaming** techniques.

---

## How the Code Works

### 1. Video Stream Controller

Located at:

```
io.github.venkat1701.netflixstreamingstudy.adapters.inbound.impl.RangeProtocolStreamController
```

This REST controller exposes two endpoints:

| Endpoint                                | Method | Description                                                                 |
|----------------------------------------|--------|-----------------------------------------------------------------------------|
| `/stream/videos`                       | GET    | Streams partial content from the file `version1.mp4` using the HTTP Range header |
| `/stream/videos/download/{fileName}`   | GET    | Downloads the entire video file as a byte array                            |

**Note**: Only `version1.mp4` is hardcoded for streaming. The download endpoint is dynamic.

---

### 2. Streaming Implementation

Located in:

```
io.github.venkat1701.netflixstreamingstudy.services.impl.RangeProtocolVideoStreamService
```

**Key responsibilities:**

- Parses the `Range` header from the HTTP request (e.g., `Range: bytes=0-1023`)
- Calculates the start and end byte offsets
- Uses `RandomAccessFile` to seek and read only the specified byte range
- Streams the bytes using `StreamingResponseBody` for low memory footprint and continuous delivery

#### Why StreamingResponseBody?

It enables **non-blocking**, **incremental** writing of byte chunks to the response stream, crucial for handling large media files efficiently.

---

## HTTP Range Protocol Explained

### Concept

HTTP allows clients to request specific byte ranges of a file using the `Range` header. This enables:

- Efficient streaming without downloading the full file
- Resuming downloads from where they left off
- Implementing media players with seek functionality

### Example

```
GET /stream/videos HTTP/1.1
Range: bytes=0-1023
```

#### Response:

```
HTTP/1.1 206 Partial Content
Content-Type: video/mp4
Content-Range: bytes 0-1023/3456710
Content-Length: 1024
Accept-Ranges: bytes
```

The server responds with the first 1024 bytes of the video.

---

## Internals: Byte Range Streaming Process

1. Request received with `Range` header.
2. Controller delegates to `VideoStreamService`.
3. File accessed via classpath (`videos/version1.mp4`).
4. `RandomAccessFile.seek(start)` is used to jump to the byte offset.
5. Chunks are read and written to response stream in a loop using a buffer (e.g., 1024 bytes).
6. Response headers are set appropriately (`206 Partial Content`, `Content-Range`, etc.).
7. Browser/player receives chunks and starts rendering progressively.

---

## What About HTTP Live Streaming (HLS)?

HTTP Live Streaming (HLS) is built on top of HTTP Range Protocols, but with **playlist-driven chunking** and **adaptive bitrate selection**.

### Key Concepts in HLS

| Component         | Description                                       |
|------------------|---------------------------------------------------|
| `.m3u8` playlist  | Contains URLs to video segments and metadata      |
| `.ts` or `.mp4` segments | Small video chunks (~10s) encoded at various bitrates |
| Bitrate adaptation | Based on network conditions, the client switches stream quality |

### Workflow

1. Client fetches `.m3u8` file.
2. It selects the best bitrate variant based on current network speed.
3. Downloads and plays `.ts` chunks in sequence.
4. Can dynamically switch bitrates if network conditions change.

In contrast, the **Range Protocol** implementation here is more manual and linear, and doesn't support:

- Chunked encoding
- Adaptive bitrate switching
- Playlist files

But it is a crucial low-level precursor for understanding the higher abstraction of HLS.

---

## Folder Structure

```
netflixstreamingstudy/
├── adapters/
│   └── inbound/
│       ├── core/                     # Interface: VideoStreamController
│       └── impl/                     # Implementation: RangeProtocolStreamController
├── services/
│   ├── core/                         # Interface: VideoStreamService
│   └── impl/                         # Implementation: RangeProtocolVideoStreamService
├── videos/                           # Place your MP4 files here (classpath resource)
```

---

## Running the Project

1. Place `version1.mp4` under `src/main/resources/videos/`.
2. Run the Spring Boot application.
3. Use Postman or `curl` to test:

```
curl -H "Range: bytes=0-1023" http://localhost:8080/stream/videos
```

4. For full download:

```
curl http://localhost:8080/stream/videos/download/version1.mp4 -o downloaded.mp4
```

---
