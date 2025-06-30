# AONE WebRTC SFU Demo

`aone-webrtc-sfu` is a demo project designed to validate and showcase the core architecture of a WebRTC-based SFU (Selective Forwarding Unit) server.

---

## 🎯 Project Goals

- Share technical insights into a custom-designed SFU server built on Google WebRTC Native C++
- Demonstrate key modules including RTP forwarding, simulcast handling, and TWCC-based congestion control
- Provide a working demo environment with an Android WebRTC client app
- Explore potential real-world applications such as real-time broadcasting and IoT-based AR synchronization

---

## 📦 Project Modules

- `media_server/`  
  - A minimal test server with built-in signaling and RTP relay features  
  - **Signaling is embedded** for development and testing convenience. In production, separating signaling into a standalone server is highly recommended

- `rtc_peerconnection/`  
  - A lightweight, customized PeerConnection module based on Google WebRTC Native code  
  - Optimized for core functionality: simulcast, TWCC, RID, RTP/RTCP handling  
  - **Source code is not publicly available**; structural overview only

- `android_client/`  
  - Source code for an Android WebRTC client app is included for demo testing

---

## 🧩 Technical Highlights

- **Lightweight PeerConnection Module**  
  Custom-modified from Google WebRTC Native for server-side performance and modular integration

- **Simulcast with RID-Based Layer Routing**  
  Handles multiple resolution layers from the sender using RID (RTP Stream ID)

- **Efficient RTP Forwarding & RTCP Feedback Processing**

- **TWCC-Based Congestion Control**  
  Implements server-side congestion control using Google’s TWCC algorithm for adaptive bandwidth handling

- **Built-in Signaling Architecture**  
  WebSocket-based signaling is included in the `media_server` for faster iteration during development.  
  This should be decoupled in real-world deployment.

---

## 📌 Notes

- This repository is not a complete commercial-grade SFU server, but a **demo project for technical validation and concept sharing**
- For production use, additional components such as a standalone signaling server, TURN server integration, and security features are required

---

## 👨‍💻 Developer

Jungje Jang | Media System Architect  
✉️ jacques97jj@gmail.com  
🔗 [LinkedIn](https://www.linkedin.com/in/%EC%A4%91%EC%A0%9C-%EC%9E%A5-71a6b010b/)


---

## 📎 Keywords

`WebRTC`, `SFU`, `Simulcast`, `RTP`, `RTCP`, `TWCC`, `BWE`, `Congestion Control`, `Android WebRTC`, `PeerConnection`, `Native C++`, `Real-Time Streaming`
