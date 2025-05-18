
#include <fstream>
#include <filesystem> 
#include <vector>
#include <iostream>
#include <string>
#include <charconv>

#include "../include/RTCMediaServerController.h"


bool is_numeric(const std::string& str, int& out) {
	auto[ptr, ec] = std::from_chars(str.data(), str.data() + str.size(), out);
	return ec == std::errc();
}


int read_or_create_listen_port(const std::string& filepath, int& out_port) {
    namespace fs = std::filesystem;

    if (fs::exists(filepath)) {
        std::ifstream file(filepath);
        if (file.is_open()) {
            std::string line;
            std::getline(file, line);
            file.close();

            if (is_numeric(line, out_port)) {
                return 0;
            } else {
                fs::remove(filepath);
            }
        }
    }

    std::string input;
    while (true) {
        std::cout << "input signaling server listen port: ";
        std::getline(std::cin, input);

        if (!is_numeric(input, out_port)) {
            std::cout << "wrong listen port: " << input << std::endl;
        } else {
            break;
        }
    }

    std::ofstream out(filepath);
    if (out.is_open()) {
        out << out_port << std::endl;
        out.close();
    } else {
        std::cerr << "Failed to write port config file.\n";
    }

    return 0;
}


static void display_tester_command() {
	printf("start - s\n");
	printf("stop - e\n");
}

int main() {

    int port;
    read_or_create_listen_port("port.cfg", port);	
	std::cout << "signaling server " << port << " listen..." << std::endl;

	std::vector<RTCMediaServerController::RTCIceServerInfo> ice_servers;
	RTCMediaServerController::RTCIceServerInfo ice_server;
	ice_server._type = RTCMediaServerController::RTC_ICE_SERVER_TYPE_STUN;
	ice_server._uri = "stun:stun.l.google.com:19302";	// google stun
	ice_servers.push_back(ice_server);

	std::unique_ptr<RTCMediaServerController> server = RTCMediaServerController::Create(ice_servers);
	if (server == nullptr) {
		printf("create server fail!!!\n");
		return 0;
	}

	display_tester_command();

	char cmd;
	bool bend = false;
	bool is_start = false;

	while (1) {
		cmd = getchar();
		switch (cmd) {
		case 's':
			{
				if (is_start == false) {
					std::cout << "Start Server" << std::endl;
					server->Start((uint16_t)port);
					is_start = true;
				}
				break;
			}
		case 'e':
			{
				if (is_start) {
					std::cout << "Stop Server" << std::endl;
					server->Stop();
					is_start = false;
				}
				bend = true;
				break;
			}
		}

		if (bend) {
			break;
		}
	}

	return 0;
}