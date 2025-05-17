
#include <vector>
#include <iostream>
#include <string>
#include <charconv>

#include "../RTCMediaServer/rtc_server_controller/RTCMediaServerController.h"


bool is_numeric(const std::string& str, int& out) {
	auto[ptr, ec] = std::from_chars(str.data(), str.data() + str.size(), out);
	return ec == std::errc();
}

static void display_tester_command() {
	printf("start - s\n");
	printf("stop - e\n");
}

int main() {
#if 1
	{
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

#if 1
		std::string input;
		int port;

		while (1) {
			std::cout << "input signaling server listen port: ";
			std::getline(std::cin, input);

			if (!is_numeric(input, port)) {

				std::cout << "wrong listen port "<< input << std::endl;
			}
			else {
				std::cout << "listen port " << input << std::endl;
				break;
			}
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
#else 
		server->Start(8080);
		while (1) {
			usleep(1000);
		}

#endif 
	}
#endif

	return 0;
}