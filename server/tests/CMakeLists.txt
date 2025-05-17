cmake_minimum_required(VERSION 3.13)
project(RTCMediaServerTest)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_executable(RTCMediaServerTest RTCMediaServerTest.cc)

target_include_directories(RTCMediaServerTest PRIVATE ../include)

target_link_directories(RTCMediaServerTest PRIVATE ../bin)

target_link_libraries(RTCMediaServerTest
    rtc_media_server
    rtc_sfu_peerconnection
    pthread
)

set_target_properties(RTCMediaServerTest PROPERTIES
    BUILD_WITH_INSTALL_RPATH TRUE
    INSTALL_RPATH "$ORIGIN/../../bin"
)
