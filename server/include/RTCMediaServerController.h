//////////////////////////////////////////////////////////////////////////
//
// author : kimi
//
//////////////////////////////////////////////////////////////////////////

#ifndef __RTC_MEDIA_SERVER_CONTROLLER_H__
#define __RTC_MEDIA_SERVER_CONTROLLER_H__

#include <memory>
#include <vector>
#include <string>
#include <unistd.h>

class RTCMediaServerController {
public:
	typedef enum {
		RTC_ICE_SERVER_TYPE_STUN,
		RTC_ICE_SERVER_TYPE_TURN,
	} RTC_ICE_SERVER_TYPE;

	struct RTCIceServerInfo {
		RTC_ICE_SERVER_TYPE _type;
		std::string		_uri;
		std::string		_user_name;
		std::string		_user_password;
	};

public:
	static std::unique_ptr<RTCMediaServerController> Create(std::vector<RTCIceServerInfo> servers);
	~RTCMediaServerController();

public:
	uint32_t Start(uint16_t port);
	uint32_t Stop();

private:
	RTCMediaServerController(std::vector<RTCIceServerInfo> &servers);

private:
	class Impl;
	std::unique_ptr<Impl> impl_;

private:
	RTCMediaServerController(const RTCMediaServerController &) = delete;
	RTCMediaServerController &operator=(const RTCMediaServerController &) = delete;

};

#endif // __RTC_MEDIA_SERVER_CONTROLLER_H__