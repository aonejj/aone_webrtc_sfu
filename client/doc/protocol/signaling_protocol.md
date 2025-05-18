# Signaling Protocol Specification

This document describes the signaling protocol used between client and server for session registration, initialization, 
and media negotiation in the AONE WebRTC SFU Demo system.

---

## Protocol Format

### 1. `regist`

#### Client -> Server

```json
{
  "category": "regist",
  "cmd": "req",
  "cookie": 123456,
  "sdp_semantics": "PlanB" // or "UnifiedPlan"
}
```

#### Server -> Client

```json
{
  "category": "regist",
  "cmd": "res",
  "cookie": 123456,
  "session_id": 10001,
  "ice_servers": {}
}
```

### 2. `init`

#### Server -> Client

```json
{
  "category": "init",
  "cmd": "req",
  "cookie": 123457,
  "session_id": 10001
}
```

#### Client -> Server (Offer)

```json
{
  "category": "init",
  "cmd": "res_offer",
  "cookie": 123457,
  "session_id": 10001,
  "sdp": "<SDP string>"
}
```

#### Server -> Client (Answer)

```json
{
  "category": "init",
  "cmd": "res_answer",
  "cookie": 123457,
  "session_id": 10001,
  "sdp": "<SDP string>"
}
```

### 3. `audio_init`

#### Server -> Client

```json
{
  "category": "audio_init",
  "cmd": "req",
  "cookie": 123458,
  "session_id": 10001
}
```

#### Client -> Server (Offer)

```json
{
  "category": "audio_init",
  "cmd": "res_offer",
  "cookie": 123458,
  "session_id": 10001,
  "sdp": "<SDP string>"
}
```

#### Server -> Client (Answer)

```json
{
  "category": "audio_init",
  "cmd": "res_answer",
  "cookie": 123458,
  "session_id": 10001,
  "sdp": "<SDP string>"
}
```

### 4. `video_add`

#### Server -> Client

```json
{
  "category": "video_add",
  "cmd": "req",
  "cookie": 123459,
  "session_id": 10001,
  "add_count": 1
}
```

#### Client -> Server (Offer)

```json
{
  "category": "video_add",
  "cmd": "res_offer",
  "cookie": 123459,
  "session_id": 10001,
  "add_count": 1,
  "sdp": "<SDP string>"
}
```

#### Server -> Client (Answer)

```json
{
  "category": "video_add",
  "cmd": "res_answer",
  "cookie": 123459,
  "session_id": 10001,
  "add_count": 1,
  "sdp": "<SDP string>"
}
```

#### Client -> Server (ACK)

```json
{
  "category": "video_add",
  "cmd": "res_answer_ack",
  "cookie": 123459,
  "session_id": 10001
}
```

### 5. `video_remove`

#### Server -> Client

```json
{
  "category": "video_remove",
  "cmd": "req",
  "cookie": 123460,
  "session_id": 10001
}
```

#### Client -> Server (Offer)

```json
{
  "category": "video_remove",
  "cmd": "res_offer",
  "cookie": 123460,
  "session_id": 10001,
  "sdp": "<SDP string>"
}
```

#### Server -> Client (Answer)

```json
{
  "category": "video_remove",
  "cmd": "res_answer",
  "cookie": 123460,
  "session_id": 10001,
  "sdp": "<SDP string>"
}
```

### 6. `candidate`

```json
{
  "category": "candidate",
  "session_id": 10001,
  "mline_index": 0,
  "mid": "video",
  "candidate": "candidate:..."
}
```

---

This protocol design ensures clear negotiation flow and flexibility for dynamic stream handling, aligned with the SFU architecture goals of the AONE project.
